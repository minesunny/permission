# Follow-ups — Gradle + natural-key refactor

Branch: `refactor/gradle-natural-key`
Source: final whole-branch review (2026-07-20). These items were intentionally **not** fixed in the refactor branch — they are pre-existing, out-of-scope, or working-but-fragile — and are recorded here so they are not lost.

## Pre-existing correctness bugs (discovered, not introduced)

### F1. `User.managerOrgUnits` `@JoinTable` column names are swapped
- `entity/actor/User.java:84-89` — `joinColumns(name="org_id", referencedColumnName="id")` (owning `User` id → `org_id`) and `inverseJoinColumns(name="user_id", referencedColumnName="id")` (target `OrgUnit` id → `user_id`). Column names are inverted vs their content.
- Symptom: a row written by `OrgUnitMgrImpl.bindManagerUser` (which writes `org_id=orgId, user_id=userId`, correct by NAME) is NOT readable via `user.getManagerOrgUnits()` (Hibernate joins `WHERE org_id = user.id`). The relation has never round-tripped correctly.
- Note: this refactor's `bindManagerUser`/`unbindManagerUser` user_id fix made the WRITE side semantically correct; the READ side was already broken. Net behavior unchanged (still broken), but the inconsistency is now visible.
- Fix: swap the `joinColumns`/`inverseJoinColumns` on `User.managerOrgUnits` so `user_id` holds the User id and `org_id` holds the OrgUnit id. Verify no existing data depends on the current (wrong) layout.
- Out of scope here: `User` entity was not in this refactor's changeset.

### F2. `ReviewMgrImpl.permissionRoles` reads roles from the INPUT entity, not the loaded one
- `manager/impl/ReviewMgrImpl.java:235` — `Set<Role> roles = permission.getRoles();` uses the input (typically `null`), not `optionalPermission.get().getRoles()`. The method effectively always returns an empty list.
- Fix: read from the loaded entity: `optionalPermission.get().getRoles()`.
- Pre-existing; untouched by this refactor.

### F3. `@SQLDelete` parameter-order bug still on 4 entities
- `version = ?1 where id in (?2)` (no-op soft-delete under Hibernate 6.2, which binds `?1`=id, `?2`=version) remains on:
  - `entity/perm/Resource.java:39`
  - `entity/perm/Operation.java:40`
  - `entity/auth/AuthPwd.java:38`
  - `entity/sdt/OrgSdtNode.java:15`
- The same bug was fixed on User/Role/Permission/Post/OrgUnit in this refactor (their delete paths are tested). These 4 are unexercised by any current delete path.
- Fix: swap to `version = ?2 where id in (?1)` (verified pattern). Recommended to batch now while context is fresh — the next delete path added against these entities will silently ship a no-op soft-delete.

## Fragility / best-practice (working today)

### F4. `AdminMgrImpl.updatePermission` replaces the managed `resources` collection reference
- `AdminMgrImpl.updatePermission` — `BeanUtils.copyProperties(permission, storePermission, "id", "permName")` does NOT ignore `resources`, so it overwrites the managed `PersistentSet` reference with the input's plain `Set`. Replacing a managed collection reference is a Hibernate anti-pattern (forces full re-diff; relies on cascade luck). Inconsistent with the other 4 update methods, which ignore their associations.
- Fix (option B): ignore `"resources"` in the copy, then mutate the managed collection:
  ```java
  BeanUtils.copyProperties(permission, storePermission, "id", "permName", "resources");
  storePermission.getResources().clear();
  if (permission.getResources() != null) storePermission.getResources().addAll(permission.getResources());
  ```
- Test passes today; deferred to avoid regression risk in the final wave.

### F5. `Resource.hashCode()` is constant → single HashSet bucket
- `entity/perm/Resource.java` — `hashCode()` returns `Resource.class.hashCode()`. Standard fix for a DB-assigned-id entity in a HashSet, but collapses to O(n) lookups. Fine for the tiny per-permission resource sets today; revisit if cardinality grows.

## Cosmetic / minor

- **F6.** `ReviewMgrImpl.userOperationsOnResource` error label is `[roleOperationsOnResource]:` (copy-paste). `ReviewMgrImpl.java:178`.
- **F7.** `ReviewMgrImplTest.testReadPermissions` calls `readPermission` (singular) in its `assertThrows`; name/body mismatch (pre-existing, carried over).
- **F8.** `addPermission` saves the input entity directly (unlike addUser/addRole/addPost/addOrgUnit which copy first) — intentional to preserve cascade-persist of `resources`; add a clarifying comment.
- **F9.** `addUser/addRole/addPost/addOrgUnit` no longer guard against a caller-supplied duplicate `id` (the old `Objects.nonNull(getId())` check is gone) → a duplicate id now surfaces as `DataIntegrityViolationException` instead of `ValidationException`. Acceptable given "id is internal," worth documenting.
- **F10.** `MapTableConstants.POST_USR` and `POST_USER` are duplicate constants for `t_post_user_map` (`bindPost` checks one, saves the other). Pre-existing; confusing.
- **F11.** 11 empty `@Test` stubs in `OrgUnitMgrImplTest` (readPost, addOrgUnit, deleteOrgUnit, …) — pre-existing placeholders, preserved.

## Release note for consumers

- **F12.** `username`/`roleName`/`permName`/`postName`/`orgName` are now DB `NOT NULL UNIQUE`. Consumers with existing rows where these are NULL must populate them before upgrading (the `ddl-auto=update` ALTER will otherwise fail).
