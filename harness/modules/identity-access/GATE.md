# Identity Access Harness Gate

Status: active

This gate is blocking for the Identity Access business loop.

## Trigger Scope

Run it after changes to files/packages such as:

- `src/main/java/com/dong/ddrag/identity/**`
- `src/main/java/com/dong/ddrag/auth/**`
- Authentication, JWT, refresh-token, password, or current-user DTO/model fields

Run it after behavior changes such as:

- Registration, login, logout, token refresh, or password change.
- Account disabled/active status handling.
- System role checks for `ADMIN` and `USER`.
- Current-user resolution used by business APIs.

## Required Command

```bash
mvn "-Dtest=IdentityAccessHarnessTest" test
```

## Required Cases

- Positive: valid active user logs in and receives access/refresh tokens.
- Positive: refresh rotates the token and invalidates the previous token.
- Positive: normal `USER` can pass business-user requirement.
- Negative: disabled user cannot login and produces no token/login side effects.
- Negative: system `ADMIN` cannot pass business-user requirement.

## Side-Effect Assertions

- Login revokes old active refresh tokens before issuing a new refresh token.
- Login updates `last_login_at`.
- Disabled login must not issue tokens or update login time.
- Refresh revokes the old refresh token before issuing the replacement.

## FAIL/PASS Rule

- PASS: this business loop can continue to human review or commit.
- FAIL: do not commit. Fix business code or explicitly update Harness only when the requirement changes the business rule.
