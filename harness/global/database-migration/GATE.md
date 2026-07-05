# Database Migration Harness Gate

Status: draft

This is not a universal blocking gate until the PostgreSQL test environment is documented and consistently available.

## Trigger Scope

When this gate becomes active, run it after changes to files/packages such as:

- `src/main/resources/db/migration/**`
- Entity fields, enum-like constraints, indexes, or required seed data that must match migration state
- Database initialization or Flyway configuration

Run it after behavior changes such as:

- Adding, removing, renaming, or rewriting migration files.
- Changing schema constraints that protect business invariants.
- Changing required seed data or migration ordering.

## Target Command

```bash
mvn "-Dtest=FlywayMigrationTest" test
```

## Required Future Cases

- Positive: empty test database migrates to latest version.
- Positive: required tables, columns, indexes, constraints, and seed data exist.
- Negative: invalid enum-like values are rejected by constraints.
- Negative: rewritten or conflicting migration versions are detected before delivery.

## Draft Rule

Do not use this as a universal blocking gate until the environment requirement is documented and consistently available.
