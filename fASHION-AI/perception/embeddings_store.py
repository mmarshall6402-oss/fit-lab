"""Append-only embeddings store keyed by (job_id, pipeline_version). Rows
are never overwritten or deleted - re-running the worker with a new
pipeline_version just adds more rows for the same job_id, so a baseline-vs-
v2 accuracy comparison (step 3) is a query over this file, not a migration.
It's also what makes the worker idempotent: `has()` lets the worker skip
any (job_id, pipeline_version) pair it's already written.

A JSON Lines file stands in for the real embeddings table this step 2
doesn't build yet (DynamoDB or RDS, per the architecture doc) - the same
has()/append() contract is what a DB-backed implementation would expose.
"""
from __future__ import annotations

import json
import os
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from typing import List, Optional, Set, Tuple


@dataclass(frozen=True)
class EmbeddingRecord:
    job_id: str
    pipeline_version: str
    embedding: List[float]
    source: str
    detection_score: Optional[float]
    category_prompt: str
    created_at: str


def make_record(
    job_id: str,
    pipeline_version: str,
    embedding: List[float],
    source: str,
    detection_score: Optional[float],
    category_prompt: str,
) -> EmbeddingRecord:
    return EmbeddingRecord(
        job_id=job_id,
        pipeline_version=pipeline_version,
        embedding=embedding,
        source=source,
        detection_score=detection_score,
        category_prompt=category_prompt,
        created_at=datetime.now(timezone.utc).isoformat(),
    )


class EmbeddingsStore:
    def __init__(self, path: str):
        self.path = path
        self._seen: Set[Tuple[str, str]] = self._load_seen_keys()

    def _load_seen_keys(self) -> Set[Tuple[str, str]]:
        if not os.path.exists(self.path):
            return set()
        seen = set()
        with open(self.path) as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                row = json.loads(line)
                seen.add((row["job_id"], row["pipeline_version"]))
        return seen

    def has(self, job_id: str, pipeline_version: str) -> bool:
        return (job_id, pipeline_version) in self._seen

    def append(self, record: EmbeddingRecord) -> None:
        key = (record.job_id, record.pipeline_version)
        if key in self._seen:
            return  # idempotent: this job+version pair is already on disk
        with open(self.path, "a") as f:
            f.write(json.dumps(asdict(record)) + "\n")
        self._seen.add(key)
