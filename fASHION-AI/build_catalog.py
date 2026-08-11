"""
Turn a zip (or folder) of item photos into JSON catalog files shaped
exactly like ClothingItem, ready to paste into the "pants"/"shoes" arrays
of the inventory_json field on /generate-outfit-from-top.

No good/bad label here - that's only for full-outfit photos (see
train_fit_bulk.py). This just builds a reusable, photo-backed inventory
out of individual item pictures (sneakers, shirts, whatever).

Usage:
    python build_catalog.py path/to/items
    python build_catalog.py path/to/items.zip

The folder (or what the zip extracts to) needs one subfolder per
category - the subfolder name becomes the category, used only for
naming the output file:

    items/
        sneakers/
            jordan4_toro.jpg
            jordan4_black_cat.jpg
        shirts/
            orange_graphic_tee.jpg

Produces one file per category, e.g. catalog_sneakers.json:
    [
      {"id": 1, "description": "jordan4 toro", "image_base64": "..."},
      {"id": 2, "description": "jordan4 black cat", "image_base64": "..."}
    ]

description is just the cleaned-up filename - a starting point, not
meant to be perfect. Rename files beforehand for better descriptions, or
edit the JSON after.
"""
import io
import sys
import json
import base64
import shutil
import tempfile
import zipfile
from pathlib import Path
from PIL import Image

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}

# CLIP resizes every image internally anyway (to ~224x224), so a full-size
# camera photo (often several MB) adds nothing but bloat - and this JSON
# ends up pasted into inventory_json, a Form field Starlette hard-caps at
# 1MB with no override exposed through FastAPI's public API. Shrinking here
# avoids hitting that cap later instead of silently 400ing on real photos.
MAX_DIMENSION = 768


def shrink_and_encode(path: Path) -> str:
    img = Image.open(path).convert("RGB")
    img.thumbnail((MAX_DIMENSION, MAX_DIMENSION))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=85)
    return base64.b64encode(buf.getvalue()).decode("ascii")


def resolve_root(path_arg):
    p = Path(path_arg)
    if p.is_dir():
        return p, None
    if p.suffix.lower() == ".zip":
        tmp_dir = tempfile.mkdtemp(prefix="catalog_")
        with zipfile.ZipFile(p) as zf:
            zf.extractall(tmp_dir)
        root = Path(tmp_dir)
        entries = [e for e in root.iterdir() if not e.name.startswith("__MACOSX")]
        if len(entries) == 1 and entries[0].is_dir():
            root = entries[0]
        return root, tmp_dir
    raise SystemExit(f"'{path_arg}' is not a folder or a .zip file")


def describe_from_filename(path: Path) -> str:
    stem = path.stem.replace("_", " ").replace("-", " ")
    return " ".join(stem.split())


def main():
    if len(sys.argv) < 2:
        raise SystemExit("Usage: python build_catalog.py <folder-or-zip-with-one-subfolder-per-category>")

    root, cleanup_dir = resolve_root(sys.argv[1])
    categories = [d for d in sorted(root.iterdir()) if d.is_dir() and not d.name.startswith("__MACOSX")]
    if not categories:
        raise SystemExit(f"No category subfolders found directly inside '{root}'. Expected e.g. sneakers/, shirts/, pants/.")

    next_id = 1
    for cat_dir in categories:
        items = []
        for f in sorted(cat_dir.iterdir()):
            if f.suffix.lower() not in IMAGE_EXTS:
                continue
            img_b64 = shrink_and_encode(f)
            items.append({
                "id": next_id,
                "description": describe_from_filename(f),
                "image_base64": img_b64,
            })
            next_id += 1

        if not items:
            print(f"  {cat_dir.name}: no images found, skipping")
            continue

        out_path = f"catalog_{cat_dir.name}.json"
        with open(out_path, "w") as out:
            json.dump(items, out)
        print(f"  {cat_dir.name}: {len(items)} items -> {out_path}")

    if cleanup_dir:
        shutil.rmtree(cleanup_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
