"""Unit tests for preprocess() - the fallback logic is the whole point of
this module (upload must never fail), so every branch that leads to a
fallback gets its own test, plus the one that doesn't."""
from PIL import Image

from perception.detector import BoundingBox, DetectorError
from perception.preprocess import SOURCE_DETECTOR, SOURCE_FALLBACK, preprocess


def make_image(size=(100, 100)):
    return Image.new("RGB", size, (10, 20, 30))


class FakeDetector:
    def __init__(self, boxes=None, raises=None):
        self._boxes = boxes or []
        self._raises = raises

    def locate(self, image, category_prompt):
        if self._raises:
            raise self._raises
        return self._boxes


def test_no_detection_falls_back_to_full_image():
    image = make_image()
    result = preprocess(image, "red shirt", FakeDetector(boxes=[]))
    assert result.source == SOURCE_FALLBACK
    assert result.box is None
    assert result.image is image


def test_low_confidence_falls_back():
    boxes = [BoundingBox(10, 10, 50, 50, score=0.1)]
    result = preprocess(make_image(), "red shirt", FakeDetector(boxes=boxes), min_conf=0.35)
    assert result.source == SOURCE_FALLBACK
    assert result.box is None


def test_detector_error_falls_back_instead_of_raising():
    """Covers a corrupt/unreadable image as far as the detector is
    concerned: the remote service rejects it and raises DetectorError -
    preprocess must swallow that and fall back, never propagate it."""
    result = preprocess(make_image(), "red shirt", FakeDetector(raises=DetectorError("corrupt image")))
    assert result.source == SOURCE_FALLBACK
    assert result.box is None


def test_multiple_boxes_picks_highest_score():
    boxes = [
        BoundingBox(0, 0, 20, 20, score=0.4, label="low"),
        BoundingBox(10, 10, 60, 60, score=0.9, label="high"),
        BoundingBox(5, 5, 30, 30, score=0.6, label="mid"),
    ]
    result = preprocess(make_image(), "red shirt", FakeDetector(boxes=boxes), min_conf=0.35)
    assert result.source == SOURCE_DETECTOR
    assert result.box.label == "high"
    assert result.image.size == (50, 50)


def test_empty_category_prompt_skips_detection_and_falls_back():
    detector = FakeDetector(boxes=[BoundingBox(0, 0, 10, 10, score=0.99)])
    result = preprocess(make_image(), "   ", detector)
    assert result.source == SOURCE_FALLBACK
    assert result.box is None
