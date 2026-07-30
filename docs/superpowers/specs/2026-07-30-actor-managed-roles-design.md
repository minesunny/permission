# Design Specification: Actor Managed Roles Relationship

## 1. Overview
This specification details the extension of the RBAC framework to support "Managed Roles" (角色管理权). An `Actor` (User, Post, or OrgUnit) can be assigned managerial responsibility over specific `Role`s, granting them the authorization to manage and assign those roles.

## 2. Data Model & Architecture

### 2.1 Database Join Table
A new join table `t_managed_role_actor_map` will be introduced.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `actor_id` | bigint | FK -> `t_actor.id`, NOT NULL | The manager actor |
| `role_id` | bigint | FK -> `t_role.id`, NOT NULL | The managed role |

Primary Key: `(actor_id, role_id)`

### 2.2 Constant Definition (`MapTableConstants.java`)
```java
public static final String MAP_TABLE_MANAGED_ROLE_ACTOR = "t_managed_role_actor_map";
```

### 2.3 JPA Entity Changes

#### `Actor.java`
```java
@ManyToMany
@Comment("Roles that this actor has authority to manage and assign")
@JoinTable(name = MapTableConstants.MAP_TABLE_MANAGED_ROLE_ACTOR,
        joinColumns = @JoinColumn(name = "actor_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"))
@Fetch(FetchMode.SELECT)
private Set<Role> managedRoles;
```

#### `Role.java`
```java
@ManyToMany(mappedBy = "managedRoles", targetEntity = Actor.class)
@Fetch(FetchMode.SELECT)
private Set<Actor> managedActors;
```

---

## 3. Manager API Specification

### 3.1 `AdminMgr` Interface
```java
/**
 * Assigns management/administration authority of a role to an actor.
 */
void assignManagedRole(@NotNull Actor actor, @NotNull Role role) throws RBACException;

/**
 * Revokes management/administration authority of a role from an actor.
 */
void deassignManagedRole(@NotNull Actor actor, @NotNull Role role) throws RBACException;
```

### 3.2 `ReviewMgr` Interface
```java
/**
 * Returns the list of roles that a given actor has authority to manage.
 */
@NotNull List<Role> managedRoles(@NotNull Actor actor) throws RBACException;

/**
 * Checks whether an actor has management authority over a specific role.
 */
boolean hasManageRole(@NotNull Actor actor, @NotNull Role role) throws RBACException;

/**
 * Checks whether an actor has management authority over any role containing a specific permission.
 */
boolean hasManagePermission(@NotNull Actor actor, @NotNull Permission permission) throws RBACException;
```

---

## 4. Verification & Testing Strategy
1. **Unit Tests**:
   - `AdminMgrImplTest`: Test `assignManagedRole` and `deassignManagedRole`.
   - `ReviewMgrImplTest`: Test `managedRoles`, `hasManageRole`, and `hasManagePermission`.
2. **Database Verification**:
   - Verify JPA table auto-creation / mapping for `t_managed_role_actor_map`.
