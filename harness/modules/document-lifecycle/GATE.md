# Document Lifecycle Harness Gate

Status: active

This gate is blocking for the Document Lifecycle business loop.

## Trigger Scope

Run it after changes to files/packages such as:

- `src/main/java/com/dong/ddrag/document/**`
- `src/main/java/com/dong/ddrag/ingestion/**`
- File storage, chunk upload, parser, chunking, vector writing, or recovery services
- Document status/model/DTO fields

Run it after behavior changes such as:

- Upload initialization, fast upload, chunk upload, or upload completion.
- Missing, duplicate, or invalid chunk handling.
- Document status transitions and stale-processing recovery.
- Parse/chunk/vector side effects during ingestion.
- Group permission checks around document visibility.

## Required Command

```bash
mvn "-Dtest=DocumentLifecycleHarnessTest" test
```

## Required Cases

- Positive: all chunks are present, completion creates the document and triggers expected downstream work.
- Positive: chunk upload stores the chunk object, records chunk metadata, and marks the session uploading.
- Positive: ingestion cleans stale artifacts, processes the document, indexes ready chunks, and marks the document ready.
- Negative: missing chunks reject completion and create no document side effects.
- Negative: failed ingestion recovery deletes partial artifacts and marks the document failed.

## Side-Effect Assertions

- Completion must mark `COMPLETING`, compose storage chunks, finalize document metadata, then mark `COMPLETED`.
- Missing chunks must not compose object, finalize document, or mark the session completed.
- Ingestion must delete old chunks, vectors, and search index entries before processing.
- Recovery must delete partial artifacts and persist `FAILED` with a failure reason.

## FAIL/PASS Rule

- PASS: this business loop can continue to human review or commit.
- FAIL: do not commit. Fix business code or explicitly update Harness only when the requirement changes the business rule.
