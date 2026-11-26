## 1. Entity and Repository Layer

- [x] 1.1 Rename/replace `Post` entity with `Department` (table `t_department`, discriminator `department`, fields: `deptName`, `path`, `level`, `description`, `users`)
- [x] 1.2 Create `DepartmentRepository` with methods `findByDeptName(String)`, `findByPath(String)`, and path prefix queries
- [x] 1.3 Update `MapTableConstants` (add `DEPARTMENT_USER = "t_department_user_map"`), update `User` entity to reference `Department`
- [x] 1.4 Update `NaturalKeys` with `requireDeptName(Department)`

## 2. Manager and DTO Layer

- [x] 2.1 Create `DepartmentTreeNode` DTO for hierarchical tree representation
- [x] 2.2 Create `OrgMgr` interface defining Department CRUD, user membership, tree queries, and user read methods
- [x] 2.3 Implement `OrgMgrImpl` with `createDepartment` path validation (extract `pPath`, verify parent existence, calculate `level`)
- [x] 2.4 Implement tree building (`readDepartmentTree`, `readDepartmentSubTree`) and user queries (`readDepartmentUsers`, `readDepartmentSuccessorUsers`)
- [x] 2.5 Update `AdminMgrImpl` and Spring AutoConfiguration imports to use `Department` and `OrgMgr`

## 3. Testing and Verification

- [x] 3.1 Create `OrgMgrImplTest` covering root creation, child creation with parent path check, path validation errors, membership management, tree queries, and direct/successor user reads
- [x] 3.2 Run `./gradlew check` to verify all tests and build pass
