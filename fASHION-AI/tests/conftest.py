"""
Shared fixtures for the fASHION-AI test suite.

main.py loads CLIP + the segmentation model at import time (module-level
`model = SentenceTransformer(...)` / `_seg_model = ...`), and reads
FASHION_AI_API_KEY at import time too - so both have to be in place
*before* main is ever imported, which is why that happens here in
conftest rather than in a fixture. That import cost (~15-20s) is paid
once for the whole test session, not per test.

Every test that touches the item bank uses the isolated_bank fixture
instead of the real item_bank.json - main.py has no test-mode switch of
its own, so this monkeypatches the module-level file path constants it
reads from, keeping tests from reading or overwriting real data.
"""
import io
import os
import zipfile

import pytest
from PIL import Image

os.environ.setdefault("FASHION_AI_API_KEY", "test-key-for-pytest")

import main  # noqa: E402  (must come after the env var is set)
from fastapi.testclient import TestClient  # noqa: E402


API_KEY = os.environ["FASHION_AI_API_KEY"]


@pytest.fixture
def client():
    return TestClient(main.app)


@pytest.fixture
def auth_headers():
    return {"X-API-Key": API_KEY}


@pytest.fixture
def isolated_bank(tmp_path, monkeypatch):
    """Points every file main.py reads/writes for the item bank and the
    fit-training brain at a throwaway temp directory, so tests can upload,
    rate, and patch items without ever touching real data on disk."""
    monkeypatch.setattr(main, "ITEM_BANK_FILE", str(tmp_path / "item_bank.json"))
    monkeypatch.setattr(main, "FIT_DATA_FILE", str(tmp_path / "fit_training_data.pkl"))
    monkeypatch.setattr(main, "FIT_MODEL_FILE", str(tmp_path / "fit_model.pkl"))
    monkeypatch.setattr(main, "FIT_HISTORY_FILE", str(tmp_path / "fit_training_history.jsonl"))
    monkeypatch.setattr(main, "FIT_BACKUP_DIR", str(tmp_path / "fit_model_backups"))
    return tmp_path


def make_test_jpeg_bytes(color=(200, 30, 30), size=(64, 64)) -> bytes:
    """A tiny solid-color JPEG - fine for exercising upload/store/dedup
    plumbing. Not a real garment photo, so segmentation won't find a
    person to parse; crop_to_garment correctly falls back to using it
    as-is, which keeps these tests from paying the ~7.5s/image
    segmentation cost for logic that isn't about segmentation at all."""
    img = Image.new("RGB", size, color)
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def make_test_zip(entries: dict) -> bytes:
    """entries: {"category/filename.jpg": jpeg_bytes, ...}"""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        for path, data in entries.items():
            zf.writestr(path, data)
    return buf.getvalue()
