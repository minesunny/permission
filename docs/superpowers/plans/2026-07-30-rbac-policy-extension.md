# RBAC Permission Policy Extension and Natural Key Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor RBAC permissions by replacing `Resource` and `Operation` entity mappings in `Permission` with plain string fields (`resource` and `operation`), and introduce fine-grained user-specific policy extension (`Policy`) and policy template support.

**Architecture:** 
1. Remove `Resource` and `Operation` entities and their repositories; update `Permission` with scalar `String resource` and `String operation` columns.
2. Introduce `Policy` base entity (inheritable `TABLE_PER_CLASS` table `t_policy`) and `PolicyRepository`.
3. Enhance `Permission` with `@Transient private Policy policy;` and `@Transient private String policyOwnerId;`.
4. Manage permission policy mappings via `MapMgr` with table `t_permission_policy_map` (`permission_id`, `user_id`, `policy_id`).
5. Update `AdminMgr` and `ReviewMgr` to support policy template creation, user policy binding, and context-aware policy loading.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA, Hibernate, JUnit 5, Lombok, H2 Database.

## Global Constraints

- Java 17 source compatibility.
- Use `@NotNull` annotations from `org.jetbrains.annotations.NotNull`.
- Validation errors must throw `com.minesunny.permission.core.exception.ValidationException`.
- Database operations on map tables must use `MapMgr`.
- Interface-level `@Transactional` on `AdminMgr` and `ReviewMgr`.

---

### Task 1: Remove Resource and Operation entities and refactor Permission entity

**Files:**
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/entity/perm/Permission.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/type/MapTableConstants.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/util/NaturalKeys.java`
- Delete: `permission-core/src/main/java/com/minesunny/permission/core/entity/perm/Resource.java`
- Delete: `permission-core/src/main/java/com/minesunny/permission/core/entity/perm/Operation.java`
- Delete: `permission-core/src/main/java/com/minesunny/permission/core/repository/ResourceRepository.java`
- Delete: `permission-core/src/main/java/com/minesunny/permission/core/repository/OperationRepository.java`
- Delete: `permission-core/src/test/java/com/minesunny/permission/core/entity/example/e1/ExampleResource1.java`
- Delete: `permission-core/src/test/java/com/minesunny/permission/core/entity/example/e2/ExampleResource2.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/manager/ReviewMgr.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/manager/impl/ReviewMgrImpl.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/manager/impl/AdminMgrImpl.java`

**Interfaces:**
- Produces: Updated `Permission` entity with `String resource` and `String operation` fields.
- Removes: `Resource` and `Operation` domain classes and `roleOperationsOnResource` / `actorOperationsOnResource` methods.

- [ ] **Step 1: Update Permission entity to use String resource and String operation**

In `Permission.java`, remove `Set<Resource> resources` and `Set<Operation> operations`. Add:
```java
@Column(name = "resource")
@Comment(value = "permission resource")
private String resource;

@Column(name = "operation")
@Comment(value = "define what operation of the resource")
private String operation;
```

- [ ] **Step 2: Remove Resource/Operation files and constants**

Remove `Resource.java`, `Operation.java`, `ResourceRepository.java`, `OperationRepository.java`, `ExampleResource1.java`, `ExampleResource2.java`.
In `MapTableConstants.java`, remove `PERMISSION_RESOURCE` and `PERMISSION_OPERATION`.
In `NaturalKeys.java`, remove `requireResourceName` and `requireOperationName`.

- [ ] **Step 3: Clean up ReviewMgr & AdminMgrImpl references to Resource/Operation**

In `ReviewMgr.java` and `ReviewMgrImpl.java`, remove `roleOperationsOnResource` and `actorOperationsOnResource`.
In `AdminMgrImpl.java`, remove `validateSingleResourceType` and `resourceRepository` references.

- [ ] **Step 4: Verify build succeeds**

Run: `./gradlew :permission-core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add permission-core/
git commit -m "refactor: replace Resource and Operation entities in Permission with String fields"
```

---

### Task 2: Create Policy entity, PolicyRepository, and helper utilities

**Files:**
- Create: `permission-core/src/main/java/com/minesunny/permission/core/entity/perm/Policy.java`
- Create: `permission-core/src/main/java/com/minesunny/permission/core/repository/PolicyRepository.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/entity/perm/Permission.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/type/MapTableConstants.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/util/NaturalKeys.java`

**Interfaces:**
- Produces: `Policy` entity, `PolicyRepository` spring repository, and `PERMISSION_POLICY` constant in `MapTableConstants`.

- [ ] **Step 1: Create Policy entity**

Create `Policy.java`:
```java
package com.minesunny.permission.core.entity.perm;

import com.minesunny.jpa.entity.CachedIdEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.SQLDelete;

@Entity
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@DiscriminatorColumn(name = "policy_type")
@Table(name = "t_policy")
@Setter
@Getter
@SQLDelete(sql = "update t_policy set delete_at = current_timestamp, version = ?2 where id in (?1) and delete_at is null")
public class Policy extends CachedIdEntity {

    @Column(name = "policy_name", unique = true, nullable = false)
    @Comment(value = "policyName")
    private String policyName;

    @Column(name = "description")
    @Comment(value = "description policy")
    private String description;

    @Override
    public int hashCode() {
        return Policy.class.hashCode();
    }
}
```

- [ ] **Step 2: Create PolicyRepository**

Create `PolicyRepository.java`:
```java
package com.minesunny.permission.core.repository;

import com.minesunny.permission.core.entity.perm.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long>, JpaSpecificationExecutor<Policy> {
    Optional<Policy> findByPolicyName(String policyName);
}
```

- [ ] **Step 3: Update Permission entity with transient policy and policyOwnerId**

In `Permission.java`, add transient fields:
```java
@Transient
private Policy policy;

@Transient
private String policyOwnerId;
```

- [ ] **Step 4: Update MapTableConstants and NaturalKeys**

In `MapTableConstants.java`:
```java
String PERMISSION_POLICY = "t_permission_policy_map";
```

In `NaturalKeys.java`:
```java
public static String requirePolicyName(Policy policy) throws ValidationException {
    return require(policy.getPolicyName(), "policy's policyName");
}
```

- [ ] **Step 5: Verify build succeeds**

Run: `./gradlew :permission-core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add permission-core/
git commit -m "feat: add Policy entity, PolicyRepository, and Permission transient policy fields"
```

---

### Task 3: Support Policy Template saving in AdminMgr and user policy operations

**Files:**
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/manager/AdminMgr.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/manager/impl/AdminMgrImpl.java`

**Interfaces:**
- Consumes: `Permission.policy`, `PolicyRepository`, `MapMgr` (`PERMISSION_POLICY`)
- Produces: `AdminMgr.addPermissionPolicy`, `deletePermissionPolicy`, `updatePermissionPolicy` and policy template mapping logic in `addPermission`/`updatePermission`/`deletePermission`.

- [ ] **Step 1: Define new policy methods in AdminMgr interface**

Add the following methods to `AdminMgr.java`:
```java
void addPermissionPolicy(@NotNull String username, @NotNull String permName, @NotNull Policy policy) throws RBACException;

void updatePermissionPolicy(@NotNull String username, @NotNull String permName, @NotNull Policy policy) throws RBACException;

void deletePermissionPolicy(@NotNull String username, @NotNull String permName) throws RBACException;
```

- [ ] **Step 2: Implement policy template handling in addPermission and updatePermission**

In `AdminMgrImpl.java`:
- When `addPermission(permission)` is invoked:
  1. Save permission via `permissionRepository.saveAndFlush(permission)`.
  2. Check if `permission.getPolicy()` is not null.
  3. If policy has no ID, save policy via `policyRepository.saveAndFlush(permission.getPolicy())`.
  4. Save template mapping in `PERMISSION_POLICY` with `permission_id` -> `permission.getId()`, `policy_id` -> `policy.getId()`.
- When `deletePermission(permission)` is invoked:
  1. Delete entries from `PERMISSION_POLICY` where `permission_id` = `storePermission.getId()`.

- [ ] **Step 3: Implement addPermissionPolicy, updatePermissionPolicy, deletePermissionPolicy**

In `AdminMgrImpl.java`:
- `addPermissionPolicy(username, permName, policy)`:
  1. Find `User` by `username` via `userRepository` (or `actorRepository`). Throw `ValidationException` if not found.
  2. Find `Permission` by `permName` via `permissionRepository`. Throw `ValidationException` if not found.
  3. Save policy if ID is null.
  4. Check if map exists in `PERMISSION_POLICY` for `permission_id` and `user_id`. If exists, throw `ValidationException`.
  5. Save map: `permission_id` -> permId, `user_id` -> userId, `policy_id` -> policyId.
- `updatePermissionPolicy(username, permName, policy)`:
  1. Find `User` and `Permission`.
  2. Save policy if ID is null.
  3. Delete existing map entry for `permission_id` and `user_id` in `PERMISSION_POLICY`.
  4. Save new map entry.
- `deletePermissionPolicy(username, permName)`:
  1. Find `User` and `Permission`.
  2. Delete map entry for `permission_id` and `user_id` in `PERMISSION_POLICY`.

- [ ] **Step 4: Verify build succeeds**

Run: `./gradlew :permission-core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add permission-core/
git commit -m "feat: implement policy template saving and user policy CRUD in AdminMgr"
```

---

### Task 4: Implement Policy loading logic in ReviewMgr

**Files:**
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/manager/ReviewMgr.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/manager/impl/ReviewMgrImpl.java`

**Interfaces:**
- Consumes: `PERMISSION_POLICY`, `PolicyRepository`, `MapMgr`
- Produces: Policy auto-population for `readPermission`, `readPermissions`, `rolePermissions`, `actorPermissions`, `assignedRoles`.

- [ ] **Step 1: Implement policy resolution helper in ReviewMgrImpl**

Add private helper method `populatePolicy(Permission permission)`:
- If `permission.getPolicyOwnerId()` != null:
  - Query `PERMISSION_POLICY` for `policy_id` where `permission_id` = `permission.getId()` and `user_id` = `permission.getPolicyOwnerId()`.
  - If found, load `Policy` from `policyRepository` and set to `permission.setPolicy(policy)`.
  - If not found, fall back to template policy (where `user_id` is null or default).
- If `permission.getPolicyOwnerId()` == null:
  - Query `PERMISSION_POLICY` for `policy_id` where `permission_id` = `permission.getId()` (template policy).
  - If found, load `Policy` from `policyRepository` and set to `permission.setPolicy(policy)`.

- [ ] **Step 2: Update readPermission & readPermissions**

- `readPermission(permission)`: If permission found, run `populatePolicy(permission)` using `permission.getPolicyOwnerId()`.
- `readPermissions(permission)`: Standard `readPermissions` queries default policy templates for returned permissions.

- [ ] **Step 3: Update rolePermissions, actorPermissions, and assignedRoles**

- `rolePermissions(role)`: Populates policy templates for permissions of the role.
- `actorPermissions(actor)`: If `actor` is a `User`, sets `policyOwnerId` to `actor.getId()` when loading permissions so user-specific policies are populated!
- `assignedRoles(actor)`: If `actor` is a `User`, sets `policyOwnerId` to `actor.getId()` when loading permissions inside roles.

- [ ] **Step 4: Verify build succeeds**

Run: `./gradlew :permission-core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add permission-core/
git commit -m "feat: implement policy template and user policy reading in ReviewMgr"
```

---

### Task 5: Update Unit Tests and Verify Complete Functionality

**Files:**
- Modify: `permission-core/src/test/java/com/minesunny/permission/core/manager/impl/AdminMgrImplTest.java`
- Modify: `permission-core/src/test/java/com/minesunny/permission/core/manager/impl/OrgUnitMgrImplTest.java`
- Modify: `permission-core/src/test/java/com/minesunny/permission/core/manager/impl/ReviewMgrImplTest.java`

**Interfaces:**
- Test suite verifying Permission scalar resource/operation fields, Policy template persistence, user policy override, and RBAC operations.

- [ ] **Step 1: Fix broken tests due to Resource/Operation removal**

Update `AdminMgrImplTest.java`, `OrgUnitMgrImplTest.java`, and `ReviewMgrImplTest.java` to set `resource` and `operation` as plain strings on `Permission` instances (e.g. `perm.setResource("system:user")`, `perm.setOperation("read")`).

- [ ] **Step 2: Add test cases for Policy template and user policy override**

In `AdminMgrImplTest.java` and `ReviewMgrImplTest.java`, add tests:
1. `testAddPermissionWithPolicyTemplate()`: Verify `addPermission` creates a policy template when `permission.setPolicy(...)` is provided.
2. `testUserPolicyBinding()`: Call `addPermissionPolicy(username, permName, userPolicy)` and verify `actorPermissions(user)` returns the user-specific policy instead of the default template.
3. `testUpdateAndDeleteUserPolicy()`: Test `updatePermissionPolicy` and `deletePermissionPolicy`.

- [ ] **Step 3: Run all unit tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add permission-core/
git commit -m "test: update unit tests for Permission policy extension and natural key refactoring"
```

---
