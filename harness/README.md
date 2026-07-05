# Harness

Harness records executable quality gates for core business loops. It is designed for AI-assisted development: after changing business code, the agent reads the matching `GATE.md`, runs the listed fast Harness command, and treats a failing gate as a blocker before delivery.

## Core Rule

`GATE.md` and `SPEC.md` are workflow context for humans and AI. They are not gates by themselves.

A blocking Harness gate exists only when all of these are true:

- The business loop has `SPEC.md` and `GATE.md`.
- There is an executable `*HarnessTest.java` under `src/test/java`.
- The test contains positive cases, negative cases, and side-effect assertions.
- The documented command has been run and verified.

If a module only has Markdown, it is a draft spec and cannot block delivery.

## AI Development Flow

```text
Change business code
-> Compare git diff with each GATE.md trigger scope
-> Run every matched *HarnessTest command
-> PASS: continue to human review or commit
-> FAIL: do not commit; fix business code or explicitly update Harness for a changed requirement
```

When a requirement intentionally changes a business rule, update the Harness expectations in the same change and explain both the rule change and assertion change in the delivery or commit note. Do not loosen Harness assertions only to make a failing gate pass.

## Layout

```text
harness/
  modules/
    identity-access/
    group-permission/
    document-lifecycle/
    qa-retrieval/
    assistant/
  global/
    database-migration/
```

- `modules/`: user-facing core business loops.
- `global/`: shared infrastructure gates that business loops depend on.
- `SPEC.md`: business rules, positive paths, negative paths, and side-effect invariants.
- `GATE.md`: trigger scope, executable command, expected cases, and FAIL/PASS handling.

Executable tests stay under `src/test/java` so the project can use the existing Maven/JUnit runner without extra build wiring.

## Gate Status

- `active`: executable Harness test exists, the command has been verified, and the active scope is named precisely.
- `draft`: the business contract is identified, but the executable Harness test is missing or not yet verified.

Current status:

| Business loop | Status | Executable gate |
| --- | --- | --- |
| Assistant Runtime Memory | active | `AssistantRuntimeMemoryHarnessTest` |
| Identity Access | active | `IdentityAccessHarnessTest` |
| Group Permission | active | `GroupPermissionHarnessTest` |
| Document Lifecycle | active | `DocumentLifecycleHarnessTest` |
| QA Retrieval | active | `QaRetrievalHarnessTest` |
| Database Migration | draft | target: `FlywayMigrationTest`, requires documented PostgreSQL test environment |

Run all active business-loop gates:

```bash
mvn "-Dtest=AssistantRuntimeMemoryHarnessTest,IdentityAccessHarnessTest,GroupPermissionHarnessTest,DocumentLifecycleHarnessTest,QaRetrievalHarnessTest" test
```

Run only matched gates when a change clearly touches a subset. If a diff crosses module boundaries, combine every matched gate in one Maven command.

## Harness Test Shape

Harness tests should be fast backend tests:

- Do not start frontend UI or use screenshots.
- Do not require Docker or a real database unless the gate explicitly documents that environment.
- Prefer mocks for repositories, external services, model calls, and infrastructure.
- Call the core service or use-case entry directly.
- Assert the main return value and all required side effects.
- Assert negative paths reject the operation and produce no forbidden side effects.
