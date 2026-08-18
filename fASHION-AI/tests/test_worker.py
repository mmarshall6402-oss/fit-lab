"""Batch worker tests: idempotency and pipeline_version tagging are the two
production requirements from the build doc, plus one check that a single
corrupt file can't take the rest of the batch down. Detector and CLIP are
faked so these run fast and offline - NimLocateAnything and the real
sentence-transformers model are covered elsewhere."""
import json

import numpy as np
from PIL import Image

from perception.detector import BoundingBox
from perception.worker import run_batch


class FakeDetector:
    def locate(self, image, category_prompt):
        return [BoundingBox(0, 0, 5, 5, score=0.9)]


class FakeEmbedModel:
    def encode(self, image):
        return np.array([1.0, 2.0, 3.0])


def _write_jpeg(path, color=(1, 2, 3)):
    Image.new("RGB", (20, 20), color).save(path, format="JPEG")


def _run(input_dir, store_path, crops_dir, pipeline_version, manifest=""):
    return run_batch(
        str(input_dir), manifest, str(crops_dir), str(store_path), pipeline_version,
        detector=FakeDetector(), embed_model=FakeEmbedModel(),
    )


def test_worker_is_idempotent(tmp_path):
    input_dir = tmp_path / "raw"
    input_dir.mkdir()
    _write_jpeg(input_dir / "a.jpg")
    store_path = tmp_path / "embeddings.jsonl"
    crops_dir = tmp_path / "crops"

    stats1 = _run(input_dir, store_path, crops_dir, "v2")
    stats2 = _run(input_dir, store_path, crops_dir, "v2")

    assert stats1["processed"] == 1
    assert stats2["processed"] == 0
    assert stats2["skipped"] == 1
    rows = store_path.read_text().strip().splitlines()
    assert len(rows) == 1


def test_new_pipeline_version_appends_without_overwriting_old_rows(tmp_path):
    input_dir = tmp_path / "raw"
    input_dir.mkdir()
    _write_jpeg(input_dir / "a.jpg")
    store_path = tmp_path / "embeddings.jsonl"
    crops_dir = tmp_path / "crops"

    _run(input_dir, store_path, crops_dir, "v1-baseline")
    _run(input_dir, store_path, crops_dir, "v2-grounded-crop")

    rows = [json.loads(line) for line in store_path.read_text().strip().splitlines()]
    assert {row["pipeline_version"] for row in rows} == {"v1-baseline", "v2-grounded-crop"}
    assert len(rows) == 2


def test_corrupt_image_is_skipped_without_failing_the_batch(tmp_path):
    input_dir = tmp_path / "raw"
    input_dir.mkdir()
    (input_dir / "bad.jpg").write_bytes(b"not a real image")
    _write_jpeg(input_dir / "good.jpg")
    store_path = tmp_path / "embeddings.jsonl"
    crops_dir = tmp_path / "crops"

    stats = _run(input_dir, store_path, crops_dir, "v2")

    assert stats["processed"] == 1
    assert stats["failed"] == 1


def test_crop_is_saved_only_for_detector_sourced_results(tmp_path):
    input_dir = tmp_path / "raw"
    input_dir.mkdir()
    _write_jpeg(input_dir / "a.jpg")
    store_path = tmp_path / "embeddings.jsonl"
    crops_dir = tmp_path / "crops"

    _run(input_dir, store_path, crops_dir, "v2")

    assert (crops_dir / "a.jpg").exists()
