# Database Migration Harness Spec

## Scope

Protects shared database schema migration. This is a global infrastructure gate, not a user-facing business module.

## Core Business Details

- Flyway must migrate an empty database to the latest version.
- Core tables, columns, indexes, and check constraints must exist.
- Enum-like constraints must reject invalid values.
- Required seed data must exist when migrations define it.
- Migration versions must not be rewritten or conflict with already-applied versions.

## Gate Status

Draft. `FlywayMigrationTest` exists, but this global gate should be activated only when the required PostgreSQL test environment is explicitly available.
