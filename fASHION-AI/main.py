import os
import io
import json
import secrets
import pickle
import numpy as np
from typing import List
from fastapi import FastAPI, UploadFile, File, HTTPException, Depends, Header
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
    allow_methods=["POST"],
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

# Data Structures for incoming inventory
class ClothingItem(BaseModel):
    id: int
    description: str

class WardrobeInventory(BaseModel):
    pants: List[ClothingItem]
    shoes: List[ClothingItem]


@app.post("/generate-outfit-from-top", tags=["Core Matching Engine"])
async def generate_outfit_from_top(inventory_json: str, file: UploadFile = File(...), _: None = Depends(require_api_key)):
    try:
        data = json.loads(inventory_json)
        inventory = WardrobeInventory(**data)

        top_image = Image.open(io.BytesIO(await file.read())).convert("RGB")
        top_embedding = model.encode(top_image)

        # Load your separate custom trained brains if they exist
        color_clf = pickle.load(open("color_model.pkl", "rb")) if os.path.exists("color_model.pkl") else None
        sil_clf = pickle.load(open("silhouette_model.pkl", "rb")) if os.path.exists("silhouette_model.pkl") else None

        # 1. Base text match for pants
        pants_desc = [p.description for p in inventory.pants]
        pants_scores = [0.0] * len(inventory.pants)
        if pants_desc:
            pants_embs = model.encode(pants_desc, convert_to_tensor=True)
            pants_scores = util.cos_sim(top_embedding, pants_embs).tolist()[0]

        ranked_pants = [{"id": p.id, "description": p.description, "match_score": float(pants_scores[idx])} for idx, p in enumerate(inventory.pants)]
        ranked_pants.sort(key=lambda x: x["match_score"], reverse=True)

        # 2. Advanced match for shoes using your custom training files
        ranked_shoes = []
        for s in inventory.shoes:
            shoe_emb = model.encode(s.description)
            best_pants_desc = ranked_pants[0]["description"] if ranked_pants else ""
            pants_emb = model.encode(best_pants_desc) if best_pants_desc else np.zeros(512)

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


@app.get("/docs", include_in_schema=False)
async def custom_swagger_ui_html():
    # Keep your custom dark mode theme rendering intact
    html_response = get_swagger_ui_html(openapi_url=app.openapi_url, title="FIT//LAB Core Controls", swagger_favicon_url="https://tiangolo.com")
    html_body = html_response.body.decode("utf-8")
    modified_body = html_body.replace("</body>", "<style>body { background-color: #0d0d0d !important; color: #ffffff !important; font-family: monospace; }</style></body>")
    return HTMLResponse(content=modified_body, status_code=200)
