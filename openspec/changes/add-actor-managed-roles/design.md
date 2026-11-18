# Design: Actor Managed Roles Relationship

## Architecture Overview
Extend the RBAC framework so that an `Actor` (User, Post, or OrgUnit) can have a management relationship with `Role`s (`managedRoles`), allowing delegated role administration (assigning/revoking roles and checking management authorization).

Add a new join table constant `MANAGED_ROLE_ACTOR = "t_managed_role_actor_map"` in `MapTableConstants`. Map `@ManyToMany Set<Role> managedRoles` in `Actor` and `@ManyToMany Set<Actor> managedActors` in `Role`. Implement mutation methods (`assignManagedRole`, `deassignManagedRole`) in `AdminMgr` / `AdminMgrImpl` using `MapMgr`. Implement query/auth check methods (`managedRoles`, `hasManageRole`, `hasManagePermission`) in `ReviewMgr` / `ReviewMgrImpl`.

## Data Model Changes
- Add `String MANAGED_ROLE_ACTOR = "t_managed_role_actor_map";` to `MapTableConstants`.
- Add `@ManyToMany Set<Role> managedRoles` mapping to `t_managed_role_actor_map` in `Actor`.
- Add `@ManyToMany(mappedBy = "managedRoles") Set<Actor> managedActors` in `Role`.

## Manager Interface Changes
- `AdminMgr`:
  - `assignManagedRole(Actor actor, Role role)`
  - `deassignManagedRole(Actor actor, Role role)`
- `ReviewMgr`:
  - `managedRoles(Actor actor)`
  - `hasManageRole(Actor actor, Role role)`
  - `hasManagePermission(Actor actor, Permission permission)`
