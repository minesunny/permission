# Proposal: Add Managed Roles Relationship to Actor and Role

## Problem Statement
Currently, the RBAC framework only supports one relationship between `Actor` and `Role`: operational role assignment (which permissions an actor possesses to operate the system). In administrative scenarios, organizations need delegated role administration—defining which roles an actor is authorized to assign or revoke to/from other actors.

## Proposed Solution
Introduce a dedicated management relationship between `Actor` and `Role` via a dedicated join table `t_managed_role_actor_map`.

1. **JPA Entity Mapping**:
   - `Actor`: Add `@ManyToMany` `managedRoles` mapping to `t_managed_role_actor_map`.
   - `Role`: Add inverse `@ManyToMany(mappedBy = "managedRoles")` `managedActors`.
   - `MapTableConstants`: Add `MAP_TABLE_MANAGED_ROLE_ACTOR = "t_managed_role_actor_map"`.

2. **Manager Interfaces**:
   - `ReviewMgr`: Add `managedRoles(Actor actor)`, `hasManageRole(Actor actor, Role role)`, `hasManagePermission(Actor actor, Permission permission)`.
   - `AdminMgr`: Add `assignManagedRole(Actor actor, Role role)`, `deassignManagedRole(Actor actor, Role role)`.

3. **Implementation**:
   - Implement relationship query and mutation logic in `AdminMgrImpl` and `ReviewMgrImpl`.
