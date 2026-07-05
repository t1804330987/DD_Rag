# Group Permission Harness Gate

Status: active

This gate is blocking for the Group Permission business loop.

## Trigger Scope

Run it after changes to files/packages such as:

- `src/main/java/com/dong/ddrag/groupmembership/**`
- Business controllers/services that call group readable/owner permission checks
- Current-user role handling that affects normal business areas

Run it after behavior changes such as:

- Group creation and owner assignment.
- Membership, join request, approval, rejection, or invitation status transitions.
- `OWNER` versus `MEMBER` permissions.
- System `ADMIN` isolation from normal business APIs.
- Group-scoped document or QA readability checks.

## Required Command

```bash
mvn "-Dtest=GroupPermissionHarnessTest" test
```

## Required Cases

- Positive: normal business user creates a group and becomes `OWNER`.
- Positive: readable group member can access group-scoped business data.
- Positive: owner approval of a join request creates `MEMBER` membership and marks the request approved.
- Negative: non-member cannot access group-scoped business data.
- Negative: system `ADMIN` cannot enter normal business flows.

## Side-Effect Assertions

- Group creation inserts both group row and owner membership.
- Admin rejection must not insert group or membership rows.
- Join approval inserts membership and updates request status from `PENDING` to `APPROVED`.
- Read rejection must not grant group-scoped access.

## FAIL/PASS Rule

- PASS: this business loop can continue to human review or commit.
- FAIL: do not commit. Fix business code or explicitly update Harness only when the requirement changes the business rule.
