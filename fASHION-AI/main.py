import os
import io
import json
import secrets
import pickle
import base64
import shutil
import zipfile
import numpy as np
from datetime import datetime
from typing import List, Optional
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, Depends, Header
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from fastapi.openapi.docs import get_swagger_ui_html
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer, util
from PIL import Image

app = FastAPI(title="⚡ FIT//LAB AI Engine", docs_url=None, redoc_url=None)

# CORS: server-to-server only, no browser origins allowed by default. Set
# ALLOWED_ORIGINS to a comma-separated list to permit specific origins.
_allowed_origins = [o.strip() for o in os.environ.get("ALLOWED_ORIGINS", "").split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_allowed_origins,
    allow_credentials=False,
    allow_methods=["POST", "GET"],
    allow_headers=["X-API-Key", "Content-Type"],
)

# Shared-secret auth. Every endpoint requires a matching X-API-Key header
# so the service can't be driven by an arbitrary network caller.
API_KEY = os.environ.get("FASHION_AI_API_KEY")
def require_api_key(x_api_key: str = Header(default=None)):
    if not API_KEY:
        raise HTTPException(status_code=500, detail="FASHION_AI_API_KEY is not configured on the server")
    if not x_api_key or not secrets.compare_digest(x_api_key, API_KEY):
        raise HTTPException(status_code=401, detail="Missing or invalid X-API-Key")

model = SentenceTransformer('clip-ViT-B-32')

# Data Structures for incoming inventory. An item can be identified by a
# photo, a text description, or both - only id is actually required.
class ClothingItem(BaseModel):
    id: int
    description: Optional[str] = None
    # Optional photo of the actual item, base64-encoded. fit_model.pkl is
    # trained on real photos (see /add-fit below), so it can only be
    # applied to an inventory item when a real photo is available too -
    # text and image embeddings sit in different regions of CLIP's shared
    # space even for matching content, so scoring a text embedding with an
    # image-trained classifier would be out-of-distribution input.
    image_base64: Optional[str] = None

class WardrobeInventory(BaseModel):
    pants: List[ClothingItem] = []
    shoes: List[ClothingItem] = []

# ---------------------------------------------------------------------
# Item bank: a persistent, growing pool of individual item photos (any
# category - sneakers, shirts, whatever) that /generate-outfit-from-top
# candidates can be pulled from, no good/bad label needed. Two ways items
# land in it: bulk zip upload via /bank/upload-zip, or automatically from
# /add-fit whenever you rate an outfit using separate top/bottom/shoes/
# outerwear slots (a single_full_fit_image is a whole-body photo, not a
# single item, so that path is excluded).
# ---------------------------------------------------------------------
ITEM_BANK_FILE = "item_bank.json"
BANK_MAX_DIMENSION = 768
BANK_IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}

def load_item_bank():
    if os.path.exists(ITEM_BANK_FILE):
        with open(ITEM_BANK_FILE) as f:
            return json.load(f)
    return []

def save_item_bank(items):
    with open(ITEM_BANK_FILE, "w") as f:
        json.dump(items, f)

def describe_from_filename(filename: str) -> str:
    stem = os.path.splitext(filename)[0].replace("_", " ").replace("-", " ")
    return " ".join(stem.split())

def shrink_and_encode_bytes(raw: bytes) -> str:
    """CLIP resizes every image internally anyway (~224x224), so a full
    camera photo is wasted bloat - and this can end up embedded in
    inventory_json, a Form field Starlette hard-caps at 1MB. Shrinking
    before storing keeps the bank (and anything built from it) small."""
    img = Image.open(io.BytesIO(raw)).convert("RGB")
    img.thumbnail((BANK_MAX_DIMENSION, BANK_MAX_DIMENSION))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=85)
    return base64.b64encode(buf.getvalue()).decode("ascii")

def add_bank_item(category: str, description: str, raw_bytes: bytes, source: str) -> int:
    items = load_item_bank()
    next_id = max((i["id"] for i in items), default=0) + 1
    items.append({
        "id": next_id,
        "category": category,
        "description": description,
        "image_base64": shrink_and_encode_bytes(raw_bytes),
        "source": source,
        "added_at": datetime.now().isoformat(),
    })
    save_item_bank(items)
    return next_id


@app.post("/bank/upload-zip", tags=["Item Bank"])
async def upload_bank_zip(zip_file: UploadFile = File(...), _: None = Depends(require_api_key)):
    """
    Upload a zip with one subfolder per category (e.g. sneakers/, shirts/)
    to grow the persistent item bank - no good/bad label, this is just a
    pool of candidates /generate-outfit-from-top can pick from. Adds to
    the existing bank rather than replacing it.
    """
    if not zip_file.filename or not zip_file.filename.lower().endswith(".zip"):
        raise HTTPException(status_code=400, detail="Expected a .zip file")

    raw_zip = await zip_file.read()
    try:
        zf = zipfile.ZipFile(io.BytesIO(raw_zip))
    except zipfile.BadZipFile:
        raise HTTPException(status_code=400, detail="Not a valid zip file")

    added_by_category: dict[str, int] = {}
    for name in zf.namelist():
        if name.endswith("/") or "__MACOSX" in name:
            continue
        parts = name.split("/")
        if len(parts) < 2:
            continue  # not inside a category subfolder, skip
        category = parts[0]
        filename = parts[-1]
        if os.path.splitext(filename)[1].lower() not in BANK_IMAGE_EXTS:
            continue
        try:
            add_bank_item(
                category=category,
                description=describe_from_filename(filename),
                raw_bytes=zf.read(name),
                source="zip_upload",
            )
        except Exception:
            continue  # skip unreadable files rather than failing the whole batch
        added_by_category[category] = added_by_category.get(category, 0) + 1

    if not added_by_category:
        raise HTTPException(status_code=400, detail="No images found inside category subfolders (e.g. sneakers/, shirts/).")

    return {"added": added_by_category, "total_bank_size": len(load_item_bank())}


@app.get("/bank", tags=["Item Bank"])
def get_bank(category: Optional[str] = None, _: None = Depends(require_api_key)):
    """The current item bank, optionally filtered to one category."""
    items = load_item_bank()
    categories = sorted(set(i["category"] for i in items))
    if category:
        items = [i for i in items if i["category"].lower() == category.lower()]
    return {"count": len(items), "categories": categories, "items": items}


# ---------------------------------------------------------------------
# Live "general fit" brain: trained at runtime from outfits you rate via
# /add-fit, independent of the curated color_model.pkl / silhouette_model.pkl
# scripts. Uses real photo embeddings throughout, both at training time
# and (when available) at inference time, to keep the two consistent.
# ---------------------------------------------------------------------
FIT_MODEL_FILE = "fit_model.pkl"
FIT_DATA_FILE = "fit_training_data.pkl"
FIT_BACKUP_DIR = "fit_model_backups"
FIT_HISTORY_FILE = "fit_training_history.jsonl"
MAX_BACKUPS = 5
# Below this many total ratings, a train/test split doesn't leave enough
# of either to mean anything - just train on everything with no held-out
# score yet.
MIN_SAMPLES_FOR_HOLDOUT = 6

def load_fit_training_data():
    if os.path.exists(FIT_DATA_FILE):
        with open(FIT_DATA_FILE, "rb") as f:
            return pickle.load(f)
    return [], []

def get_image_embedding(file: Optional[UploadFile]):
    if not file or not file.filename:
        return np.zeros(512, dtype=np.float32)
    try:
        img = Image.open(io.BytesIO(file.file.read())).convert("RGB")
        return model.encode(img)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to process image {file.filename}: {str(e)}")

def get_image_embedding_and_bank(file: Optional[UploadFile], category: str):
    """Same as get_image_embedding, but also adds the photo to the item
    bank under the given category - used for /add-fit's separate item
    slots (top/bottom/shoes/outerwear), which are genuine single-item
    photos, unlike single_full_fit_image (a whole-body photo)."""
    if not file or not file.filename:
        return np.zeros(512, dtype=np.float32)
    raw = file.file.read()
    try:
        img = Image.open(io.BytesIO(raw)).convert("RGB")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to process image {file.filename}: {str(e)}")
    add_bank_item(category=category, description=describe_from_filename(file.filename), raw_bytes=raw, source="training_data")
    return model.encode(img)

def get_item_image_embedding(item: Optional[ClothingItem]):
    """Real photo embedding for an inventory item, or None if it only has a text description."""
    if not item or not item.image_base64:
        return None
    try:
        img_bytes = base64.b64decode(item.image_base64)
        img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
        return model.encode(img)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to decode image for item {item.id}: {str(e)}")

def get_item_embedding_for_matching(item: Optional[ClothingItem]):
    """Prefers a real photo, falls back to the text description, and
    returns None if the item has neither (nothing to score it against)."""
    img_emb = get_item_image_embedding(item)
    if img_emb is not None:
        return img_emb
    if item and item.description:
        return model.encode(item.description)
    return None

def _log_training_history(n_samples, holdout_accuracy):
    entry = {
        "timestamp": datetime.now().isoformat(),
        "n_samples": n_samples,
        "holdout_accuracy": holdout_accuracy,
    }
    with open(FIT_HISTORY_FILE, "a") as f:
        f.write(json.dumps(entry) + "\n")

def _backup_current_model():
    """Copies the existing fit_model.pkl aside before it gets overwritten,
    so a refit that makes things worse can always be rolled back by hand."""
    if not os.path.exists(FIT_MODEL_FILE):
        return
    os.makedirs(FIT_BACKUP_DIR, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
    shutil.copy2(FIT_MODEL_FILE, os.path.join(FIT_BACKUP_DIR, f"fit_model_{stamp}.pkl"))
    backups = sorted(os.listdir(FIT_BACKUP_DIR))
    for old in backups[:-MAX_BACKUPS]:
        os.remove(os.path.join(FIT_BACKUP_DIR, old))

def train_fit_brain(X_memory, y_memory):
    """Trains and persists fit_model.pkl from the full X_memory/y_memory
    passed in - always loaded fresh from disk by the caller right before
    this runs, never cached across requests, so a bulk-import script
    writing to the same files between /add-fit calls can't get clobbered
    by a stale in-memory copy."""
    # Persist the raw ratings unconditionally, even before there's enough
    # to actually fit a classifier - otherwise a single-class streak (e.g.
    # several "good" ratings before the first "bad" one) gets silently
    # dropped on every call and can never accumulate toward the 2-class
    # minimum below.
    with open(FIT_DATA_FILE, "wb") as f:
        pickle.dump((X_memory, y_memory), f)

    if len(set(y_memory)) < 2:
        return "Data cached. Need at least 1 Good Fit AND 1 Bad Fit example to train the brain matrix."

    X = np.array(X_memory)
    y = np.array(y_memory)

    # Fit a throwaway probe on a train/test split purely to measure how it
    # does on ratings it wasn't trained on, so accuracy is tracked over time
    # instead of always looking perfect because it's scored on its own
    # training data. The deployed model below is still trained on
    # everything - this is a diagnostic, not a gate.
    holdout_note = " (not enough ratings yet for a held-out accuracy check)"
    if len(y) >= MIN_SAMPLES_FOR_HOLDOUT:
        try:
            X_train, X_test, y_train, y_test = train_test_split(
                X, y, test_size=0.2, stratify=y, random_state=42
            )
            probe = LogisticRegression()
            probe.fit(X_train, y_train)
            acc = accuracy_score(y_test, probe.predict(X_test))
            _log_training_history(len(y), acc)
            holdout_note = f" Held-out accuracy: {acc:.0%} on {len(y_test)} ratings not used for training (see {FIT_HISTORY_FILE})."
        except ValueError:
            # e.g. one class still has too few examples to stratify-split.
            holdout_note = " (couldn't compute held-out accuracy yet - need more examples of both good and bad)"

    _backup_current_model()

    clf = LogisticRegression()
    clf.fit(X, y)
    with open(FIT_MODEL_FILE, "wb") as f:
        pickle.dump(clf, f)
    return f"Brain successfully updated and saved to fit_model.pkl!{holdout_note}"


@app.post("/add-fit", tags=["AI Style Training"])
async def add_fit(
    label: str,
    single_full_fit_image: Optional[UploadFile] = File(None),
    top: Optional[UploadFile] = File(None),
    bottom: Optional[UploadFile] = File(None),
    shoes: Optional[UploadFile] = File(None),
    outerwear: Optional[UploadFile] = File(None),
    _: None = Depends(require_api_key),
):
    """
    Rate a real outfit as 'good' or 'bad' to train fit_model.pkl at runtime.
    Upload any combination of a full-fit photo, top, bottom, shoes, and
    outerwear - just at least one image. Missing slots are zero-padded.
    This is independent of the curated color/silhouette scripts.
    """
    label_clean = label.strip().lower()
    if label_clean not in ["good", "bad"]:
        raise HTTPException(status_code=400, detail="Label parameter must be exactly 'good' or 'bad'")

    provided = [f for f in [single_full_fit_image, top, bottom, shoes, outerwear] if f and f.filename]
    if not provided:
        raise HTTPException(status_code=400, detail="Provide at least one image: a full-fit photo, or any of top/bottom/shoes/outerwear.")

    if single_full_fit_image and single_full_fit_image.filename:
        # A whole-body photo, not a single item - doesn't belong in the
        # item bank alongside individual sneaker/shirt/etc. photos.
        fit_emb = get_image_embedding(single_full_fit_image)
        outfit_vector = np.concatenate([fit_emb, fit_emb, fit_emb, fit_emb])
    else:
        top_emb = get_image_embedding_and_bank(top, "top")
        bottom_emb = get_image_embedding_and_bank(bottom, "pants")
        shoes_emb = get_image_embedding_and_bank(shoes, "shoes")
        outerwear_emb = get_image_embedding_and_bank(outerwear, "outerwear")
        outfit_vector = np.concatenate([top_emb, bottom_emb, shoes_emb, outerwear_emb])

    # Load fresh rather than trusting an in-memory copy - see
    # load_fit_training_data's docstring-equivalent note above train_fit_brain.
    X_memory, y_memory = load_fit_training_data()
    X_memory.append(outfit_vector)
    y_memory.append(1 if label_clean == "good" else 0)

    status = train_fit_brain(X_memory, y_memory)

    return {
        "status": f"Outfit recorded as {label_clean.upper()}",
        "engine_message": status,
        "total_dataset_samples": len(y_memory),
    }


@app.post("/generate-outfit-from-top", tags=["Core Matching Engine"])
async def generate_outfit_from_top(inventory_json: str = Form(...), file: UploadFile = File(...), _: None = Depends(require_api_key)):
    # inventory_json is a Form field, not a query parameter - ClothingItem
    # can carry a full base64-encoded photo now, and real photos are
    # multiple MB, which blows past URL length limits every server
    # enforces. Form fields ride in the multipart body instead, which has
    # no such limit.
    try:
        data = json.loads(inventory_json)
        inventory = WardrobeInventory(**data)

        top_image = Image.open(io.BytesIO(await file.read())).convert("RGB")
        top_embedding = model.encode(top_image)

        # Load your separate custom trained brains if they exist
        color_clf = pickle.load(open("color_model.pkl", "rb")) if os.path.exists("color_model.pkl") else None
        sil_clf = pickle.load(open("silhouette_model.pkl", "rb")) if os.path.exists("silhouette_model.pkl") else None
        fit_clf = pickle.load(open(FIT_MODEL_FILE, "rb")) if os.path.exists(FIT_MODEL_FILE) else None

        # 1. Base match for pants - photo if the item has one, else its
        # text description. Items with neither just score 0 and rank last.
        ranked_pants = []
        pants_emb_by_id = {}
        for p in inventory.pants:
            emb = get_item_embedding_for_matching(p)
            pants_emb_by_id[p.id] = emb
            score = float(util.cos_sim(top_embedding, emb)) if emb is not None else 0.0
            ranked_pants.append({"id": p.id, "description": p.description, "match_score": score})
        ranked_pants.sort(key=lambda x: x["match_score"], reverse=True)
        pants_by_id = {p.id: p for p in inventory.pants}
        best_pants_item = pants_by_id[ranked_pants[0]["id"]] if ranked_pants else None

        # 2. Advanced match for shoes using your custom training files
        ranked_shoes = []
        for s in inventory.shoes:
            shoe_emb = get_item_embedding_for_matching(s)
            pants_emb = pants_emb_by_id.get(best_pants_item.id) if best_pants_item else None
            if pants_emb is None:
                pants_emb = np.zeros(512)
            if shoe_emb is None:
                # Nothing to embed this shoe with - baseline-only score of 0.
                ranked_shoes.append({"id": s.id, "description": s.description, "match_score": 0.0})
                continue

            # color_model was trained with the pants slot always zeroed out
            # (train_color_clash.py only ever fills top + shoes), so it has
            # to be scored on that same zero-padded layout here - feeding it
            # a real pants embedding would be out-of-distribution input.
            # silhouette_model was trained with a real "bottom" embedding in
            # that slot, so it gets the actual pants_emb.
            color_vec = np.concatenate([top_embedding, np.zeros(512), shoe_emb, np.zeros(512)]).reshape(1, -1)
            sil_vec = np.concatenate([top_embedding, pants_emb, shoe_emb, np.zeros(512)]).reshape(1, -1)

            # Combine scores from both custom brains
            score = float(util.cos_sim(top_embedding, shoe_emb))  # baseline
            if color_clf:
                score += float(color_clf.predict_proba(color_vec)[0][1]) * 1.5
            if sil_clf:
                score += float(sil_clf.predict_proba(sil_vec)[0][1]) * 1.5

            # fit_model.pkl only ever saw real photo embeddings during
            # /add-fit, so only apply it when the pants/shoes items in this
            # inventory carry a real photo too - otherwise skip it rather
            # than score a text embedding with an image-trained model.
            if fit_clf:
                pants_img_emb = get_item_image_embedding(best_pants_item)
                shoe_img_emb = get_item_image_embedding(s)
                if pants_img_emb is not None and shoe_img_emb is not None:
                    fit_vec = np.concatenate([top_embedding, pants_img_emb, shoe_img_emb, np.zeros(512)]).reshape(1, -1)
                    score += float(fit_clf.predict_proba(fit_vec)[0][1]) * 1.5

            ranked_shoes.append({"id": s.id, "description": s.description, "match_score": score})

        ranked_shoes.sort(key=lambda x: x["match_score"], reverse=True)

        return {
            "recommended_outfit": {
                "top_status": "Base Item Provided",
                "best_pants_match": ranked_pants if ranked_pants else None,
                "best_shoes_match": ranked_shoes if ranked_shoes else None
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Engine error: {str(e)}")


def _read_json_if_exists(path):
    if not os.path.exists(path):
        return None
    with open(path) as f:
        return json.load(f)


@app.get("/stats", tags=["AI Style Training"])
def get_stats(_: None = Depends(require_api_key)):
    """How much data each of the three brains was actually trained on."""
    fit_X, fit_y = load_fit_training_data()
    fit_good = sum(fit_y)
    fit_history = []
    if os.path.exists(FIT_HISTORY_FILE):
        with open(FIT_HISTORY_FILE) as f:
            fit_history = [json.loads(line) for line in f if line.strip()]

    return {
        "fit_model": {
            "trained": os.path.exists(FIT_MODEL_FILE),
            "total_ratings": len(fit_y),
            "good": fit_good,
            "bad": len(fit_y) - fit_good,
            "latest_holdout_accuracy": fit_history[-1]["holdout_accuracy"] if fit_history else None,
            "holdout_checks_logged": len(fit_history),
        },
        "color_model": _read_json_if_exists("color_model_meta.json") or {"trained": os.path.exists("color_model.pkl"), "note": "meta file missing - rerun train_color_clash.py to record counts"},
        "silhouette_model": _read_json_if_exists("silhouette_model_meta.json") or {"trained": os.path.exists("silhouette_model.pkl"), "note": "meta file missing - rerun train_silhouette.py to record counts"},
        "item_bank": _bank_stats(),
    }


def _bank_stats():
    items = load_item_bank()
    by_category: dict[str, int] = {}
    by_source: dict[str, int] = {}
    for i in items:
        by_category[i["category"]] = by_category.get(i["category"], 0) + 1
        by_source[i["source"]] = by_source.get(i["source"], 0) + 1
    return {"total_items": len(items), "by_category": by_category, "by_source": by_source}


@app.get("/docs", include_in_schema=False)
async def custom_swagger_ui_html():
    # Keep your custom dark mode theme rendering intact
    html_response = get_swagger_ui_html(openapi_url=app.openapi_url, title="FIT//LAB Core Controls", swagger_favicon_url="https://tiangolo.com")
    html_body = html_response.body.decode("utf-8")
    modified_body = html_body.replace("</body>", "<style>body { background-color: #0d0d0d !important; color: #ffffff !important; font-family: monospace; }</style></body>")
    return HTMLResponse(content=modified_body, status_code=200)
