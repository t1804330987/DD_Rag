# Assistant Harness Spec

## Scope

This Harness protects the Assistant business flow. It does not test UI rendering, browser behavior, or real model quality.

## Core Business Details

### Chat Flow

- Save the user message before running memory extraction or Agent execution.
- Call the Assistant Agent only when the current turn should produce a real answer.
- Save the assistant reply after Agent execution or confirmation-question generation.
- Reject invalid tool-mode combinations before invoking downstream services.

### Runtime Memory

- Explicit memory replacement must update runtime memory and keep the current user request as the effective request.
- Ambiguous memory replacement must ask for confirmation and must not call the main Assistant Agent in that turn.
- Confirming a pending replacement must apply the pending value and resume the original user request.
- Rejecting a pending replacement must clear pending state and must not overwrite the existing active memory value.
- Runtime memory context must be injected before compact summary and session memory, so newer explicit preferences override stale summaries.

### Knowledge Base Search

- `KB_SEARCH` requires `groupId`.
- `CHAT` must not accept `groupId`.
- Knowledge-base answers must go through the Agent/Tool path, not a hard-coded service branch.
- Group readability must be checked before querying group-scoped knowledge.

### Streaming

- Streaming must emit a start event before deltas and a done event at the end.
- Confirmation-question turns must emit the confirmation answer and must not call the Agent.
- The saved assistant message must match the final stream result.

## Positive Cases

- Normal chat saves user message, calls Agent once, saves assistant reply, and returns the reply.
- Explicit runtime memory change updates active memory and continues the current request.
- Pending runtime memory confirmation applies the pending value and resumes the original request.
- Runtime memory block is injected before stale summary memory.

## Negative Cases

- Ambiguous runtime memory change asks for confirmation and does not call Agent.
- Runtime memory rejection clears pending state and keeps the old active value.
- `CHAT` with `groupId` is rejected.
- `KB_SEARCH` without `groupId` is rejected.

## Side-Effect Invariants

- Confirmation replies are saved as assistant messages.
- The Agent is not called on confirmation-question turns.
- Runtime state changes use the existing context version update path.
- Invalid or cancelled memory changes must not pollute active runtime memory.
