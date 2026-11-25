## ADDED Requirements

### Requirement: Department Entity with Materialized Path
The system SHALL define a `Department` entity extending `Actor` (table `t_department`, discriminator `department`).
Each Department SHALL have a unique `deptName`, a unique `path` representing the materialized node path (format `/<id>` or `/<id1>/<id2>/...`), an integer `level` representing tree depth (starting at 1 for root nodes), and a set of associated `users`.

#### Scenario: Department fields validation
- **WHEN** a Department entity is inspected
- **THEN** it contains `deptName`, `path`, `level`, `description`, and a many-to-many relationship with `User` mapped by `t_department_user_map`

### Requirement: Create Department with Parent Path Validation
The system SHALL validate the `path` of a Department upon creation via `createDepartment(Department department)`.
If the path has multiple segments, the system SHALL extract `pPath` (the path prefix before the last `/`) and verify that a Department with `path == pPath` exists.
If the parent department does not exist, the system SHALL throw `ValidationException`.
If `pPath` is null/empty, the node SHALL be treated as a root node with `level == 1`.
If `pPath` exists, the department's `level` SHALL be set to `parent.level + 1`.

#### Scenario: Create root department successfully
- **WHEN** `createDepartment` is called with `path == "/1"` and `level == 1`
- **THEN** no parent lookup is performed and the department is successfully saved with `level == 1`

#### Scenario: Create child department when parent exists
- **WHEN** department `/1` exists and `createDepartment` is called with `path == "/1/2"`
- **THEN** the parent path `/1` is found, the new department is saved with `level == 2`

#### Scenario: Create child department when parent does not exist
- **WHEN** no department exists with path `/99` and `createDepartment` is called with `path == "/99/100"`
- **THEN** the system throws `ValidationException` indicating the parent department path does not exist

### Requirement: Department CRUD Operations in OrgMgr
The system SHALL provide `OrgMgr` with standard Department operations: `createDepartment`, `updateDepartment`, `deleteDepartment`, `readDepartment`, and `readDepartments`.

#### Scenario: Update department
- **WHEN** `updateDepartment` is called with an existing `deptName` and valid new department details
- **THEN** the scalar fields of the department are updated and saved

#### Scenario: Delete department
- **WHEN** `deleteDepartment` is called for an existing department
- **THEN** the department and its mappings in `t_department_user_map` are deleted

### Requirement: User Membership Management in Department
The system SHALL provide `addUser(Department department, User user)` to associate an existing user with an existing department, and `removeUser(Department department, User user)` to remove the association.

#### Scenario: Add user to department
- **WHEN** `addUser` is called with an existing department and an existing user that is not yet a member
- **THEN** the association is created in `t_department_user_map`

#### Scenario: Add duplicate user to department throws exception
- **WHEN** `addUser` is called with an existing user who is already a member of the department
- **THEN** the system throws `ValidationException`

#### Scenario: Remove user from department
- **WHEN** `removeUser` is called with an existing department and an existing user who is a member
- **THEN** the association is removed from `t_department_user_map`

#### Scenario: Remove non-member user throws exception
- **WHEN** `removeUser` is called for a user not belonging to the department
- **THEN** the system throws `ValidationException`

### Requirement: Organization Tree Query
The system SHALL provide `readDepartmentTree()` to query the full organization tree, and `readDepartmentSubTree(Department department)` to query the subtree rooted at a specific department.
The queries SHALL return structured `DepartmentTreeNode` objects containing the current department and recursively nested `children`.

#### Scenario: Read full department tree
- **WHEN** multiple root and child departments exist and `readDepartmentTree()` is invoked
- **THEN** the system queries all active departments in a single SQL query ordered by `level` and builds the nested tree structure in memory

#### Scenario: Read department sub-tree
- **WHEN** `readDepartmentSubTree(department)` is called for an existing department
- **THEN** the system queries only the department and its descendants using path prefix matching, and returns the root `DepartmentTreeNode` with nested children

### Requirement: Query Direct and Successor Users by Department ID
The system SHALL provide `readDepartmentUsers(Long departmentId)` to return direct members of the department, and `readDepartmentSuccessorUsers(Long departmentId)` to return the aggregated, de-duplicated users belonging to the department or any of its descendant departments.

#### Scenario: Read direct users of a department
- **WHEN** `readDepartmentUsers(deptId)` is called
- **THEN** the system returns only users directly assigned to the department with id `deptId`

#### Scenario: Read successor users including descendant departments
- **WHEN** department A has user U1, child department B has user U2, and `readDepartmentSuccessorUsers(deptA.getId())` is called
- **THEN** the returned list contains both U1 and U2
