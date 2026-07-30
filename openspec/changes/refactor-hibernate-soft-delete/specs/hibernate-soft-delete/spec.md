## ADDED Requirements

### Requirement: Timestamp-based Soft Delete for Entities
Entities (`Role`, `Actor`, `Permission`, `Policy`, `OrgSdtNode`, `AuthPwd`) SHALL be soft-deleted by setting `delete_at` to the current timestamp when deleted via JPA repository `delete()` methods.

#### Scenario: Soft deleting an entity updates timestamp
- **WHEN** an entity is deleted using `repository.delete(entity)` or `repository.deleteById(id)`
- **THEN** the record in database SHALL be updated with `delete_at = CURRENT_TIMESTAMP`
- **THEN** the record SHALL NOT be physically removed from the table

### Requirement: Automatic Soft Delete Filtering
JPA queries (HQL, JPQL, Finders) SHALL automatically exclude soft-deleted records where `delete_at IS NOT NULL`.

#### Scenario: Querying entities excludes deleted items
- **WHEN** querying entities via `repository.findAll()` or finder methods
- **THEN** only entities with `delete_at IS NULL` SHALL be returned

### Requirement: Physical Cleanup of Many-To-Many Join Tables
Join tables for entity relationships SHALL perform physical `DELETE` queries when entities are deleted to unbind relationships.

#### Scenario: Deleting a Role physically removes join table records
- **WHEN** a `Role` entity is soft-deleted
- **THEN** corresponding records in `t_role_actor_map` and `t_role_permission_map` SHALL be physically deleted
