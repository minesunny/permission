## Context

The permission service entities (`Role`, `Actor`, `Permission`, `Policy`, `OrgSdtNode`, `AuthPwd`) currently implement soft deletion using Hibernate's `@SQLDelete` and `@Where` annotations. With Spring Boot 3.1.1 upgrading to Spring Boot 3.2.x+ (which uses Hibernate 6.4+), Hibernate native `@SoftDelete` becomes available.

## Goals / Non-Goals

**Goals:**
- Upgrade Spring Boot version in `gradle.properties` from `3.1.1` to `3.2.12`.
- Refactor all soft-deleted entities to use `@SoftDelete(columnName = "delete_at", strategy = SoftDeleteType.TIMESTAMP)`.
- Remove manual `@SQLDelete` SQL strings and deprecated `@Where` annotations across all entity classes.
- Maintain existing database schema (`delete_at` column) without requiring database migrations.
- Keep physical DELETE behavior for join tables (`t_role_actor_map`, `t_role_permission_map`).

**Non-Goals:**
- Changing database column names (retain `delete_at`).
- Soft deleting join table relationship records.

## Decisions

### 1. SoftDelete Strategy Choice: `SoftDeleteType.TIMESTAMP` vs `SoftDeleteType.DELETED`
- **Decision**: Use `SoftDeleteType.TIMESTAMP` with `columnName = "delete_at"`.
- **Rationale**: `SoftDeleteType.DELETED` only updates a boolean flag (`true`/`false`), whereas `TIMESTAMP` sets `delete_at = CURRENT_TIMESTAMP` upon deletion and filters queries with `delete_at IS NULL`. This preserves auditability and retains historical deletion timestamps while leveraging native Hibernate support.

### 2. Removal of `@SQLDelete` and `@Where` / `@SQLRestriction`
- **Decision**: Completely strip `@SQLDelete` and `@Where` / `@SQLRestriction` from entities annotated with `@SoftDelete`.
- **Rationale**: Hibernate `@SoftDelete` automatically intercepts delete statements (converting `DELETE` to `UPDATE ... SET delete_at = NOW()`) and automatically appends `AND delete_at IS NULL` to HQL/JPQL/Criteria queries.

## Risks / Trade-offs

- **[Risk] Spring Boot 3.2 Upgrade Breaking Changes** → Test all JPA repositories and manager integration tests to ensure compatibility after upgrading Spring Boot to 3.2.x.
- **[Risk] Hibernate 6.4 Query Behavior** → Verify custom JPQL or native SQL queries in repositories function correctly with `@SoftDelete`.
