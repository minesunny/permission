## Why

当前租户与组织架构设计依赖较为繁重的 `OrgUnit` 与 `OrgSdtNode`（外部 SDT 闭包表和节点表），同时存在 `Post`（岗位）概念。在实际租户与企业组织层级管理中，基于 SDT 闭包表维护层级关系不仅操作复杂、依赖外部组件，而且在查询树与父子关系时不够直观。通过将 `Post` 重构改名为 `Department`（部门），并在 `Department` 实体中原生引入物化路径（`path`）与层级深度（`level`），可以使组织架构树的校验、查询（整树与子树）以及直属与后继用户查询更加轻量、直观且高效。

## What Changes

- **BREAKING**: 将实体 `Post` 重命名为 `Department`（映射表 `t_department`，鉴别值 `department`），移除原有岗位概念。
- **BREAKING**: 在 `Department` 实体中增加 `path`（存储部门 ID 路径，如 `/1/2/5`）和 `level`（节点层级深度，根节点为 1）。
- **BREAKING**: 重构组织管理接口 `OrgUnitMgr` 为 `OrgMgr`，清理原有的 `Post` 及 `OrgUnit`/SDT 相关关联，并建立规范的部门 CRUD 与树管理方法：
  - `createDepartment(Department department)`：对 `path` 进行结构与父路径（`pPath`）有效性校验，自动维护 `level`。
  - `updateDepartment(String deptName, Department department)`：更新部门信息。
  - `deleteDepartment(Department department)`：删除部门并清理关联关系。
  - `readDepartment(Department department)` 与 `readDepartments(Department department)`：按条件查询部门。
  - `readDepartmentTree()`：查询全量组织架构树（返回 `List<DepartmentTreeNode>`）。
  - `readDepartmentSubTree(Department department)`：查询以指定部门为根的子树。
  - `addUser(Department department, User user)` 与 `removeUser(Department department, User user)`：管理部门与已有用户的直属映射。
  - `readDepartmentUsers(Long departmentId)`：查询指定部门的直属用户列表。
  - `readDepartmentSuccessorUsers(Long departmentId)`：查询指定部门及其所有后继下级部门的聚合用户列表（包含直接用户）。
- 更新 `Actor` 与 `User` 对 `Department` 的映射关系，将 `t_post_user_map` 迁移/重命名为 `t_department_user_map`。

## Capabilities

### New Capabilities
- `department-org-tree`: 部门组织架构树管理能力，包含基于物化路径的层级校验、整树/子树构建、用户直接与后继关联关系查询。

### Modified Capabilities
<!-- None -->

## Impact

- **API 变更**：`OrgUnitMgr` 重命名为 `OrgMgr`，废弃 `addPost`/`deletePost`/`bindPost`/`unbindPost` 以及 `bindOrgUnitNode` 等 SDT 接口，替换为 `createDepartment`, `addUser`, `removeUser`, `readDepartmentTree`, `readDepartmentUsers`, `readDepartmentSuccessorUsers` 等。
- **实体与数据表变更**：新增/重命名 `t_department` 表，新增 `t_department_user_map`。更新 `User` 和 `AdminMgrImpl` 中对 `Post` 的引用。
- **单元测试**：重构 `OrgUnitMgrImplTest` 为 `OrgMgrImplTest`，全面覆盖路径校验、父节点查询、树构建、直接与后继用户读取逻辑。
