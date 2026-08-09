import os
import io
import json
import secrets
from typing import List
from fastapi import FastAPI, UploadFile, File, HTTPException, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer, util
from PIL import Image

# 1. Initialize FastAPI Application
app = FastAPI(title="Complete Fashion AI & Cataloging Engine")

# 2. Configure CORS Middleware. This service is only meant to be called
# server-to-server by the Spring Boot backend, so no browser origins are
# allowed by default; set ALLOWED_ORIGINS to a comma-separated list to
# permit specific origins if a browser client is ever added.
_allowed_origins = [o.strip() for o in os.environ.get("ALLOWED_ORIGINS", "").split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_allowed_origins,
    allow_credentials=False,
    allow_methods=["POST"],
    allow_headers=["X-API-Key", "Content-Type"],
)

# 2b. Shared-secret auth. Every endpoint requires a matching X-API-Key
# header so the service can't be driven by an arbitrary network caller.
API_KEY = os.environ.get("FASHION_AI_API_KEY")

def require_api_key(x_api_key: str = Header(default=None)):
    if not API_KEY:
        raise HTTPException(status_code=500, detail="FASHION_AI_API_KEY is not configured on the server")
    if not x_api_key or not secrets.compare_digest(x_api_key, API_KEY):
        raise HTTPException(status_code=401, detail="Missing or invalid X-API-Key")

# 2c. Local directory scanning is restricted to this base directory so a
# caller can't point the scanner at arbitrary paths on disk.
CATALOG_BASE_DIR = os.environ.get("CATALOG_BASE_DIR")

# 3. Load the local CLIP Multimodal Model into RAM
# This model handles both images and text math cleanly on your Xeon CPU
model = SentenceTransformer('clip-ViT-B-32')

# 4. Define Data Models for Spring Boot Interoperability
class ClothingItem(BaseModel):
    id: int
    description: str

class WardrobeInventory(BaseModel):
    pants: List[ClothingItem]
    shoes: List[ClothingItem]

# 5. Core Vocabulary Pool for AI Description Classification
# You can expand this array with any style tags you like
STYLE_VOCAB = [
    "camo rugged cargo shorts techwear",
    "black formal slim fit tuxedo trousers",
    "light wash relaxed fit blue vintage denim jeans",
    "black leather heavy combat boots",
    "white classic canvas low top minimalist sneakers",
    "patent black leather formal dress shoes",
    "oversized vintage graphic tee streetwear",
    "puffer winter coat warm heavy",
    "khaki classic chino pants smart casual"
]
vocab_embeddings = model.encode(STYLE_VOCAB, convert_to_tensor=True)

# Helper function to compute mathematical cosine similarity rankings
def get_best_match(target_embedding, items_list: List[ClothingItem]):
    if not items_list: 
        return []
    descriptions = [item.description for item in items_list]
    inventory_embeddings = model.encode(descriptions, convert_to_tensor=True)
    scores = util.cos_sim(target_embedding, inventory_embeddings)
    ranked_items = [
        {"id": item.id, "description": item.description, "match_score": float(scores[idx])} 
        for idx, item in enumerate(items_list)
    ]
    ranked_items.sort(key=lambda x: x["match_score"], reverse=True)
    return ranked_items


# =====================================================================
# ENDPOINT 1: LOCAL DIRECTORY SCANNER (Bypasses Browser Upload Forms)
# =====================================================================
@app.post("/catalog-local-directory")
def catalog_local_directory(folder_path: str, _: None = Depends(require_api_key)):
    """
    Pass a folder path relative to CATALOG_BASE_DIR (e.g., "closet").
    The AI reads the files directly from your SSD and generates a JSON catalog.
    """
    if not CATALOG_BASE_DIR:
        raise HTTPException(status_code=500, detail="CATALOG_BASE_DIR is not configured on the server")

    base_dir = os.path.realpath(CATALOG_BASE_DIR)
    resolved_path = os.path.realpath(os.path.join(base_dir, folder_path))
    if resolved_path != base_dir and not resolved_path.startswith(base_dir + os.sep):
        raise HTTPException(status_code=400, detail="folder_path must resolve inside the configured catalog directory")
    folder_path = resolved_path

    if not os.path.exists(folder_path):
        raise HTTPException(status_code=404, detail=f"Local path '{folder_path}' not found")

    if not os.path.isdir(folder_path):
        raise HTTPException(status_code=400, detail="The provided path is not a folder directory")

    generated_inventory = []
    index = 0
    
    for filename in os.listdir(folder_path):
        file_path = os.path.join(folder_path, filename)
        
        # Filter for standard image types
        if not filename.lower().endswith(('.png', '.jpg', '.jpeg', '.webp')):
            continue
            
        try:
            print(f"Direct local disk read: {filename}")
            img = Image.open(file_path).convert("RGB")
            
            # Run CLIP visual vector extraction loop
            img_embedding = model.encode(img, convert_to_tensor=True)
            scores = util.cos_sim(img_embedding, vocab_embeddings)
            best_vocab_idx = int(scores.argmax())
            ai_description = STYLE_VOCAB[best_vocab_idx]
            
            generated_inventory.append({
                "id": 500 + index,
                "local_file_path": file_path,
                "filename": filename,
                "ai_generated_description": ai_description
            })
            index += 1
            
        except Exception as e:
            print(f"Skipping unreadable file {filename}: {str(e)}")
            continue
            
    return {
        "scanned_directory": folder_path,
        "total_items_processed": len(generated_inventory),
        "generated_catalog": generated_inventory
    }


# =====================================================================
# ENDPOINT 2: BULK BROWSER UPLOAD CATALOGER
# =====================================================================
@app.post("/catalog-images")
async def catalog_images(files: List[UploadFile], _: None = Depends(require_api_key)):
    """
    Accepts an array of image file streams submitted via browser/frontend interface.
    """
    generated_inventory = []
    
    if not files:
        raise HTTPException(status_code=400, detail="No files uploaded")
    
    for index, file in enumerate(files):
        if not file.content_type.startswith("image/"):
            continue 
            
        try:
            image_bytes = await file.read()
            img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
            
            img_embedding = model.encode(img, convert_to_tensor=True)
            scores = util.cos_sim(img_embedding, vocab_embeddings)
            best_vocab_idx = int(scores.argmax())
            ai_description = STYLE_VOCAB[best_vocab_idx]
            
            generated_inventory.append({
                "id": 100 + index, 
                "original_filename": file.filename,
                "ai_generated_description": ai_description
            })
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Failed processing {file.filename}: {str(e)}")
            
    return {"generated_catalog": generated_inventory}


# =====================================================================
# ENDPOINT 3: VISUAL OUTFIT GENERATION ENGINE
# =====================================================================
@app.post("/generate-outfit-from-top")
async def generate_outfit_from_top(inventory_json: str, file: UploadFile = File(...), _: None = Depends(require_api_key)):
    """
    Takes an image of a Top/Jacket alongside a string JSON database payload 
    of pants and shoes. Returns cross-category recommendation matches.
    """
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Uploaded file must be an image")
    try:
        data = json.loads(inventory_json)
        inventory = WardrobeInventory(**data)
        
        image_bytes = await file.read()
        top_image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        
        top_embedding = model.encode(top_image, convert_to_tensor=True)
        ranked_pants = get_best_match(top_embedding, inventory.pants)
        ranked_shoes = get_best_match(top_embedding, inventory.shoes)
        
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
