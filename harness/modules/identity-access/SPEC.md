# Identity Access Harness Spec

## Scope

Protects authentication, account operations, administrator user management, JWT parsing, and refresh-token behavior.

## Core Business Details

- Registration creates an active `USER` with hashed password and no active refresh token.
- Login succeeds only for valid credentials and active users.
- Login issues access and refresh tokens tied to the authenticated user.
- Refresh rotates tokens and invalidates the previous refresh token.
- Password change requires the old password and invalidates old credentials.
- Admin-only user management must reject normal users.
- Disabled users must not login.
- Business APIs must not accept legacy test-user headers as authentication.

## Gate Status

Active. `IdentityAccessHarnessTest` is the executable gate for this business loop.
