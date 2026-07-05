# QA Retrieval Harness Gate

Status: active

This gate is blocking for the QA Retrieval business loop.

## Trigger Scope

Run it after changes to files/packages such as:

- `src/main/java/com/dong/ddrag/qa/**`
- Retrieval, query planning, citation, answer parsing, or evidence assembly services
- Group/document readability logic used by QA
- Assistant `KB_SEARCH` tool behavior when it affects QA retrieval

Run it after behavior changes such as:

- Group-scoped retrieval filters.
- Ready-document or ready-chunk filtering.
- Vector/keyword hybrid merge order.
- Query planning fallback behavior.
- Citation construction and invalid model-output parsing.

## Required Command

```bash
mvn "-Dtest=QaRetrievalHarnessTest" test
```

## Required Cases

- Positive: readable group member retrieves only ready evidence and receives grounded citations.
- Negative: empty evidence returns an unanswered response and does not call the model.
- Negative: non-member cannot retrieve group-scoped evidence.
- Negative: missing structured model output is rejected without invented citations.

## Side-Effect Assertions

- Permission check must happen before retrieval/model calls.
- Non-member rejection must not call retriever or model.
- Empty evidence must not call the model.
- Citations must come from retrieved evidence metadata.
- Invalid model output must return `ANSWER_FORMAT_ERROR` with no citations.

## FAIL/PASS Rule

- PASS: this business loop can continue to human review or commit.
- FAIL: do not commit. Fix business code or explicitly update Harness only when the requirement changes the business rule.
