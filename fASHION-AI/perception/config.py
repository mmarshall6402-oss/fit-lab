"""Every knob the perception pipeline needs, read from the environment -
none of it is hardcoded, so the same code runs against a local NIM
container, a staging endpoint, or (once the Terraform module in step 4
lands) values pulled from SSM Parameter Store, without a code change."""
import os


def _float_env(name: str, default: float) -> float:
    raw = os.environ.get(name)
    return float(raw) if raw else default


# Detector (NimLocateAnything)
NIM_ENDPOINT_URL = os.environ.get("NIM_ENDPOINT_URL", "")
NIM_API_KEY = os.environ.get("NIM_API_KEY", "")
NIM_TIMEOUT_SECONDS = _float_env("NIM_TIMEOUT_SECONDS", 10.0)
MIN_DETECTION_CONFIDENCE = _float_env("MIN_DETECTION_CONFIDENCE", 0.35)

# Batch worker
PIPELINE_VERSION = os.environ.get("PIPELINE_VERSION", "v2-grounded-crop")
EMBEDDINGS_STORE_PATH = os.environ.get("EMBEDDINGS_STORE_PATH", "embeddings_store.jsonl")
WORKER_INPUT_DIR = os.environ.get("WORKER_INPUT_DIR", "raw")
WORKER_CROPS_DIR = os.environ.get("WORKER_CROPS_DIR", "crops")
WORKER_MANIFEST_FILE = os.environ.get("WORKER_MANIFEST_FILE", "")
DEFAULT_CATEGORY_PROMPT = os.environ.get("DEFAULT_CATEGORY_PROMPT", "garment")
