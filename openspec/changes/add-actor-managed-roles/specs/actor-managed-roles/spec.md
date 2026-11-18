# Spec: Actor Managed Roles Relationship

## Requirements

### Requirement: Entity Mapping
The system SHALL support mapping management relationships between `Actor` and `Role` entities.
- `Actor` SHALL possess a `managedRoles` property representing roles the actor is authorized to manage/assign.
- `Role` SHALL possess a `managedActors` property representing actors authorized to manage the role.
- Join table name MUST be `t_managed_role_actor_map`.

### Requirement: Role Management Mutations (`AdminMgr`)
- `assignManagedRole(Actor actor, Role role)`: Assign management responsibility over `role` to `actor`. Throw `ValidationException` if relationship already exists.
- `deassignManagedRole(Actor actor, Role role)`: Revoke management responsibility over `role` from `actor`. Throw `ValidationException` if relationship does not exist.

### Requirement: Role Management Audit & Checks (`ReviewMgr`)
- `managedRoles(Actor actor)`: Return a list of roles managed by `actor`.
- `hasManageRole(Actor actor, Role role)`: Return true if `actor` is authorized to manage `role`.
- `hasManagePermission(Actor actor, Permission permission)`: Return true if `actor` manages any role that grants `permission`.
