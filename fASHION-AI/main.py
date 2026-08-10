import os
import io
import json
import secrets
import pickle
import numpy as np
from typing import List, Optional
from fastapi import FastAPI, UploadFile, File, HTTPException, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from fastapi.openapi.docs import get_swagger_ui_html
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer, util
from sklearn.linear_model import LogisticRegression
from PIL import Image

# 1. Initialize FastAPI Application
app = FastAPI(title="⚡ FIT//LAB AI Engine", docs_url=None, redoc_url=None)

# 2. Configure CORS Middleware using production environment variables
_allowed_origins = [o.strip() for o in os.environ.get("ALLOWED_ORIGINS", "").split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_allowed_origins if _allowed_origins else ["*"],
    allow_credentials=True if _allowed_origins else False,
    allow_methods=["POST", "GET"],
    allow_headers=["X-API-Key", "Content-Type"],
)

# 2b. Shared-secret auth check. Fail CLOSED: if the key isn't configured,
# reject every request rather than silently letting unauthenticated
# callers train and read the model.
API_KEY = os.environ.get("FASHION_AI_API_KEY")
def require_api_key(x_api_key: str = Header(default=None)):
    if not API_KEY:
        raise HTTPException(status_code=500, detail="FASHION_AI_API_KEY is not configured on the server")
    if not x_api_key or not secrets.compare_digest(x_api_key, API_KEY):
        raise HTTPException(status_code=401, detail="Missing or invalid X-API-Key")

# 3. Load local CLIP Model and setup Training paths
model = SentenceTransformer('clip-ViT-B-32')
MODEL_FILE = "style_model.pkl"
DATA_FILE = "training_data.pkl"

X_memory, y_memory = [], []
if os.path.exists(DATA_FILE):
    with open(DATA_FILE, "rb") as f:
        X_memory, y_memory = pickle.load(f)

# 4. Data Structures for Outfit Matching
class ClothingItem(BaseModel):
    id: int
    description: str

class WardrobeInventory(BaseModel):
    pants: List[ClothingItem]
    shoes: List[ClothingItem]

# Core Helper Functions
def get_image_embedding(file: Optional[UploadFile]):
    if not file or not file.filename:
        return np.zeros(512, dtype=np.float32)
    try:
        img = Image.open(io.BytesIO(file.file.read())).convert("RGB")
        return model.encode(img)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to process image {file.filename}: {str(e)}")

def build_outfit_vector(
    single_full_fit_image: Optional[UploadFile],
    top: Optional[UploadFile],
    bottom: Optional[UploadFile],
    shoes: Optional[UploadFile],
    outerwear: Optional[UploadFile],
):
    """
    Single source of truth for turning a set of garment photos into the
    2048-dim feature vector the classifier is trained and scored on.
    Both /add-fit and /score-fit call this so training and inference
    always see the same feature construction (same modality: real
    garment images, never text descriptions).
    """
    if single_full_fit_image and single_full_fit_image.filename:
        fit_emb = get_image_embedding(single_full_fit_image)
        return np.concatenate([fit_emb, fit_emb, fit_emb, fit_emb])

    if (not top or not top.filename) or (not bottom or not bottom.filename):
        raise HTTPException(status_code=400, detail="Provide either a single full-fit image OR separate top and bottom images.")

    top_emb = get_image_embedding(top)
    bottom_emb = get_image_embedding(bottom)
    shoes_emb = get_image_embedding(shoes)
    outerwear_emb = get_image_embedding(outerwear)
    return np.concatenate([top_emb, bottom_emb, shoes_emb, outerwear_emb])

def train_brain():
    global X_memory, y_memory
    if len(set(y_memory)) < 2:
        return "Data cached. Need at least 1 Good Fit AND 1 Bad Fit example to train the brain matrix."
    clf = LogisticRegression()
    clf.fit(np.array(X_memory), np.array(y_memory))
    with open(MODEL_FILE, "wb") as f:
        pickle.dump(clf, f)
    with open(DATA_FILE, "wb") as f:
        pickle.dump((X_memory, y_memory), f)
    return "Brain successfully updated and saved to style_model.pkl!"


# =====================================================================
# UNIFIED OUTFIT STYLE TRAINING ENDPOINT
# =====================================================================
@app.post("/add-fit", tags=["AI Style Training"])
async def add_fit(
    label: str,
    single_full_fit_image: Optional[UploadFile] = File(None),
    top: Optional[UploadFile] = File(None),
    bottom: Optional[UploadFile] = File(None),
    shoes: Optional[UploadFile] = File(None),
    outerwear: Optional[UploadFile] = File(None),
    _: None = Depends(require_api_key)
):
    """
    Upload an outfit profile. Label must be 'good' or 'bad'.
    You can either upload ONE picture of someone wearing a full fit,
    OR upload individual images for top, bottom, shoes, and outerwear.
    """
    global X_memory, y_memory

    label_clean = label.strip().lower()
    if label_clean not in ["good", "bad"]:
        raise HTTPException(status_code=400, detail="Label parameter must be exactly 'good' or 'bad'")

    outfit_vector = build_outfit_vector(single_full_fit_image, top, bottom, shoes, outerwear)

    X_memory.append(outfit_vector)
    y_memory.append(1 if label_clean == "good" else 0)

    status = train_brain()

    return {
        "status": f"Outfit recorded as {label_clean.upper()}",
        "engine_message": status,
        "total_dataset_samples": len(y_memory)
    }


# =====================================================================
# TRAINED-MODEL SCORING ENDPOINT (same modality as training: real photos)
# =====================================================================
@app.post("/score-fit", tags=["AI Style Training"])
async def score_fit(
    single_full_fit_image: Optional[UploadFile] = File(None),
    top: Optional[UploadFile] = File(None),
    bottom: Optional[UploadFile] = File(None),
    shoes: Optional[UploadFile] = File(None),
    outerwear: Optional[UploadFile] = File(None),
    _: None = Depends(require_api_key)
):
    """
    Score a real outfit photo set (same upload shape as /add-fit) against
    the trained classifier. This is the correct place to use the trained
    "brain" — it only ever sees the same feature modality it was trained
    on (real garment images), unlike the catalog-matching endpoint below.
    """
    if not os.path.exists(MODEL_FILE):
        raise HTTPException(status_code=400, detail="No trained model yet. Add at least 1 good and 1 bad fit first.")

    outfit_vector = build_outfit_vector(single_full_fit_image, top, bottom, shoes, outerwear)

    with open(MODEL_FILE, "rb") as f:
        clf = pickle.load(f)

    good_fit_probability = float(clf.predict_proba(outfit_vector.reshape(1, -1))[0][1])

    return {
        "good_fit_probability": good_fit_probability,
        "verdict": "good" if good_fit_probability >= 0.5 else "bad"
    }


# =====================================================================
# VISUAL OUTFIT GENERATION ENGINE (catalog text descriptions -> cosine match)
# =====================================================================
@app.post("/generate-outfit-from-top", tags=["Core Matching Engine"])
async def generate_outfit_from_top(inventory_json: str, file: UploadFile = File(...), _: None = Depends(require_api_key)):
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Uploaded file must be an image")

    try:
        data = json.loads(inventory_json)
        inventory = WardrobeInventory(**data)

        top_image = Image.open(io.BytesIO(await file.read())).convert("RGB")
        top_embedding = model.encode(top_image)

        # NOTE: inventory items only carry text descriptions, not photos,
        # so their embeddings live in CLIP's text space while the uploaded
        # top's embedding lives in CLIP's image space. The trained /add-fit
        # classifier was fit purely on real garment photos (image-space
        # vectors), so applying it to these text-space vectors would
        # silently produce meaningless scores (CLIP's image/text "modality
        # gap"). Use plain cosine similarity here instead; use /score-fit
        # when you have real photos for every slot and want the trained
        # classifier's opinion.
        pants_desc = [p.description for p in inventory.pants]
        pants_scores = [0.0] * len(inventory.pants)
        if pants_desc:
            pants_embs = model.encode(pants_desc, convert_to_tensor=True)
            pants_scores = util.cos_sim(top_embedding, pants_embs).tolist()

        ranked_pants = [
            {"id": p.id, "description": p.description, "match_score": float(pants_scores[idx])}
            for idx, p in enumerate(inventory.pants)
        ]
        ranked_pants.sort(key=lambda x: x["match_score"], reverse=True)

        ranked_shoes = []
        for s in inventory.shoes:
            shoe_emb = model.encode(s.description)
            score = float(util.cos_sim(top_embedding, shoe_emb))
            ranked_shoes.append({"id": s.id, "description": s.description, "match_score": score})
        ranked_shoes.sort(key=lambda x: x["match_score"], reverse=True)

        return {
            "recommended_outfit": {
                "top_status": "Base Item Provided",
                "best_pants_match": ranked_pants[0] if ranked_pants else None,
                "best_shoes_match": ranked_shoes[0] if ranked_shoes else None
            },
            "all_options_ranked": {
                "pants_pool": ranked_pants,
                "shoes_pool": ranked_shoes
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Engine error: {str(e)}")


# =====================================================================
# CUSTOM MINIMALIST STREETWEAR DASHBOARD UI (Fail-safe Version)
# =====================================================================
@app.get("/docs", include_in_schema=False)
async def custom_swagger_ui_html():
    custom_css = """
    body { background-color: #0d0d0d !important; color: #ffffff !important; font-family: monospace; }
    .swagger-ui .topbar { background-color: #000000 !important; border-bottom: 2px solid #ff0055; }
    .swagger-ui .info .title { color: #ff0055 !important; font-weight: bold; letter-spacing: 2px; }
    .swagger-ui .info p, .swagger-ui .info a, .swagger-ui .info th { color: #888888 !important; }
    .swagger-ui .scheme-container { background: #000000 !important; border: 1px solid #222; }
    .swagger-ui .opblock.opblock-post { background: rgba(255, 0, 85, 0.03) !important; border: 1px solid #ff0055 !important; }
    .swagger-ui .opblock.opblock-post .opblock-summary { border-bottom: 1px solid rgba(255, 0, 85, 0.2); }
    .swagger-ui .opblock.opblock-post .opblock-summary-method { background: #ff0055 !important; font-weight: bold; }
    .swagger-ui .opblock .opblock-section-header { background: transparent !important; color: #ff0055 !important; }
    .swagger-ui input[type=text], .swagger-ui textarea { background: #1a1a1a !important; color: #fff !important; border: 1px solid #333 !important; }
    .swagger-ui .btn.execute { background-color: #ff0055 !important; border-color: #ff0055 !important; color: #fff !important; font-weight: bold; }
    .swagger-ui .responses-table, .swagger-ui .response-col_status { color: #ff0055 !important; }
    .swagger-ui .response-col_description { color: #cccccc !important; }
    """

    html_response = get_swagger_ui_html(
        openapi_url=app.openapi_url,
        title="FIT//LAB Core Controls",
        swagger_favicon_url="https://tiangolo.com"
    )

    html_body = html_response.body.decode("utf-8")
    custom_style_tags = f"<style>{custom_css}</style>"
    modified_body = html_body.replace("</body>", f"{custom_style_tags}</body>")

    return HTMLResponse(content=modified_body, status_code=200)
