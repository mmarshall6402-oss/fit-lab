"""
Tests for the bank-management logic that doesn't need the segmentation
model to exercise: category normalization, filename-to-description
cleanup, and near-duplicate detection.
"""
import numpy as np
import pytest
from PIL import Image

import main


# --- normalize_category ---------------------------------------------------

@pytest.mark.parametrize("raw,expected", [
    ("sneakers", "shoes"),
    ("Sneakers", "shoes"),
    ("KICKS", "shoes"),
    ("footwear", "shoes"),
    ("trainers", "shoes"),
    ("jeans", "pants"),
    ("Trousers", "pants"),
    ("shorts", "pants"),
    ("bottoms", "pants"),
    ("bottom", "pants"),
])
def test_normalize_category_aliases(raw, expected):
    assert main.normalize_category(raw) == expected


def test_normalize_category_passes_through_unknown_categories():
    # "outerwear", "top", or any custom folder name with no known alias
    # should be left as-is (just whitespace-trimmed), not silently mapped
    # to something else.
    assert main.normalize_category("outerwear") == "outerwear"
    assert main.normalize_category("  top  ") == "top"


# --- describe_from_filename -------------------------------------------------

def test_describe_from_filename_strips_extension_and_separators():
    assert main.describe_from_filename("jordan4_black_cat.jpg") == "jordan4 black cat"
    assert main.describe_from_filename("air-jordan-1-og.PNG") == "air jordan 1 og"


def test_describe_from_filename_collapses_extra_whitespace():
    assert main.describe_from_filename("a   b__c.jpg") == "a b c"


# --- crop_to_garment fallback path (no segmentation call needed) -----------

def test_crop_to_garment_returns_original_for_unmapped_category():
    img = Image.new("RGB", (40, 40), (10, 20, 30))
    result = main.crop_to_garment(img, "outerwear")
    assert result is img  # untouched, not even a copy - no model call made


def test_crop_to_garment_is_case_insensitive():
    img = Image.new("RGB", (10, 10), (0, 0, 0))
    assert main.crop_to_garment(img, "OUTERWEAR") is img


# --- _find_duplicate_item ---------------------------------------------------

def _item(id, category, embedding):
    return {"id": id, "category": category, "embedding": embedding}


def test_find_duplicate_item_matches_near_identical_embedding():
    existing = np.random.RandomState(0).rand(512).tolist()
    items = [_item(1, "shoes", existing)]

    # Same vector, trivially perturbed - should still clear the 0.999
    # cosine-similarity threshold.
    near_duplicate = (np.array(existing) * 1.0001).tolist()
    dup = main._find_duplicate_item(items, "shoes", near_duplicate)
    assert dup is not None
    assert dup["id"] == 1


def test_find_duplicate_item_ignores_different_category():
    existing = np.random.RandomState(1).rand(512).tolist()
    items = [_item(1, "pants", existing)]
    # Identical embedding, but filed under "shoes" - pants and shoes are
    # intentionally separate matchable pools, so this must NOT merge.
    dup = main._find_duplicate_item(items, "shoes", existing)
    assert dup is None


def test_find_duplicate_item_rejects_genuinely_different_embedding():
    rng = np.random.RandomState(2)
    items = [_item(1, "shoes", rng.rand(512).tolist())]
    unrelated = rng.rand(512).tolist()
    dup = main._find_duplicate_item(items, "shoes", unrelated)
    assert dup is None


def test_find_duplicate_item_skips_items_missing_embedding():
    items = [{"id": 1, "category": "shoes"}]  # no "embedding" key at all
    dup = main._find_duplicate_item(items, "shoes", [0.1] * 512)
    assert dup is None
