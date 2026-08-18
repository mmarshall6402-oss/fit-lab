#!/usr/bin/env python
"""Manual smoke test for step 1 of the perception pipeline build: point
NimLocateAnything at a handful of real sample photos and see what it finds
and how often it falls back. Hits a real NIM endpoint, so this isn't part
of the pytest suite - run it by hand after standing up the detector.

Requires NIM_ENDPOINT_URL (and NIM_API_KEY if the deployment needs one).

Usage:
    python scripts/smoke_test_detector.py samples/ "blue denim jacket"
    python scripts/smoke_test_detector.py samples/ --manifest samples/manifest.json
"""
from __future__ import annotations

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from PIL import Image, ImageOps, UnidentifiedImageError  # noqa: E402

from perception.detector import NimLocateAnything  # noqa: E402
from perception.preprocess import SOURCE_DETECTOR, preprocess  # noqa: E402

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("samples_dir", help="Directory of sample images (10ish is plenty for a smoke test)")
    parser.add_argument("category_prompt", nargs="?", default=None, help="Prompt used for every image, unless --manifest overrides it")
    parser.add_argument("--manifest", default=None, help="Optional JSON file mapping filename -> category_prompt")
    args = parser.parse_args()

    if not args.category_prompt and not args.manifest:
        parser.error("pass a category_prompt or --manifest")

    manifest = {}
    if args.manifest:
        with open(args.manifest) as f:
            manifest = json.load(f)

    detector = NimLocateAnything()
    filenames = sorted(f for f in os.listdir(args.samples_dir) if os.path.splitext(f)[1].lower() in IMAGE_EXTS)
    if not filenames:
        parser.error(f"no images found in {args.samples_dir}")

    fallback_count = 0
    for filename in filenames:
        prompt = manifest.get(filename, args.category_prompt)
        path = os.path.join(args.samples_dir, filename)
        try:
            image = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
        except (UnidentifiedImageError, OSError) as exc:
            print(f"{filename}: SKIP (unreadable: {exc})")
            continue

        result = preprocess(image, prompt, detector)
        if result.source == SOURCE_DETECTOR:
            print(f"{filename}: prompt={prompt!r} box={result.box} -> crop {result.image.size}")
        else:
            fallback_count += 1
            print(f"{filename}: prompt={prompt!r} -> FALLBACK (full image {result.image.size})")

    print(f"\n{len(filenames)} images, {fallback_count} fell back to full image ({fallback_count / len(filenames):.0%})")


if __name__ == "__main__":
    main()
