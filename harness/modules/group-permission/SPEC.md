# Group Permission Harness Spec

## Scope

Protects group ownership, membership, join requests, and system-role versus group-role isolation.

## Core Business Details

- System `ADMIN` must not enter normal business areas.
- Normal `USER` can create a group and becomes `OWNER`.
- `OWNER` and `MEMBER` have different permissions.
- Non-members cannot read group-scoped documents or QA data.
- Join requests must handle create, duplicate, approve, reject, and status transitions.
- Only group owners can manage members and invitations.

## Gate Status

Active. `GroupPermissionHarnessTest` is the executable gate for this business loop.
