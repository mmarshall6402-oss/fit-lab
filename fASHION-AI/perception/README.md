# Perception pipeline

Grounded-detection preprocessing: crop the garment out of a raw photo
*before* it gets CLIP-embedded, instead of embedding the whole scene. This
covers steps 1-2 of the build (`Detector` + batch worker) - see the
architecture doc for the full plan, including the SQS-triggered worker,
Terraform, and CloudWatch dashboard steps 3-6 add on top of this.

Detection runs **offline only**, in the batch worker below - never in a
request path.

## Layout

- `detector.py` - the `Detector` protocol and `NimLocateAnything`, the
  NVIDIA LocateAnything-3B NIM-backed implementation. Swap in a different
  `Detector` implementation (GroundingDINO, YOLO-World) without touching
  anything downstream - required before FitLab ever charges money, since
  LocateAnything-3B's license is non-commercial.
- `preprocess.py` - `preprocess(image, category_prompt, detector)`. Never
  raises: no box found, low confidence, or a detector failure all fall back
  to embedding the full original image, tagged `source=fallback_full`.
- `embeddings_store.py` - append-only JSONL store keyed by
  `(job_id, pipeline_version)`. Old rows are never overwritten, so
  baseline-vs-v2 accuracy (step 3) is a query, not a migration.
- `worker.py` - local batch worker. Point it at a directory of raw images
  and it crops, embeds, and writes a `pipeline_version`-tagged row per
  image. Idempotent: re-running on the same input directory skips any
  `(job_id, pipeline_version)` pair already in the store.
- `config.py` - every threshold, endpoint, and path, read from the
  environment.

## Config (env vars)

| Var | Default | Purpose |
|---|---|---|
| `NIM_ENDPOINT_URL` | *(required)* | NIM LocateAnything-3B inference endpoint |
| `NIM_API_KEY` | *(none)* | Bearer token for the NIM endpoint, if it requires one |
| `NIM_TIMEOUT_SECONDS` | `10.0` | Request timeout for the NIM call |
| `MIN_DETECTION_CONFIDENCE` | `0.35` | Boxes below this score are treated as no detection |
| `PIPELINE_VERSION` | `v2-grounded-crop` | Tag written on every embedding row |
| `EMBEDDINGS_STORE_PATH` | `embeddings_store.jsonl` | Where the worker appends rows |
| `WORKER_INPUT_DIR` | `raw` | Directory of raw images to process |
| `WORKER_CROPS_DIR` | `crops` | Where detector-sourced crops are saved |
| `WORKER_MANIFEST_FILE` | *(none)* | Optional JSON `{filename: category_prompt}` |
| `DEFAULT_CATEGORY_PROMPT` | `garment` | Prompt used for any file not in the manifest |

## Running

```bash
# smoke test step 1: hit a real NIM endpoint with ~10 sample photos
python scripts/smoke_test_detector.py samples/ "blue denim jacket"

# batch worker step 2: process a directory of raw images
python -m perception.worker --input-dir raw --manifest raw/manifest.json
```

## Tests

```bash
pytest tests/test_detector.py tests/test_nim_locate_anything.py \
       tests/test_embeddings_store.py tests/test_worker.py
```

`test_detector.py` and `test_worker.py` use fakes for `Detector` and the
CLIP model, so they run fast and offline. `test_nim_locate_anything.py`
covers the NIM request/response contract with mocked HTTP calls.
