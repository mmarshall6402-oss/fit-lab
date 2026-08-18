"""Unit tests for EmbeddingsStore itself, isolated from the worker that
drives it - the append-only + idempotent contract is the requirement here."""
from perception.embeddings_store import EmbeddingsStore, make_record


def _record(job_id="item-1", pipeline_version="v2"):
    return make_record(
        job_id=job_id, pipeline_version=pipeline_version, embedding=[0.1, 0.2],
        source="detector", detection_score=0.9, category_prompt="shirt",
    )


def test_append_then_has_is_true(tmp_path):
    store = EmbeddingsStore(str(tmp_path / "embeddings.jsonl"))
    assert not store.has("item-1", "v2")
    store.append(_record())
    assert store.has("item-1", "v2")


def test_append_is_idempotent_for_same_job_and_version(tmp_path):
    path = tmp_path / "embeddings.jsonl"
    store = EmbeddingsStore(str(path))
    store.append(_record())
    store.append(_record())
    assert len(path.read_text().strip().splitlines()) == 1


def test_reloading_store_picks_up_existing_rows(tmp_path):
    path = tmp_path / "embeddings.jsonl"
    EmbeddingsStore(str(path)).append(_record())
    reopened = EmbeddingsStore(str(path))
    assert reopened.has("item-1", "v2")


def test_different_pipeline_versions_coexist(tmp_path):
    path = tmp_path / "embeddings.jsonl"
    store = EmbeddingsStore(str(path))
    store.append(_record(pipeline_version="v1-baseline"))
    store.append(_record(pipeline_version="v2-grounded-crop"))
    assert store.has("item-1", "v1-baseline")
    assert store.has("item-1", "v2-grounded-crop")
    assert len(path.read_text().strip().splitlines()) == 2
