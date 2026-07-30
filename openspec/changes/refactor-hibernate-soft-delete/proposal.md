## Why

Current entities use legacy Hibernate annotations (`@SQLDelete` and deprecated `@Where`) for soft deletes. This requires manual SQL strings in annotations and maintenance of custom filtering clauses. Upgrading to Spring Boot 3.2+ allows leveraging Hibernate 6.4+ native `@SoftDelete(columnName = "delete_at", strategy = SoftDeleteType.TIMESTAMP)` annotation, eliminating redundant annotations and simplifying soft delete handling across all entities while automatically keeping deletion timestamps.

## What Changes

- Upgrade Spring Boot version in `gradle.properties` from `3.1.1` to `3.2.12` (or latest 3.2.x), updating Hibernate to 6.4+.
- Refactor entities (`Role`, `Actor`, `Permission`, `Policy`, `OrgSdtNode`, `AuthPwd`) to replace `@SQLDelete` and `@Where` annotations with Hibernate 6.4 native `@SoftDelete(columnName = "delete_at", strategy = SoftDeleteType.TIMESTAMP)`.
- Retain physical deletion on many-to-many relationship join tables (`t_role_actor_map`, `t_role_permission_map`) to prevent stale join records.

## Capabilities

### New Capabilities

- `hibernate-soft-delete`: Provides standard Hibernate 6.4+ native soft delete behavior using timestamp strategy across domain entities.

### Modified Capabilities

(None)

## Impact

- **Dependencies**: `gradle.properties` (upgrade `springBootVersion` to 3.2.x).
- **Domain Entities**: `Role`, `Actor`, `Permission`, `Policy`, `OrgSdtNode`, `AuthPwd`.
- **Database Schema**: Existing `delete_at` column is retained and used by Hibernate `@SoftDelete`.
