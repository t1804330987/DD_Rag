# QA Retrieval Harness Spec

## Scope

Protects group-scoped retrieval, query planning, evidence assembly, answer parsing, and citation correctness.

## Core Business Details

- QA must only retrieve documents readable by the current group member.
- Retrieval must use ready chunks only.
- Query planning must degrade safely when planning output is unavailable or invalid.
- Hybrid retrieval must merge vector and keyword results deterministically.
- Answer parsing must handle invalid model output explicitly.
- Citations must be assembled from retrieved evidence, not invented answer text.

## Gate Status

Active. `QaRetrievalHarnessTest` is the executable gate for this business loop.
