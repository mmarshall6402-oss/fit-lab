"""
Quick manual test for /generate-outfit-from-top against your actual
running server (uvicorn must already be up on 127.0.0.1:8000).

Usage:
    python test_generate_outfit.py <top_image> <shoe_image_1> [<shoe_image_2> ...]

Encodes the shoe images as base64 ClothingItems (so fit_model.pkl can
actually score them - see main.py's ClothingItem.image_base64), sends
the top image as the file upload, and prints the ranked response.

This is just a smoke test to confirm the pipeline works end-to-end with
real photos - reusing outfit photos as stand-in "shoes" is fine for
that, but for a real inventory you'd want actual individual item photos
(see build_catalog.py).
"""
import io
import sys
import os
import json
import base64
from pathlib import Path

try:
    import requests
except ImportError:
    raise SystemExit("Missing dependency - run: pip install requests")

from PIL import Image

API_KEY = os.environ.get("FASHION_AI_API_KEY")
if not API_KEY:
    raise SystemExit("Set FASHION_AI_API_KEY in this terminal first (same key your running server is using).")

BASE_URL = "http://127.0.0.1:8000"

# CLIP resizes every image internally anyway (to ~224x224), so a full-size
# camera photo (often several MB) adds nothing but bloat - and inventory_json
# is a Form field, which Starlette hard-caps at 1MB with no override exposed
# through FastAPI's public API. Shrinking client-side avoids hitting that
# cap instead of silently 400ing on real photos.
MAX_DIMENSION = 768


def shrink_and_encode(path: Path) -> str:
    img = Image.open(path).convert("RGB")
    img.thumbnail((MAX_DIMENSION, MAX_DIMENSION))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=85)
    return base64.b64encode(buf.getvalue()).decode("ascii")


def main():
    if len(sys.argv) < 3:
        raise SystemExit("Usage: python test_generate_outfit.py <top_image> <shoe_image_1> [<shoe_image_2> ...]")

    top_path = Path(sys.argv[1])
    shoe_paths = [Path(p) for p in sys.argv[2:]]

    inventory = {
        "shoes": [
            {"id": i + 1, "description": p.stem, "image_base64": shrink_and_encode(p)}
            for i, p in enumerate(shoe_paths)
        ]
    }

    top_buf = io.BytesIO()
    Image.open(top_path).convert("RGB").save(top_buf, format="JPEG", quality=90)
    top_buf.seek(0)

    resp = requests.post(
        f"{BASE_URL}/generate-outfit-from-top",
        data={"inventory_json": json.dumps(inventory)},
        files={"file": (top_path.name, top_buf, "image/jpeg")},
        headers={"X-API-Key": API_KEY},
    )

    print(resp.status_code)
    print(json.dumps(resp.json(), indent=2))


if __name__ == "__main__":
    main()
