"""NimLocateAnything is the swappable Detector implementation - these tests
cover its request/response contract in isolation from preprocess()'s
fallback logic, which is exercised separately via a fake Detector."""
from unittest.mock import Mock, patch

import pytest
import requests
from PIL import Image

from perception.detector import BoundingBox, DetectorError, NimLocateAnything


def make_image():
    return Image.new("RGB", (50, 50), (1, 2, 3))


@pytest.fixture
def detector():
    return NimLocateAnything(endpoint_url="https://nim.example.test/locate", api_key="test-key")


def test_missing_endpoint_url_raises():
    with pytest.raises(ValueError):
        NimLocateAnything(endpoint_url="")


def test_parses_detections_into_bounding_boxes(detector):
    mock_resp = Mock()
    mock_resp.json.return_value = {
        "detections": [
            {"bbox": [1, 2, 30, 40], "score": 0.87, "label": "jacket"},
        ]
    }
    mock_resp.raise_for_status.return_value = None

    with patch("perception.detector.requests.post", return_value=mock_resp) as mock_post:
        boxes = detector.locate(make_image(), "denim jacket")

    assert len(boxes) == 1
    assert boxes[0] == BoundingBox(x0=1, y0=2, x1=30, y1=40, score=0.87, label="jacket")
    _, kwargs = mock_post.call_args
    assert kwargs["headers"]["Authorization"] == "Bearer test-key"
    assert kwargs["json"]["prompt"] == "denim jacket"


def test_network_failure_raises_detector_error(detector):
    with patch("perception.detector.requests.post", side_effect=requests.ConnectionError("boom")):
        with pytest.raises(DetectorError):
            detector.locate(make_image(), "denim jacket")


def test_malformed_detection_raises_detector_error(detector):
    mock_resp = Mock()
    mock_resp.json.return_value = {"detections": [{"bbox": [1, 2, 3]}]}  # missing score, short bbox
    mock_resp.raise_for_status.return_value = None

    with patch("perception.detector.requests.post", return_value=mock_resp):
        with pytest.raises(DetectorError):
            detector.locate(make_image(), "denim jacket")
