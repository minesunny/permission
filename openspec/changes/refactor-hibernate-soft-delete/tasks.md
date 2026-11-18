## 1. Upgrade Dependencies

- [x] 1.1 Upgrade `springBootVersion` in `gradle.properties` to `3.2.12` to enable Hibernate 6.4+ `@SoftDelete`.

## 2. Refactor Entity Soft Delete Annotations

- [x] 2.1 Refactor `Role` entity: replace `@SQLDelete` and `@Where` with `@SoftDelete(columnName = "delete_at", strategy = SoftDeleteType.TIMESTAMP)`.
- [x] 2.2 Refactor `Actor` entity: replace `@SQLDelete` and `@Where` with `@SoftDelete(columnName = "delete_at", strategy = SoftDeleteType.TIMESTAMP)`.
- [x] 2.3 Refactor `Permission` entity: replace `@SQLDelete` and `@Where` with `@SoftDelete(columnName = "delete_at", strategy = SoftDeleteType.TIMESTAMP)`.
- [x] 2.4 Refactor `Policy` entity: replace `@SQLDelete` with `@SoftDelete(columnName = "delete_at", strategy = SoftDeleteType.TIMESTAMP)`.
- [x] 2.5 Refactor `OrgSdtNode` entity: replace `@SQLDelete` and `@Where` with `@SoftDelete(columnName = "delete_at", strategy = SoftDeleteType.TIMESTAMP)`.
- [x] 2.6 Refactor `AuthPwd` entity: replace `@SQLDelete` and `@Where` with `@SoftDelete(columnName = "delete_at", strategy = SoftDeleteType.TIMESTAMP)`.

## 3. Testing and Verification

- [x] 3.1 Run `AdminMgrImplTest`, `ReviewMgrImplTest`, and all core unit/integration tests to verify soft delete and query filtering behavior.
