"""Local batch worker - step 2 of the perception pipeline build. Stands in
for the Mac Pro / SQS-triggered worker in the architecture doc, minus the
AWS plumbing (S3 raw/crops buckets, SQS, a real jobs table) that later
steps add: point it at a local directory of raw images and it does the
same per-image work the production worker will - crop via preprocess(),
embed with CLIP, write a pipeline_version-tagged row - against the local
filesystem instead of S3.

Safe to re-run on the same input directory: EmbeddingsStore skips any
(job_id, pipeline_version) pair it's already written, so a repeated or
partially-failed run never duplicates embeddings.
"""
from __future__ import annotations

import argparse
import json
import logging
import os
from typing import Dict, Optional

from PIL import Image, ImageOps, UnidentifiedImageError
from sentence_transformers import SentenceTransformer

from . import config
from .detector import Detector, NimLocateAnything
from .embeddings_store import EmbeddingsStore, make_record
from .preprocess import SOURCE_DETECTOR, preprocess

logger = logging.getLogger(__name__)

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}


def open_image(path: str) -> Image.Image:
    return ImageOps.exif_transpose(Image.open(path)).convert("RGB")


def load_manifest(manifest_path: str) -> Dict[str, str]:
    """filename -> category_prompt, or {} if no manifest was given. Jobs
    without an entry fall back to config.DEFAULT_CATEGORY_PROMPT."""
    if not manifest_path or not os.path.exists(manifest_path):
        return {}
    with open(manifest_path) as f:
        return json.load(f)


def run_job(
    image_path: str,
    job_id: str,
    category_prompt: str,
    detector: Detector,
    embed_model,
    store: EmbeddingsStore,
    crops_dir: Optional[str],
    pipeline_version: str,
) -> str:
    """Processes one image end to end. Returns "skipped" (already written
    for this job+version), "failed" (unreadable image - logged, never
    raised, so one bad file can't take the batch down), or the record's
    source ("detector"/"fallback_full") on success."""
    if store.has(job_id, pipeline_version):
        logger.info("skip %s: already processed at %s", job_id, pipeline_version)
        return "skipped"

    try:
        image = open_image(image_path)
    except (UnidentifiedImageError, OSError) as exc:
        logger.error("skip %s: unreadable image (%s)", job_id, exc)
        return "failed"

    result = preprocess(image, category_prompt, detector)
    embedding = embed_model.encode(result.image).tolist()

    if crops_dir and result.source == SOURCE_DETECTOR:
        os.makedirs(crops_dir, exist_ok=True)
        result.image.save(os.path.join(crops_dir, os.path.basename(image_path)))

    record = make_record(
        job_id=job_id,
        pipeline_version=pipeline_version,
        embedding=embedding,
        source=result.source,
        detection_score=result.box.score if result.box else None,
        category_prompt=category_prompt,
    )
    store.append(record)
    return result.source


def run_batch(
    input_dir: str,
    manifest_path: str,
    crops_dir: str,
    store_path: str,
    pipeline_version: str,
    detector: Optional[Detector] = None,
    embed_model=None,
) -> Dict[str, int]:
    detector = detector or NimLocateAnything()
    embed_model = embed_model or SentenceTransformer("clip-ViT-B-32")
    manifest = load_manifest(manifest_path)
    store = EmbeddingsStore(store_path)

    stats = {"detector": 0, "fallback": 0, "skipped": 0, "failed": 0}
    filenames = sorted(f for f in os.listdir(input_dir) if os.path.splitext(f)[1].lower() in IMAGE_EXTS)
    for filename in filenames:
        category_prompt = manifest.get(filename, config.DEFAULT_CATEGORY_PROMPT)
        outcome = run_job(
            os.path.join(input_dir, filename), filename, category_prompt,
            detector, embed_model, store, crops_dir, pipeline_version,
        )
        stats[outcome] += 1

    stats["processed"] = stats["detector"] + stats["fallback"]
    return stats


def main():
    parser = argparse.ArgumentParser(description="Batch-embed raw catalog images with grounded-detection cropping.")
    parser.add_argument("--input-dir", default=config.WORKER_INPUT_DIR)
    parser.add_argument("--manifest", default=config.WORKER_MANIFEST_FILE, help="JSON file mapping filename -> category_prompt")
    parser.add_argument("--crops-dir", default=config.WORKER_CROPS_DIR)
    parser.add_argument("--store", default=config.EMBEDDINGS_STORE_PATH)
    parser.add_argument("--pipeline-version", default=config.PIPELINE_VERSION)
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    stats = run_batch(args.input_dir, args.manifest, args.crops_dir, args.store, args.pipeline_version)

    fallback_rate = stats["fallback"] / stats["processed"] if stats["processed"] else 0.0
    logger.info(
        "done: %d processed (%d detector, %d fallback), %d skipped, %d failed - fallback rate %.1f%%",
        stats["processed"], stats["detector"], stats["fallback"], stats["skipped"], stats["failed"],
        fallback_rate * 100,
    )
    if fallback_rate > 0.30:
        logger.warning("fallback rate %.1f%% exceeds the 30%% threshold - check category prompts", fallback_rate * 100)


if __name__ == "__main__":
    main()
