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
from sklearn.cluster import KMeans
from scipy import ndimage
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from fastapi.openapi.docs import get_swagger_ui_html
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
from PIL import Image, ImageOps
import torch
from transformers import SegformerImageProcessor, AutoModelForSemanticSegmentation

app = FastAPI(title="⚡ FIT//LAB AI Engine", docs_url=None, redoc_url=None)

# CORS: server-to-server only, no browser origins allowed by default. Set
# ALLOWED_ORIGINS to a comma-separated list to permit specific origins.
_allowed_origins = [o.strip() for o in os.environ.get("ALLOWED_ORIGINS", "").split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_allowed_origins,
    allow_credentials=False,
    allow_methods=["POST", "GET", "PATCH"],
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

# Clothes-parsing model: CLIP has no notion of "this pixel region is the
# shirt vs. the background vs. the shoes" - it just embeds a whole photo
# holistically. For single_full_fit_image uploads (a real head-to-toe
# outfit photo, not an individually-cropped item), this segmentation
# model finds the actual garment regions so each one can be cropped and
# CLIP-encoded on its own, instead of the whole scene (person, background,
# every garment at once) getting duplicated into all four inventory slots.
GARMENT_SEG_MODEL = "mattmdjaga/segformer_b2_clothes"
_seg_processor = SegformerImageProcessor.from_pretrained(GARMENT_SEG_MODEL)
_seg_model = AutoModelForSemanticSegmentation.from_pretrained(GARMENT_SEG_MODEL)
_seg_model.eval()
_seg_label2id = {name.lower(): idx for idx, name in _seg_model.config.id2label.items()}
# Only labels we have an inventory slot for - Hat/Hair/Face/Bag/etc are
# real segments this model finds but nothing in WardrobeInventory maps to
# them, so they're left alone (background as far as this app is concerned).
GARMENT_LABEL_GROUPS = {
    "top": ["upper-clothes", "dress"],
    "pants": ["pants", "skirt"],
    "shoes": ["left-shoe", "right-shoe"],
}

def _segment_pixel_labels(img: Image.Image) -> np.ndarray:
    """Per-pixel label id for every pixel in img, upsampled back to img's
    original resolution (the model itself runs at a fixed internal size)."""
    inputs = _seg_processor(images=img, return_tensors="pt")
    with torch.no_grad():
        logits = _seg_model(**inputs).logits
    upsampled = torch.nn.functional.interpolate(logits, size=img.size[::-1], mode="bilinear", align_corners=False)
    return upsampled.argmax(dim=1)[0].numpy()

def _skin_mask(img: Image.Image) -> np.ndarray:
    """Rough YCbCr skin-tone detector, used as a safety net for where the
    segmentation model itself mislabels skin as garment - e.g. hands
    folded across the stomach, or bare neck at an open collar, sometimes
    get predicted as "upper-clothes" outright, which is a labeling error
    no amount of eroding/cleaning up the mask's boundary can fix, since
    it's not a boundary problem. YCbCr separates color from brightness,
    which makes a fixed skin-tone range hold up across lighting much
    better than the same kind of check would in plain RGB. Trade-off: a
    genuinely tan/beige garment can lose some pixels to this too - given
    the ask was "not skin, not background, no noise," erring toward
    cutting too much rather than letting real skin through is the
    intended trade."""
    arr = np.array(img.convert("YCbCr"), dtype=np.float32)
    y, cb, cr = arr[..., 0], arr[..., 1], arr[..., 2]
    return (y > 40) & (cb >= 77) & (cb <= 127) & (cr >= 133) & (cr <= 173)

def _crop_to_labels(img: Image.Image, pixel_labels: np.ndarray, label_names: list, pad_frac: float = 0.02, erode_px: int = 4):
    """Isolates every pixel matching any of label_names and crops tight to
    it, or None if that garment wasn't found in this photo at all.

    Four things keep this from leaking anything but the garment itself:
    1. A skin-tone filter (_skin_mask) drops pixels the model itself
       mislabeled as garment - hands folded over the stomach or bare neck
       at an open collar sometimes get predicted as "upper-clothes"
       outright, which no amount of cleaning up the mask's boundary can
       fix, since it's a labeling error, not a boundary problem.
    2. Stray misclassified pixels (a normal segmentation-model artifact -
       e.g. a shadow far from the body briefly reads as "pants") get
       dropped by keeping only the single largest connected blob of the
       mask, instead of every matching pixel anywhere in the photo. A
       handful of stray pixels off in the background would otherwise blow
       the bounding box out to include everything between them and the
       real garment.
    3. The mask is eroded inward a couple pixels before anything else
       happens - the model's prediction is upsampled from a much lower
       internal resolution, so the outermost ring of "garment" pixels is
       the blurriest and most likely to actually be skin, jewelry (a
       chain has no label of its own, so it gets absorbed into whichever
       real class is nearest), or background bleeding through at the
       boundary.
    4. Whatever's left outside the (skin-filtered, eroded, largest-blob)
       mask - other garments, skin, walls, chains, anything - gets
       painted a neutral white, and padding around the crop is kept
       minimal, so the result is zoomed in tight on the garment itself
       rather than a loose box that happens to contain it."""
    ids = [_seg_label2id[n] for n in label_names if n in _seg_label2id]
    mask = np.isin(pixel_labels, ids)
    if not mask.any():
        return None

    skin_free = mask & ~_skin_mask(img)
    if skin_free.any():  # don't wipe out a garment that's genuinely skin-toned entirely
        mask = skin_free

    labeled, num_blobs = ndimage.label(mask)
    if num_blobs > 1:
        sizes = ndimage.sum(mask, labeled, range(1, num_blobs + 1))
        mask = labeled == (int(np.argmax(sizes)) + 1)

    if erode_px > 0:
        eroded = ndimage.binary_erosion(mask, iterations=erode_px)
        if eroded.any():  # don't erode away a thin/small garment (e.g. a belt) entirely
            mask = eroded

    ys, xs = np.where(mask)
    y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
    h, w = pixel_labels.shape
    pad_y, pad_x = int((y1 - y0) * pad_frac), int((x1 - x0) * pad_frac)
    y0, y1 = max(0, y0 - pad_y), min(h - 1, y1 + pad_y)
    x0, x1 = max(0, x0 - pad_x), min(w - 1, x1 + pad_x)

    isolated = np.array(img).copy()
    isolated[~mask] = 255  # neutral fill outside this garment's own pixels
    return Image.fromarray(isolated).crop((int(x0), int(y0), int(x1) + 1, int(y1) + 1))

def detect_garments(img: Image.Image) -> dict:
    """Returns {"top": PIL.Image|None, "pants": PIL.Image|None, "shoes":
    PIL.Image|None} - a cropped photo of each garment this model actually
    found in img, or None for anything it didn't (out of frame, occluded,
    not present)."""
    pixel_labels = _segment_pixel_labels(img)
    return {slot: _crop_to_labels(img, pixel_labels, names) for slot, names in GARMENT_LABEL_GROUPS.items()}

def crop_to_garment(img: Image.Image, category: str) -> Image.Image:
    """Crops img down to just the `category` garment region (e.g. isolates
    the shoes out of a full scene) so backgrounds, other garments, and the
    person wearing them don't leak into that item's embedding. Falls back
    to the original photo unchanged when category isn't one this model
    detects (outerwear, accessories, ...), or when nothing was found in
    this particular photo - which is also the correct behavior for an
    already-isolated product shot (no person to parse means nothing to
    crop to, and the original photo is already the right crop)."""
    label_names = GARMENT_LABEL_GROUPS.get(category.lower())
    if not label_names:
        return img
    pixel_labels = _segment_pixel_labels(img)
    crop = _crop_to_labels(img, pixel_labels, label_names)
    return crop if crop is not None else img

COLOR_PALETTE_SIZE = 3  # dominant colors kept per garment

def color_palette(img: Image.Image, k: int = COLOR_PALETTE_SIZE) -> list:
    """The k most common colors in img's real pixels, each with its share
    of the garment (weights sum to 1) - a richer color-match signal than
    a single average. A shirt that's mostly black fabric with a small
    colorful graphic averages out to "black", silently throwing away
    exactly the accent colors (gold/green/red text, say) that actually
    matter for judging a "detailed" match - a plain mean can't represent
    "mostly black, with some green and gold" at all. k-means clustering on
    the real pixel colors keeps that whole palette, weighted by how much
    of the garment each color actually covers, instead of collapsing it
    to one blended color no real color in the photo may even resemble.

    _crop_to_labels fills every non-garment pixel with exact pure white
    (255,255,255); real photographed pixels - even on a genuinely white
    garment - essentially never land there, so a simple equality check
    reliably tells "real garment pixel" from "background fill" without
    needing the original segmentation mask passed in here."""
    arr = np.array(img).reshape(-1, 3).astype(np.float32)
    real_pixels = arr[~np.all(arr >= 254, axis=1)]
    if len(real_pixels) == 0:
        real_pixels = arr  # fully white garment, or an uncropped photo

    k_eff = min(k, len(np.unique(real_pixels, axis=0)))
    if k_eff <= 1:
        return [{"color": real_pixels.mean(axis=0).tolist(), "weight": 1.0}]

    labels = KMeans(n_clusters=k_eff, n_init=3, random_state=0).fit(real_pixels)
    counts = np.bincount(labels.labels_, minlength=k_eff)
    weights = counts / counts.sum()
    order = np.argsort(-weights)
    return [{"color": labels.cluster_centers_[i].tolist(), "weight": float(weights[i])} for i in order]

def _rgb_to_lab(rgb) -> np.ndarray:
    """Standard sRGB (D65) -> CIE LAB conversion, vectorized over an
    (..., 3) array of 0-255 RGB values. Plain RGB Euclidean distance
    doesn't track how different two colors actually LOOK to a person -
    the RGB channels don't correspond to how the eye weights brightness
    vs. color, so two visually-close colors can sit far apart in raw RGB
    while two visually-different ones sit close. LAB is built specifically
    so Euclidean distance in this space (delta-E) approximates perceived
    difference much more closely."""
    arr = np.asarray(rgb, dtype=np.float64) / 255.0
    arr = np.where(arr > 0.04045, ((arr + 0.055) / 1.055) ** 2.4, arr / 12.92)

    srgb_to_xyz = np.array([
        [0.4124564, 0.3575761, 0.1804375],
        [0.2126729, 0.7151522, 0.0721750],
        [0.0193339, 0.1191920, 0.9503041],
    ])
    xyz = arr @ srgb_to_xyz.T

    xyz = xyz / np.array([0.95047, 1.0, 1.08883])  # normalize by the D65 reference white
    xyz = np.where(xyz > 0.008856, np.cbrt(xyz), (7.787 * xyz) + 16 / 116)

    x, y, z = xyz[..., 0], xyz[..., 1], xyz[..., 2]
    return np.stack([(116 * y) - 16, 500 * (x - y), 200 * (y - z)], axis=-1)

LAB_DISTANCE_CEILING = 150.0  # calibrated so black-vs-white (delta-E 100) and
# most realistic garment-color pairs land well inside 0-1; only near the
# most extreme, fully-saturated opposite hues does similarity clamp to 0.

def _rgb_similarity(color_a: list, color_b: list) -> float:
    """0-1 score, 1 = perceptually identical color. Compares in CIE LAB
    space (delta-E) rather than raw RGB distance, so "how similar do these
    look" tracks human color perception instead of a flat per-channel
    difference."""
    delta_e = float(np.linalg.norm(_rgb_to_lab(np.array(color_a)) - _rgb_to_lab(np.array(color_b))))
    return max(0.0, 1.0 - delta_e / LAB_DISTANCE_CEILING)

def palette_similarity(palette_a: list, palette_b: list) -> float:
    """Weighted best-match between two color palettes: each color in A is
    matched to whichever color in B is closest, weighted by how much of
    garment A that color actually covers, then symmetrized so a match
    counts however either side is queried from. A green shoe should score
    well against a mostly-black top that has a green accent - a single-
    average comparison could only ever say "not black enough"; matching
    per-color lets the shared green earn credit while black-vs-green
    elsewhere in the palette still costs it."""
    def best_match_score(pa, pb):
        return sum(c_a["weight"] * max(_rgb_similarity(c_a["color"], c_b["color"]) for c_b in pb) for c_a in pa)
    return (best_match_score(palette_a, palette_b) + best_match_score(palette_b, palette_a)) / 2

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
    # Pull every item in this item-bank category as additional candidates,
    # WITHOUT the client re-uploading their photo data - it's already
    # stored server-side. This is what makes a bank of any real size
    # (dozens+ photos) usable at all: embedding even a handful of shrunk
    # photos directly in inventory_json can still blow past Starlette's
    # 1MB Form field cap once there are enough of them.
    pants_bank_category: Optional[str] = None
    shoes_bank_category: Optional[str] = None

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

def open_image(raw_bytes: bytes) -> Image.Image:
    """PIL.Image.open() ignores EXIF orientation - most phone cameras
    save pixels in the sensor's native (often landscape) orientation and
    rely on an EXIF Orientation tag for viewers to rotate on display, so
    every normal viewer shows the photo upright but PIL would process it
    exactly as stored, sideways. That silently fed sideways pixels into
    every embedding and, once crop_to_garment started drawing tight boxes
    around specific garments instead of using the whole photo, turned into
    visibly rotated crops. exif_transpose bakes the intended rotation into
    the pixels once, up front, before anything downstream touches them."""
    return ImageOps.exif_transpose(Image.open(io.BytesIO(raw_bytes))).convert("RGB")

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

def _encode_and_shrink_for_bank(img: Image.Image):
    """Shrinks + JPEG-encodes img for storage (CLIP resizes every image
    internally anyway (~224x224), so a full camera photo is wasted bloat -
    and this can end up embedded in inventory_json, a Form field Starlette
    hard-caps at 1MB), AND computes its CLIP embedding once here at upload
    time. Caching the embedding on the bank item means /generate-outfit-
    from-top can look it up instead of re-decoding and re-running CLIP on
    the same unchanged photo on every single request - with a bank of
    dozens of items, that repeated re-encoding was the entire cost of the
    endpoint. img should already be cropped to the relevant garment (see
    crop_to_garment) - this step only handles storage sizing + encoding."""
    img = img.copy()
    img.thumbnail((BANK_MAX_DIMENSION, BANK_MAX_DIMENSION))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=85)
    b64 = base64.b64encode(buf.getvalue()).decode("ascii")
    embedding = model.encode(img).tolist()
    return b64, embedding

BANK_DEDUP_THRESHOLD = 0.999  # cosine similarity above which two photos in the
# same category are treated as the same physical item, not two different ones.

def _find_duplicate_item(items: list, category: str, embedding: list):
    """Same physical item photographed/uploaded more than once (most often
    the same shoe or pants re-submitted across several /add-fit ratings)
    shouldn't turn into separate bank entries - every rating still trains
    fit_model.pkl on its own outfit_vector regardless, this only stops the
    browsable pool (and resolve_candidates' match options) from filling up
    with near-identical clones of one real item."""
    candidate = np.array(embedding, dtype=np.float32)
    candidate_norm = np.linalg.norm(candidate)
    if candidate_norm == 0:
        return None
    for item in items:
        if item["category"].lower() != category.lower() or "embedding" not in item:
            continue
        existing = np.array(item["embedding"], dtype=np.float32)
        existing_norm = np.linalg.norm(existing)
        if existing_norm == 0:
            continue
        sim = float(candidate @ existing) / (candidate_norm * existing_norm)
        if sim >= BANK_DEDUP_THRESHOLD:
            return item
    return None

# Common synonyms for the same slot get folded into one canonical bank
# category at upload time - mirrors test_ui.html's pickDefaultCategory
# alias list, so a zip folder named "sneakers" or "kicks" lands in the
# same place a "shoes" folder would, instead of splintering into a
# separate category that has to be merged by hand afterward.
CATEGORY_ALIASES = {
    "sneakers": "shoes", "kicks": "shoes", "footwear": "shoes", "trainers": "shoes",
    "bottoms": "pants", "bottom": "pants", "jeans": "pants", "trousers": "pants", "shorts": "pants",
}

def normalize_category(category: str) -> str:
    return CATEGORY_ALIASES.get(category.strip().lower(), category.strip())

def _store_bank_item(items: list, category: str, description: str, img: Image.Image, source: str, label: Optional[str]) -> tuple:
    """Shared tail end of add_bank_item and the single_full_fit_image path
    in /add-fit: both need dedup-and-append against an already-loaded
    items list, but single_full_fit_image already has its per-garment crop
    from detect_garments and shouldn't pay for a second segmentation pass
    on what's already an isolated crop. Returns (id, is_new, embedding);
    does NOT save to disk - the caller does that once after all items in
    a batch are added, instead of once per item."""
    image_b64, embedding = _encode_and_shrink_for_bank(img)
    palette = color_palette(img)

    duplicate = _find_duplicate_item(items, category, embedding)
    if duplicate is not None:
        if label is not None:
            duplicate["label"] = label
        return duplicate["id"], False, embedding, palette

    next_id = max((i["id"] for i in items), default=0) + 1
    items.append({
        "id": next_id,
        "category": category,
        "description": description,
        "image_base64": image_b64,
        "embedding": embedding,
        "palette": palette,
        "source": source,
        "label": label,
        "added_at": datetime.now().isoformat(),
    })
    return next_id, True, embedding, palette

def add_bank_item(category: str, description: str, raw_bytes: bytes, source: str, label: Optional[str] = None) -> tuple:
    """Returns (id, is_new, embedding, palette) - is_new is False when this
    photo matched an existing bank item closely enough to be merged into
    it instead. label is the good/bad rating of the OUTFIT this photo
    came from (via /add-fit), not a judgment on the item itself in
    isolation - a zip upload has no rating context, so it stays None
    (unrated)."""
    category = normalize_category(category)
    items = load_item_bank()
    img = open_image(raw_bytes)
    img = crop_to_garment(img, category)
    result = _store_bank_item(items, category, description, img, source, label)
    save_item_bank(items)
    return result


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
    duplicates_skipped = 0
    processed = 0
    for name in zf.namelist():
        if name.endswith("/") or "__MACOSX" in name:
            continue
        parts = name.split("/")
        if len(parts) < 2:
            continue  # not inside a category subfolder, skip
        category = normalize_category(parts[0])
        filename = parts[-1]
        if os.path.splitext(filename)[1].lower() not in BANK_IMAGE_EXTS:
            continue
        try:
            _, is_new, _, _ = add_bank_item(
                category=category,
                description=describe_from_filename(filename),
                raw_bytes=zf.read(name),
                source="zip_upload",
            )
        except Exception:
            continue  # skip unreadable files rather than failing the whole batch
        processed += 1
        if is_new:
            added_by_category[category] = added_by_category.get(category, 0) + 1
        else:
            duplicates_skipped += 1

    if processed == 0:
        raise HTTPException(status_code=400, detail="No images found inside category subfolders (e.g. sneakers/, shirts/).")

    return {"added": added_by_category, "duplicates_skipped": duplicates_skipped, "total_bank_size": len(load_item_bank())}


@app.get("/bank", tags=["Item Bank"])
def get_bank(category: Optional[str] = None, label: Optional[str] = None, _: None = Depends(require_api_key)):
    """The current item bank, optionally filtered to one category and/or
    label ('good', 'bad', or 'unrated' for items with no rating context,
    like zip uploads)."""
    items = load_item_bank()
    categories = sorted(set(i["category"] for i in items))
    if category:
        items = [i for i in items if i["category"].lower() == category.lower()]
    if label:
        if label.lower() == "unrated":
            items = [i for i in items if i.get("label") is None]
        else:
            items = [i for i in items if i.get("label") == label.lower()]
    return {"count": len(items), "categories": categories, "items": items}


@app.patch("/bank/{item_id}", tags=["Item Bank"])
def update_bank_item(item_id: int, label: Optional[str] = Form(None), description: Optional[str] = Form(None), _: None = Depends(require_api_key)):
    """Hand-edit a single bank item's label and/or description - e.g. from
    a browsing/sorting UI, correcting a bulk zip upload's auto-generated
    description, or manually labeling items that came in unrated."""
    if label is not None:
        label_clean = label.strip().lower()
        if label_clean not in ("good", "bad", "unrated", ""):
            raise HTTPException(status_code=400, detail="label must be 'good', 'bad', 'unrated', or empty")

    items = load_item_bank()
    item = next((i for i in items if i["id"] == item_id), None)
    if item is None:
        raise HTTPException(status_code=404, detail=f"No bank item with id {item_id}")

    if label is not None:
        item["label"] = None if label_clean in ("", "unrated") else label_clean
    if description is not None:
        item["description"] = description

    save_item_bank(items)
    return {"id": item["id"], "category": item["category"], "description": item["description"], "label": item.get("label")}


def resolve_candidates(explicit_items: List[ClothingItem], bank_category: Optional[str], bank_items: list) -> List[ClothingItem]:
    """Merges any explicitly-supplied items with every item from the given
    bank category, pulling their photo data straight from the already-
    loaded bank_items instead of requiring the client to have sent it (or
    re-reading item_bank.json from disk a second time for the other
    slot). IDs aren't touched - the client is responsible for using ids
    that won't collide with real bank ids (e.g. negative ids for one-off
    manual uploads), since the response echoes back whichever id matched
    so the caller can look up the right item on their end."""
    if not bank_category:
        return explicit_items
    bank_matches = [
        ClothingItem(id=i["id"], description=i["description"], image_base64=i["image_base64"])
        for i in bank_items
        if i["category"].lower() == bank_category.lower()
    ]
    return explicit_items + bank_matches


# ---------------------------------------------------------------------
# Live "general fit" brain: trained at runtime from outfits you rate via
# /add-fit, independent of the curated silhouette_model.pkl script. Uses
# real photo embeddings throughout, both at training time and (when
# available) at inference time, to keep the two consistent.
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
        img = open_image(file.file.read())
        return model.encode(img)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to process image {file.filename}: {str(e)}")

def get_image_embedding_and_bank(file: Optional[UploadFile], category: str, label: Optional[str] = None):
    """Same as get_image_embedding, but also adds the photo to the item
    bank under the given category - used for /add-fit's separate item
    slots (top/bottom/shoes/outerwear). add_bank_item crops the photo down
    to just that garment first (see crop_to_garment), so a background or
    the rest of an outfit sneaking into frame doesn't leak into the
    embedding - the returned embedding is exactly what got stored, so
    fit_model.pkl trains on the same signal /generate-outfit-from-top
    later matches against. label is the good/bad rating the outfit this
    photo was part of received."""
    if not file or not file.filename:
        return np.zeros(512, dtype=np.float32)
    raw = file.file.read()
    try:
        open_image(raw)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to process image {file.filename}: {str(e)}")
    _, _, embedding, _ = add_bank_item(category=category, description=describe_from_filename(file.filename), raw_bytes=raw, source="training_data", label=label)
    return np.array(embedding, dtype=np.float32)

def get_items_embeddings_for_matching(items: List[ClothingItem], garment_slot: str, embedding_cache: Optional[dict] = None, palette_cache: Optional[dict] = None):
    """Batch version of the old per-item encode: decodes every item's
    photo (or falls back to its text description) up front, then runs
    CLIP exactly once per modality - all images together, all
    descriptions together - instead of once per item. A single batched
    .encode() call is dramatically faster than N sequential single-item
    calls (CLIP has real fixed overhead per call, especially on CPU),
    which matters a lot once a bank category has dozens of items.

    embedding_cache/palette_cache map item id -> precomputed embedding/
    color palette (as stored on bank items - see _encode_and_shrink_for_
    bank and color_palette). Bank items hit these caches and skip decode+
    encode (and the crop below) entirely, since they were already cropped
    once at upload time - only genuinely new (non-bank, manually-attached)
    items get cropped and encoded here. garment_slot ("pants" or "shoes")
    tells crop_to_garment which region of a manually-attached photo
    actually matters.

    Returns a list of (embedding_or_None, is_image, palette_or_None), same
    order as `items`. is_image tells the caller whether that embedding is
    safe to reuse for fit_clf (image-trained) without re-encoding; palette
    is None for text-only items (nothing to read a color off of)."""
    embedding_cache = embedding_cache or {}
    palette_cache = palette_cache or {}
    results = [None] * len(items)
    palettes = [None] * len(items)
    image_indices, images = [], []
    text_indices, texts = [], []

    for idx, item in enumerate(items):
        if item and item.id in embedding_cache:
            results[idx] = (np.array(embedding_cache[item.id], dtype=np.float32), True)
            palettes[idx] = palette_cache.get(item.id)
        elif item and item.image_base64:
            try:
                img_bytes = base64.b64decode(item.image_base64)
                img = open_image(img_bytes)
                img = crop_to_garment(img, garment_slot)
            except Exception as e:
                raise HTTPException(status_code=400, detail=f"Failed to decode image for item {item.id}: {str(e)}")
            image_indices.append(idx)
            images.append(img)
            palettes[idx] = color_palette(img)
        elif item and item.description:
            text_indices.append(idx)
            texts.append(item.description)
        # else: nothing to embed this item with - stays None below.

    if images:
        img_embs = model.encode(images)
        for i, idx in enumerate(image_indices):
            results[idx] = (img_embs[i], True)
    if texts:
        text_embs = model.encode(texts)
        for i, idx in enumerate(text_indices):
            results[idx] = (text_embs[i], False)

    return [((results[i] or (None, False))[0], (results[i] or (None, False))[1], palettes[i]) for i in range(len(items))]

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

    A full-fit photo gets run through garment segmentation to find the
    actual top/pants/shoes regions in the scene, crop each one out, and
    bank + embed them individually - same as if you'd uploaded three
    separate cropped photos to top/bottom/shoes yourself, just automatic.
    """
    label_clean = label.strip().lower()
    if label_clean not in ["good", "bad"]:
        raise HTTPException(status_code=400, detail="Label parameter must be exactly 'good' or 'bad'")

    provided = [f for f in [single_full_fit_image, top, bottom, shoes, outerwear] if f and f.filename]
    if not provided:
        raise HTTPException(status_code=400, detail="Provide at least one image: a full-fit photo, or any of top/bottom/shoes/outerwear.")

    detected_regions = None
    if single_full_fit_image and single_full_fit_image.filename:
        raw = single_full_fit_image.file.read()
        try:
            fit_img = open_image(raw)
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"Failed to process image {single_full_fit_image.filename}: {str(e)}")

        crops = detect_garments(fit_img)
        detected_regions = {slot: crop is not None for slot, crop in crops.items()}

        if not any(crops.values()):
            # Segmentation didn't find a person wearing clothes in this
            # photo at all - fall back to the old whole-photo-in-every-
            # slot behavior rather than training on an all-zero vector.
            fit_emb = model.encode(fit_img)
            outfit_vector = np.concatenate([fit_emb, fit_emb, fit_emb, fit_emb])
        else:
            bank_items = load_item_bank()

            def _embed_detected_slot(crop, category):
                if crop is None:
                    return np.zeros(512, dtype=np.float32)
                _, _, embedding, _ = _store_bank_item(
                    bank_items, category, f"{category} detected from full-fit photo",
                    crop, "full_fit_detection", label_clean,
                )
                return np.array(embedding, dtype=np.float32)

            top_emb = _embed_detected_slot(crops["top"], "top")
            bottom_emb = _embed_detected_slot(crops["pants"], "pants")
            shoes_emb = _embed_detected_slot(crops["shoes"], "shoes")
            outerwear_emb = np.zeros(512, dtype=np.float32)  # not a label this segmentation model produces
            save_item_bank(bank_items)
            outfit_vector = np.concatenate([top_emb, bottom_emb, shoes_emb, outerwear_emb])
    else:
        top_emb = get_image_embedding_and_bank(top, "top", label=label_clean)
        bottom_emb = get_image_embedding_and_bank(bottom, "pants", label=label_clean)
        shoes_emb = get_image_embedding_and_bank(shoes, "shoes", label=label_clean)
        outerwear_emb = get_image_embedding_and_bank(outerwear, "outerwear", label=label_clean)
        outfit_vector = np.concatenate([top_emb, bottom_emb, shoes_emb, outerwear_emb])

    # Load fresh rather than trusting an in-memory copy - see
    # load_fit_training_data's docstring-equivalent note above train_fit_brain.
    X_memory, y_memory = load_fit_training_data()
    X_memory.append(outfit_vector)
    y_memory.append(1 if label_clean == "good" else 0)

    status = train_fit_brain(X_memory, y_memory)

    response = {
        "status": f"Outfit recorded as {label_clean.upper()}",
        "engine_message": status,
        "total_dataset_samples": len(y_memory),
    }
    if detected_regions is not None:
        response["detected_regions"] = detected_regions
    return response


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
        bank_items = load_item_bank()  # loaded once, reused for both slots below
        inventory.pants = resolve_candidates(inventory.pants, inventory.pants_bank_category, bank_items)
        inventory.shoes = resolve_candidates(inventory.shoes, inventory.shoes_bank_category, bank_items)
        # Bank items carry a precomputed embedding + color palette (see
        # _encode_and_shrink_for_bank / color_palette) - only items
        # missing one (older bank entries from before these existed) fall
        # through to being decoded + encoded/read fresh below.
        bank_embedding_cache = {i["id"]: i["embedding"] for i in bank_items if "embedding" in i}
        bank_palette_cache = {i["id"]: i["palette"] for i in bank_items if "palette" in i}

        top_image = open_image(await file.read())
        top_image = crop_to_garment(top_image, "top")
        top_embedding = model.encode(top_image)
        top_palette = color_palette(top_image)

        # Load your separate custom trained brains if they exist
        sil_clf = pickle.load(open("silhouette_model.pkl", "rb")) if os.path.exists("silhouette_model.pkl") else None
        fit_clf = pickle.load(open(FIT_MODEL_FILE, "rb")) if os.path.exists(FIT_MODEL_FILE) else None

        # Score every pants+shoes pairing jointly instead of picking pants
        # by raw top-similarity alone and only then bringing the trained
        # brains in for shoes - that left pants ranking blind to actual
        # outfit compatibility, so whichever pants item was just visually
        # closest to the top always won regardless of what the color match,
        # sil_clf, or fit_clf thought of the resulting outfit. Batched:
        # every photo is decoded once, and every classifier is called once
        # across the whole pants x shoes grid, not once per pair.
        pants_results = get_items_embeddings_for_matching(inventory.pants, "pants", bank_embedding_cache, bank_palette_cache)
        shoes_results = get_items_embeddings_for_matching(inventory.shoes, "shoes", bank_embedding_cache, bank_palette_cache)

        def _stack(results):
            n = len(results)
            embs = np.zeros((n, 512))
            valid = np.zeros(n, dtype=bool)
            is_image = np.zeros(n, dtype=bool)
            color_sim = np.zeros(n)  # 0 (neutral) when there's no color to compare (text-only items)
            for i, (emb, img, palette) in enumerate(results):
                if emb is not None:
                    embs[i] = emb
                    valid[i] = True
                    is_image[i] = img
                if palette is not None:
                    color_sim[i] = palette_similarity(top_palette, palette)
            return embs, valid, is_image, color_sim

        def _cos_to_top(M):
            norms = np.linalg.norm(M, axis=1)
            norms[norms == 0] = 1  # avoid div-by-zero; those rows are masked to 0 anyway
            return (M @ top_embedding) / (norms * np.linalg.norm(top_embedding))

        P, pants_valid, pants_is_image, pants_color_sim = _stack(pants_results)
        S, shoes_valid, shoes_is_image, shoes_color_sim = _stack(shoes_results)
        Np, Ns = len(inventory.pants), len(inventory.shoes)

        pants_scores = np.zeros(Np)
        shoes_scores = np.zeros(Ns)

        if Ns == 0:
            # Nothing to pair pants against - rank by raw top-similarity
            # plus how well its color reads against the top, same as
            # before any of the trained brains existed (they all need a
            # shoe in the picture too).
            pants_scores = (_cos_to_top(P) + pants_color_sim * 1.5) * pants_valid if Np else pants_scores
        else:
            # Real pants candidates pair against every shoe. With none
            # supplied, fall back to a single zero-padded virtual pants row
            # (mirrors the old best_pants_emb=zeros default) so shoes still
            # get scored by sil_clf/fit_clf even with no pants in play.
            if Np:
                P_eff, valid_eff, is_image_eff, pants_color_sim_eff = P, pants_valid, pants_is_image, pants_color_sim
            else:
                P_eff, valid_eff, is_image_eff = np.zeros((1, 512)), np.array([True]), np.array([False])
                pants_color_sim_eff = np.zeros(1)  # no real pants candidate to read a color off of
            Np_eff = P_eff.shape[0]

            pants_sim = _cos_to_top(P_eff)
            shoes_sim = _cos_to_top(S)
            combo_score = pants_sim[:, None] + shoes_sim[None, :]

            # Explicit color match against the top - CLIP similarity alone
            # can rate a black shoe as a fine match for a white top based
            # on style/shape, so this gives ranking a direct, reliable
            # sense of color. Supersedes color_model.pkl entirely: that
            # classifier was trained on 4 hand-written examples and never
            # grew from real ratings (unlike fit_model.pkl), so it was
            # pure noise sitting alongside a much more reliable signal.
            combo_score += pants_color_sim_eff[:, None] * 1.5 + shoes_color_sim[None, :] * 1.5

            # silhouette_model and fit_model were both trained with a real
            # "bottom" embedding in that slot, so they score every actual
            # pants+shoes pairing rather than a fixed one.
            if sil_clf or fit_clf:
                n = Np_eff * Ns
                combo_vec = np.concatenate([
                    np.tile(top_embedding, (n, 1)),
                    np.repeat(P_eff, Ns, axis=0),
                    np.tile(S, (Np_eff, 1)),
                    np.zeros((n, 512)),
                ], axis=1)
                if sil_clf:
                    combo_score += sil_clf.predict_proba(combo_vec)[:, 1].reshape(Np_eff, Ns) * 1.5
                if fit_clf:
                    # fit_model.pkl always requires a real shoe photo - text
                    # descriptions live in a different part of CLIP's space
                    # than photos, so scoring one with an image-trained
                    # model is out-of-distribution input. Pants only needs
                    # to be a real photo when real pants candidates are
                    # actually in play (Np>0); the zero-padded virtual row
                    # used when no pants are supplied isn't actually out-
                    # of-distribution for this model - /add-fit itself
                    # zero-pads the same way whenever a rating skips the
                    # pants slot, so fit_clf has genuinely trained on that
                    # exact pattern. Requiring a real pants photo here too
                    # was silently zeroing out fit_clf's entire contribution
                    # for shoes-only matching, meaning good/bad ratings had
                    # no effect on shoe ranking at all whenever no pants
                    # were supplied.
                    pants_ok = is_image_eff if Np else np.ones(Np_eff, dtype=bool)
                    fit_mask = pants_ok[:, None] & shoes_is_image[None, :]
                    combo_score += fit_clf.predict_proba(combo_vec)[:, 1].reshape(Np_eff, Ns) * fit_mask * 1.5

            # Items with neither a photo nor a description have nothing to
            # score them against - keep them at a flat 0 no matter what
            # they'd be paired with.
            combo_score *= valid_eff[:, None] * shoes_valid[None, :]

            best_i, best_j = np.unravel_index(np.argmax(combo_score), combo_score.shape)
            shoes_scores = combo_score[best_i, :]
            if Np:
                pants_scores = combo_score[:, best_j]

        ranked_pants = [
            {"id": p.id, "description": p.description, "match_score": float(pants_scores[i])}
            for i, p in enumerate(inventory.pants)
        ]
        ranked_pants.sort(key=lambda x: x["match_score"], reverse=True)

        ranked_shoes = [
            {"id": s.id, "description": s.description, "match_score": float(shoes_scores[i])}
            for i, s in enumerate(inventory.shoes)
        ]
        ranked_shoes.sort(key=lambda x: x["match_score"], reverse=True)

        return {
            "recommended_outfit": {
                "top_status": "Base Item Provided",
                "best_pants_match": ranked_pants if ranked_pants else None,
                "best_shoes_match": ranked_shoes if ranked_shoes else None
            }
        }
    except HTTPException:
        raise  # already has the right status code (e.g. 400 for bad input) - don't flatten it to a 500
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Engine error: {str(e)}")


def _read_json_if_exists(path):
    if not os.path.exists(path):
        return None
    with open(path) as f:
        return json.load(f)


@app.get("/stats", tags=["AI Style Training"])
def get_stats(_: None = Depends(require_api_key)):
    """How much data each brain was actually trained on. color_model.pkl
    is deliberately absent here - see palette_similarity's docstring in
    this file for why it was retired in favor of direct color matching."""
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
        "silhouette_model": _read_json_if_exists("silhouette_model_meta.json") or {"trained": os.path.exists("silhouette_model.pkl"), "note": "meta file missing - rerun train_silhouette.py to record counts"},
        "item_bank": _bank_stats(),
    }


def _bank_stats():
    items = load_item_bank()
    by_category: dict[str, int] = {}
    by_source: dict[str, int] = {}
    by_label: dict[str, int] = {}
    for i in items:
        by_category[i["category"]] = by_category.get(i["category"], 0) + 1
        by_source[i["source"]] = by_source.get(i["source"], 0) + 1
        label_key = i.get("label") or "unrated"
        by_label[label_key] = by_label.get(label_key, 0) + 1
    return {"total_items": len(items), "by_category": by_category, "by_source": by_source, "by_label": by_label}


@app.get("/docs", include_in_schema=False)
async def custom_swagger_ui_html():
    # Keep your custom dark mode theme rendering intact
    html_response = get_swagger_ui_html(openapi_url=app.openapi_url, title="FIT//LAB Core Controls", swagger_favicon_url="https://tiangolo.com")
    html_body = html_response.body.decode("utf-8")
    modified_body = html_body.replace("</body>", "<style>body { background-color: #0d0d0d !important; color: #ffffff !important; font-family: monospace; }</style></body>")
    return HTMLResponse(content=modified_body, status_code=200)
