# Assistant Runtime Memory Harness Gate

Status: active for the Assistant Runtime Memory business loop only.

Other Assistant flows in `SPEC.md` are still draft unless a matching executable Harness test exists.

## Trigger Scope

Run this gate when a change touches files/packages such as:

- `src/main/java/com/dong/ddrag/assistant/service/AssistantService.java`
- `src/main/java/com/dong/ddrag/assistant/memory/**`
- `src/main/java/com/dong/ddrag/assistant/agent/**` when Agent request context changes
- `src/main/java/com/dong/ddrag/assistant/model/**` when runtime-memory state, request, or response fields change
- `src/main/java/com/dong/ddrag/assistant/service/AssistantModelSelectionService.java`
- `src/main/java/com/dong/ddrag/modelplatform/**` when Assistant model admission, instruction injection, or invocation governance changes

Run this gate when a change affects behavior such as:

- Runtime memory extraction, replacement, confirmation, or rejection.
- The effective user request after a memory confirmation.
- Whether the main Assistant Agent is called during confirmation-question turns.
- The order of runtime memory, compact summary, and session memory injected into model context.
- Persistence of runtime memory state through the session context update path.
- Compatibility between model selection/instruction injection and the Assistant runtime-memory or `KB_SEARCH` path.

## Required Command

```bash
mvn "-Dtest=AssistantRuntimeMemoryHarnessTest" test
```

## Required Cases

- Positive: explicit replacement updates active runtime memory and keeps the current request.
- Positive: confirmation applies pending replacement and resumes the original request.
- Positive: runtime memory is injected before stale compact summary memory.
- Negative: ambiguous replacement asks for confirmation and does not call Agent.
- Negative: rejection clears pending state without overwriting active memory.

## Side-Effect Assertions

- Confirmation-question turns must not call the main Agent.
- Rejection must not call the runtime memory extractor.
- Invalid or cancelled memory changes must not pollute active runtime memory.
- Runtime memory state must be saved through versioned session-context update.

## Recommended Focused Regression

```bash
mvn "-Dtest=AssistantRuntimeMemoryHarnessTest,AssistantRuntimeMemoryServiceTest,AssistantServiceTest,AssistantShortTermMemoryHookTest" test
```

This regression is broader than the active Harness gate. Do not report it as additional Harness coverage.

## FAIL/PASS Rule

- PASS: this business loop can continue to human review or commit.
- FAIL: do not commit. Fix the business code or explicitly update the Harness only when the requirement changes the business rule.
