# Actor Managed Roles Relationship Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the RBAC framework so that an `Actor` (User, Post, or OrgUnit) can have a management relationship with `Role`s (`managedRoles`), allowing delegated role administration (assigning/revoking roles and checking management authorization).

**Architecture:** Add a new join table constant `MANAGED_ROLE_ACTOR = "t_managed_role_actor_map"` in `MapTableConstants`. Map `@ManyToMany Set<Role> managedRoles` in `Actor` and `@ManyToMany Set<Actor> managedActors` in `Role`. Implement mutation methods (`assignManagedRole`, `deassignManagedRole`) in `AdminMgr` / `AdminMgrImpl` using `MapMgr`. Implement query/auth check methods (`managedRoles`, `hasManageRole`, `hasManagePermission`) in `ReviewMgr` / `ReviewMgrImpl`.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA / Hibernate, JUnit 5, Lombok, MapMgr (com.minesunny.jpa).

## Global Constraints

- Follow existing RBAC natural key conventions (`actorName`, `roleName`, `permName`).
- Method names: `managedRoles(@NotNull Actor actor)`, `hasManageRole(@NotNull Actor actor, @NotNull Role role)`, `hasManagePermission(@NotNull Actor actor, @NotNull Permission permission)`, `assignManagedRole(@NotNull Actor actor, @NotNull Role role)`, `deassignManagedRole(@NotNull Actor actor, @NotNull Role role)`.
- Use `MapMgr` for join table persistence operations on `MANAGED_ROLE_ACTOR`.

---

### Task 1: MapTableConstants and Entity Model Extensions

**Files:**
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/type/MapTableConstants.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/entity/actor/Actor.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/entity/Role.java`
- Test: `permission-core/src/test/java/com/minesunny/permission/core/manager/impl/AdminMgrImplTest.java`

**Interfaces:**
- Consumes: Existing `Actor` and `Role` entity definitions
- Produces:
  - `MapTableConstants.MANAGED_ROLE_ACTOR` (`"t_managed_role_actor_map"`)
  - `Actor.getManagedRoles()` / `Actor.setManagedRoles(Set<Role>)`
  - `Role.getManagedActors()` / `Role.setManagedActors(Set<Actor>)`

- [ ] **Step 1: Add `MANAGED_ROLE_ACTOR` to `MapTableConstants` and `@ManyToMany` mappings to `Actor` and `Role`**

In `permission-core/src/main/java/com/minesunny/permission/core/type/MapTableConstants.java`:
```java
String MANAGED_ROLE_ACTOR = "t_managed_role_actor_map";
```

In `permission-core/src/main/java/com/minesunny/permission/core/entity/actor/Actor.java`:
```java
@ManyToMany
@Comment("Roles that this actor has authority to manage and assign")
@JoinTable(name = MapTableConstants.MANAGED_ROLE_ACTOR,
        joinColumns = @JoinColumn(name = "actor_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"))
@Fetch(FetchMode.SELECT)
private Set<Role> managedRoles;
```

In `permission-core/src/main/java/com/minesunny/permission/core/entity/Role.java`:
```java
@ManyToMany(mappedBy = "managedRoles", targetEntity = Actor.class)
@Fetch(FetchMode.SELECT)
private Set<Actor> managedActors;
```

- [ ] **Step 2: Verify project compilation**

Run: `./gradlew test-classes` or `mvn test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit entity & constant modifications**

```bash
git add permission-core/src/main/java/com/minesunny/permission/core/type/MapTableConstants.java permission-core/src/main/java/com/minesunny/permission/core/entity/actor/Actor.java permission-core/src/main/java/com/minesunny/permission/core/entity/Role.java
git commit -m "feat: add managedRoles and MANAGED_ROLE_ACTOR entity mapping"
```

---

### Task 2: AdminMgr Managed Role Operations (`assignManagedRole` & `deassignManagedRole`)

**Files:**
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/manager/AdminMgr.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/manager/impl/AdminMgrImpl.java`
- Test: `permission-core/src/test/java/com/minesunny/permission/core/manager/impl/AdminMgrImplTest.java`

**Interfaces:**
- Consumes: `MapMgr`, `ActorRepository`, `RoleRepository`, `MapTableConstants.MANAGED_ROLE_ACTOR`
- Produces:
  - `AdminMgr.assignManagedRole(@NotNull Actor actor, @NotNull Role role)`
  - `AdminMgr.deassignManagedRole(@NotNull Actor actor, @NotNull Role role)`

- [ ] **Step 1: Write failing tests in `AdminMgrImplTest.java`**

Add tests to `AdminMgrImplTest.java`:
```java
@Test
void testAssignAndDeassignManagedRole() throws RBACException {
    User user = User.builder().username("mgr_user").actorName("mgr_user_actor").build();
    adminMgr.addActor(user);

    Role role = Role.builder().roleName("ROLE_TARGET").build();
    adminMgr.addRole(role);

    adminMgr.assignManagedRole(user, role);

    // Re-assigning should throw ValidationException
    assertThrows(ValidationException.class, () -> adminMgr.assignManagedRole(user, role));

    adminMgr.deassignManagedRole(user, role);

    // Re-deassigning should throw ValidationException
    assertThrows(ValidationException.class, () -> adminMgr.deassignManagedRole(user, role));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run test method via `./gradlew test --tests com.minesunny/permission.core.manager.impl.AdminMgrImplTest`
Expected: FAIL (compilation error / missing method)

- [ ] **Step 3: Declare methods in `AdminMgr.java`**

In `permission-core/src/main/java/com/minesunny/permission/core/manager/AdminMgr.java`:
```java
/**
 * Assigns management/administration authority of a role to an actor.
 */
void assignManagedRole(@NotNull Actor actor, @NotNull Role role) throws RBACException;

/**
 * Removes management/administration authority of a role from an actor.
 */
void deassignManagedRole(@NotNull Actor actor, @NotNull Role role) throws RBACException;
```

- [ ] **Step 4: Implement methods in `AdminMgrImpl.java`**

In `permission-core/src/main/java/com/minesunny/permission/core/manager/impl/AdminMgrImpl.java`:
```java
@Override
public void assignManagedRole(@NotNull Actor actor, @NotNull Role role) throws RBACException {
    ActorRoleIds ids = resolveActorRole(actor, role, "assignManagedRole");
    Map<String, List<Long>> map = new HashMap<>();
    map.put("actor_id", List.of(ids.actorId()));
    map.put("role_id", List.of(ids.roleId()));
    try {
        if (mapMgr.exist(MANAGED_ROLE_ACTOR, map)) {
            throw new ValidationException("[assignManagedRole]:    The RBAC managed role[id: %s] entity has assigned to actor[id: %s] entity"
                    .formatted(ids.roleId(), ids.actorId()));
        }
        mapMgr.save(MANAGED_ROLE_ACTOR, map);
    } catch (ServiceException e) {
        throw rethrow(e);
    }
}

@Override
public void deassignManagedRole(@NotNull Actor actor, @NotNull Role role) throws RBACException {
    ActorRoleIds ids = resolveActorRole(actor, role, "deassignManagedRole");
    Map<String, List<Long>> map = new HashMap<>();
    map.put("actor_id", List.of(ids.actorId()));
    map.put("role_id", List.of(ids.roleId()));
    try {
        if (!mapMgr.exist(MANAGED_ROLE_ACTOR, map)) {
            throw new ValidationException("[deassignManagedRole]:    The RBAC managed role[id: %s] entity has deassigned from actor[id: %s] entity"
                    .formatted(ids.roleId(), ids.actorId()));
        }
        mapMgr.delete(MANAGED_ROLE_ACTOR, map);
    } catch (ServiceException e) {
        throw rethrow(e);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests com.minesunny.permission.core.manager.impl.AdminMgrImplTest`
Expected: PASS

- [ ] **Step 6: Commit AdminMgr implementation**

```bash
git add permission-core/src/main/java/com/minesunny/permission/core/manager/AdminMgr.java permission-core/src/main/java/com/minesunny/permission/core/manager/impl/AdminMgrImpl.java permission-core/src/test/java/com/minesunny/permission/core/manager/impl/AdminMgrImplTest.java
git commit -m "feat: implement assignManagedRole and deassignManagedRole in AdminMgr"
```

---

### Task 3: ReviewMgr Managed Role Query & Authorization Checks

**Files:**
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/manager/ReviewMgr.java`
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/manager/impl/ReviewMgrImpl.java`
- Test: `permission-core/src/test/java/com/minesunny/permission/core/manager/impl/ReviewMgrImplTest.java`

**Interfaces:**
- Consumes: `ActorRepository`, `RoleRepository`, `PermissionRepository`, `AdminMgr`
- Produces:
  - `ReviewMgr.managedRoles(@NotNull Actor actor)`
  - `ReviewMgr.hasManageRole(@NotNull Actor actor, @NotNull Role role)`
  - `ReviewMgr.hasManagePermission(@NotNull Actor actor, @NotNull Permission permission)`

- [ ] **Step 1: Write failing unit tests in `ReviewMgrImplTest.java`**

In `permission-core/src/test/java/com/minesunny/permission/core/manager/impl/ReviewMgrImplTest.java`:
```java
@Test
void testManagedRolesAndHasManageRoleAndPermission() throws RBACException {
    User user = User.builder().username("admin_user").actorName("admin_user_actor").build();
    adminMgr.addActor(user);

    Role role = Role.builder().roleName("ROLE_EDITOR").build();
    adminMgr.addRole(role);

    Permission perm = Permission.builder().permName("article:edit").build();
    adminMgr.addPermission(perm);
    adminMgr.grantPermission(perm, role);

    // Initial check: should be empty / false
    assertTrue(reviewMgr.managedRoles(user).isEmpty());
    assertFalse(reviewMgr.hasManageRole(user, role));
    assertFalse(reviewMgr.hasManagePermission(user, perm));

    // Assign management relationship
    adminMgr.assignManagedRole(user, role);

    // Verify managedRoles
    List<Role> mRoles = reviewMgr.managedRoles(user);
    assertEquals(1, mRoles.size());
    assertEquals("ROLE_EDITOR", mRoles.get(0).getRoleName());

    // Verify hasManageRole & hasManagePermission
    assertTrue(reviewMgr.hasManageRole(user, role));
    assertTrue(reviewMgr.hasManagePermission(user, perm));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.minesunny.permission.core.manager.impl.ReviewMgrImplTest`
Expected: FAIL (methods not found)

- [ ] **Step 3: Declare methods in `ReviewMgr.java`**

In `permission-core/src/main/java/com/minesunny/permission/core/manager/ReviewMgr.java`:
```java
/**
 * Returns the list of roles managed by a given actor.
 */
@NotNull List<Role> managedRoles(@NotNull Actor actor) throws RBACException;

/**
 * Checks whether an actor has management authority over a specific role.
 */
boolean hasManageRole(@NotNull Actor actor, @NotNull Role role) throws RBACException;

/**
 * Checks whether an actor has management authority over any role that carries the given permission.
 */
boolean hasManagePermission(@NotNull Actor actor, @NotNull Permission permission) throws RBACException;
```

- [ ] **Step 4: Implement methods in `ReviewMgrImpl.java`**

In `permission-core/src/main/java/com/minesunny/permission/core/manager/impl/ReviewMgrImpl.java`:
```java
@Override
public @NotNull List<Role> managedRoles(@NotNull Actor actor) throws RBACException {
    Optional<Actor> optionalActor;
    if (Objects.nonNull(actor.getId())) {
        optionalActor = actorRepository.findById(actor.getId());
    } else if (StringUtils.hasText(actor.getActorName())) {
        optionalActor = actorRepository.findByActorName(actor.getActorName());
    } else {
        return Collections.emptyList();
    }
    if (optionalActor.isPresent()) {
        Set<Role> roles = optionalActor.get().getManagedRoles();
        if (Objects.isNull(roles) || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(roles);
    }
    return Collections.emptyList();
}

@Override
public boolean hasManageRole(@NotNull Actor actor, @NotNull Role role) throws RBACException {
    ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
    List<Role> mRoles = reviewMgr.managedRoles(actor);
    if (mRoles.isEmpty()) {
        return false;
    }
    NaturalKeys.requireRoleName(role);
    return mRoles.stream().anyMatch(r -> role.getRoleName().equals(r.getRoleName()));
}

@Override
public boolean hasManagePermission(@NotNull Actor actor, @NotNull Permission permission) throws RBACException {
    ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
    List<Role> mRoles = reviewMgr.managedRoles(actor);
    if (mRoles.isEmpty()) {
        return false;
    }
    NaturalKeys.requirePermName(permission);
    return mRoles.stream()
            .map(Role::getPermissions)
            .filter(Objects::nonNull)
            .flatMap(Set::stream)
            .anyMatch(p -> permission.getPermName().equals(p.getPermName()));
}
```

- [ ] **Step 5: Run tests to verify all tests pass**

Run: `./gradlew test`
Expected: BUILD SUCCESS and ALL TESTS PASS

- [ ] **Step 6: Commit ReviewMgr implementation**

```bash
git add permission-core/src/main/java/com/minesunny/permission/core/manager/ReviewMgr.java permission-core/src/main/java/com/minesunny/permission/core/manager/impl/ReviewMgrImpl.java permission-core/src/test/java/com/minesunny/permission/core/manager/impl/ReviewMgrImplTest.java
git commit -m "feat: implement managedRoles, hasManageRole, and hasManagePermission in ReviewMgr"
```

---

## Plan Self-Review & Verification
- All spec requirements covered (Entities, Join Table, AdminMgr, ReviewMgr APIs).
- Method names and signatures match exact user requirements (`managedRoles`, `hasManageRole`, `hasManagePermission`, `assignManagedRole`, `deassignManagedRole`).
- Every step contains specific code, commands, and expected outcomes.
