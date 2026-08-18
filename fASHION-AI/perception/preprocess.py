"""preprocess() is the single entry point the worker calls per image, and
it must never raise: detection runs offline in the worker, but a garment
still has to end up embedded either way, so any failure in detection
(nothing found, low confidence, a DetectorError) degrades to embedding the
full original photo instead of failing the job. Every result is tagged
with `source` so the fallback rate is queryable later, not just inferred.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Optional

from PIL import Image

from . import config
from .detector import BoundingBox, Detector, DetectorError

logger = logging.getLogger(__name__)

SOURCE_DETECTOR = "detector"
SOURCE_FALLBACK = "fallback_full"


@dataclass(frozen=True)
class PreprocessResult:
    image: Image.Image
    source: str
    box: Optional[BoundingBox]


def _crop(image: Image.Image, box: BoundingBox) -> Image.Image:
    w, h = image.size
    x0, y0 = max(0, int(box.x0)), max(0, int(box.y0))
    x1, y1 = min(w, int(box.x1)), min(h, int(box.y1))
    if x1 <= x0 or y1 <= y0:
        raise DetectorError(f"degenerate box after clamping to image bounds: {box}")
    return image.crop((x0, y0, x1, y1))


def preprocess(
    image: Image.Image,
    category_prompt: str,
    detector: Detector,
    min_conf: Optional[float] = None,
) -> PreprocessResult:
    min_conf = config.MIN_DETECTION_CONFIDENCE if min_conf is None else min_conf

    if not category_prompt or not category_prompt.strip():
        logger.info("preprocess: empty category_prompt, skipping detection")
        return PreprocessResult(image=image, source=SOURCE_FALLBACK, box=None)

    try:
        boxes = detector.locate(image, category_prompt)
    except DetectorError as exc:
        logger.warning("preprocess: detector failed (%s), falling back to full image", exc)
        return PreprocessResult(image=image, source=SOURCE_FALLBACK, box=None)

    if not boxes:
        logger.info("preprocess: no boxes found for %r, falling back to full image", category_prompt)
        return PreprocessResult(image=image, source=SOURCE_FALLBACK, box=None)

    best = max(boxes, key=lambda b: b.score)
    if best.score < min_conf:
        logger.info(
            "preprocess: best box score %.3f below min_conf %.3f, falling back",
            best.score, min_conf,
        )
        return PreprocessResult(image=image, source=SOURCE_FALLBACK, box=None)

    try:
        cropped = _crop(image, best)
    except DetectorError as exc:
        logger.warning("preprocess: crop failed (%s), falling back to full image", exc)
        return PreprocessResult(image=image, source=SOURCE_FALLBACK, box=None)

    return PreprocessResult(image=cropped, source=SOURCE_DETECTOR, box=best)
