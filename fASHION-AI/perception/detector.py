"""Grounded-detection abstraction for locating a garment in a raw photo
before it gets embedded. `Detector` is the swappable seam: NimLocateAnything
is the implementation for now, but LocateAnything-3B is NVIDIA's
non-commercial license (fine for a portfolio project, not once FitLab
charges money) - swapping in GroundingDINO or YOLO-World later means
writing a new class against this same Protocol, nothing downstream changes.
"""
from __future__ import annotations

import base64
import io
from dataclasses import dataclass
from typing import List, Optional, Protocol

import requests
from PIL import Image

from . import config


@dataclass(frozen=True)
class BoundingBox:
    x0: float
    y0: float
    x1: float
    y1: float
    score: float
    label: str = ""


class Detector(Protocol):
    def locate(self, image: Image.Image, category_prompt: str) -> List[BoundingBox]:
        """Every box found for category_prompt in image, in no particular
        order - callers are responsible for picking the best one. Empty
        list means nothing matched, not an error."""
        ...


class DetectorError(Exception):
    """Raised by a Detector when it can't produce a result: a network
    failure, a corrupt/unreadable image the remote service rejects, or a
    response that doesn't parse. preprocess() catches this and falls back
    to the full image - a DetectorError must never propagate up to the
    upload path."""


class NimLocateAnything:
    """Detector backed by NVIDIA's LocateAnything-3B NIM microservice: an
    open-vocabulary grounded detector that takes a free-text category
    prompt ("blue denim jacket") instead of a fixed label set.

    NIM containers expose a REST inference endpoint whose exact schema is
    fixed by the deployed container image - the request/response shape
    below is this client's contract; verify it against the running
    deployment's own OpenAPI docs before pointing this at a real endpoint.
    """

    def __init__(
        self,
        endpoint_url: Optional[str] = None,
        api_key: Optional[str] = None,
        timeout: Optional[float] = None,
    ):
        self.endpoint_url = endpoint_url or config.NIM_ENDPOINT_URL
        self.api_key = api_key or config.NIM_API_KEY
        self.timeout = timeout if timeout is not None else config.NIM_TIMEOUT_SECONDS
        if not self.endpoint_url:
            raise ValueError("NIM_ENDPOINT_URL is not configured")

    def locate(self, image: Image.Image, category_prompt: str) -> List[BoundingBox]:
        buf = io.BytesIO()
        image.save(buf, format="JPEG")
        payload = {
            "image": base64.b64encode(buf.getvalue()).decode("ascii"),
            "prompt": category_prompt,
        }
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        try:
            resp = requests.post(self.endpoint_url, json=payload, headers=headers, timeout=self.timeout)
            resp.raise_for_status()
            data = resp.json()
        except (requests.RequestException, ValueError) as exc:
            raise DetectorError(f"NIM request failed: {exc}") from exc

        boxes = []
        for det in data.get("detections", []):
            try:
                x0, y0, x1, y1 = det["bbox"]
                boxes.append(
                    BoundingBox(
                        x0=float(x0), y0=float(y0), x1=float(x1), y1=float(y1),
                        score=float(det["score"]), label=det.get("label", ""),
                    )
                )
            except (KeyError, TypeError, ValueError) as exc:
                raise DetectorError(f"Malformed detection in NIM response: {det!r}") from exc
        return boxes
