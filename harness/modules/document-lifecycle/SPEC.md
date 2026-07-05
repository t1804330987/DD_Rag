# Document Lifecycle Harness Spec

## Scope

Protects the document flow from upload initiation through chunk upload, completion, ingestion trigger, parsing, chunking, vector writing, and recovery.

## Core Business Details

- Upload initialization validates filename, size, hash, and group access.
- Fast upload must reuse existing content safely and must not duplicate object writes.
- Chunk upload validates chunk number, chunk size, duplicate chunks, and missing chunks.
- Completion creates a document only when all required chunks are present.
- Document status transitions must remain observable and valid.
- Ingestion parses supported formats, chunks content in order, preserves metadata, and writes vectors.
- Failed parsing or ingestion must not leave half-written vector state.
- Stale processing documents must be recoverable on startup.

## Gate Status

Active. `DocumentLifecycleHarnessTest` is the executable gate for this business loop.
