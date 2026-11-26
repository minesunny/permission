## Context

当前项目中，组织架构管理由 `OrgUnitMgr` 负责，实体包含 `OrgUnit`（组织单元）和 `Post`（岗位）。
在层级维护上，之前采用 `minesunny.jpa` 的 `SdtNodeMgr` 与 `OrgSdtNode`（映射 `t_org_sdt` 闭包表）来维系树节点绑定关系，用户关联则分散在 `t_post_user_map`、`t_org_unit_user_map` 以及 `t_user_org_unit_map` 中。
这种设计存在以下问题：
1. **概念冗余**：岗位（Post）与部门（Department）混淆，业务上缺乏真正的“部门”树概念。
2. **架构依赖重**：组织树强依赖 SDT 闭包表和 `nodeMgr`，增删改查逻辑繁琐且查询开销大。
3. **接口不规范**：用户与组织、岗位的关联存在多套方法（`bindPost`, `bindOrgUnit`, `bindManagerUser`），缺乏统一直观的部门成员管理接口。

为简化租户组织架构模型，本次重构将 `Post` 正式更名为 `Department`，采用物化路径（Materialized Path，`path` + `level`）内建层级结构，并提供轻量、直观的树查询与用户直属/后继查询能力。

## Goals / Non-Goals

**Goals:**
- 将实体 `Post` 更名为 `Department`，表更名为 `t_department`，鉴别值设为 `department`。
- 在 `Department` 增加 `path`（格式如 `/1/2/5`）和 `level`（整数层级深度，根节点为 1）。
- 实现 `createDepartment` 路径校验逻辑：截取最后一个斜杠前的部分作为 `pPath`，检查父路径对应部门是否存在；若是根部门（无 `pPath`），则允许直接创建且 level 设为 1；子部门 level 设为父部门 level + 1。
- 将 `OrgUnitMgr` 重命名为 `OrgMgr`，清理废弃的 SDT / `OrgUnit` 树方法，提供标准部门 CRUD。
- 提供用户关联管理：`addUser(Department, User)` 与 `removeUser(Department, User)`，基于关联表 `t_department_user_map`。
- 提供高效组织树查询：`readDepartmentTree()`（全树）与 `readDepartmentSubTree(Department)`（指定子树），采用单次 SQL 查询 + 内存 O(N) 映射组装 `DepartmentTreeNode`。
- 提供基于部门 ID 的用户查询方法：
  - `readDepartmentUsers(Long departmentId)`：查询直属用户。
  - `readDepartmentSuccessorUsers(Long departmentId)`：查询当前部门及其所有后继子孙部门的汇总去重用户。

**Non-Goals:**
- 不引入外部图数据库或递归 SQL 闭包表（坚持单表前缀匹配 + 内存组装）。
- 不在当前阶段对通用 `User` 表进行非组织相关的结构调整。

## Decisions

### 1. 实体与存储：Post 重命名为 Department
- **选择**：直接将 `Post` 实体重构为 `Department`，对应表为 `t_department`，用户关联表由 `t_post_user_map` 变更为 `t_department_user_map`。
- **替代方案**：保留 `Post` 并新建 `Department`。废弃原因：用户明确指出原有岗位模型不合理，需要重构成 Department 组织树模型。

### 2. 物化路径设计与 pPath 校验
- **选择**：`path` 存储以斜杠分隔的部门 ID 链，例如根部门 `/1`，二级部门 `/1/2`，三级部门 `/1/2/5`。
- **pPath 解析逻辑**：
  - 若 `path` 中无斜杠或只有一个开头的 `/`（如 `/1`），则 `pPath = null`，视为根部门，level 校验为 1。
  - 若 `path` 包含多段（如 `/1/2/5`），截取最后一个 `/` 之前的部分作为 `pPath`（即 `/1/2`）。
  - 通过 `departmentRepository.findByPath(pPath)` 检查父部门是否存在，若不存在抛出 `ValidationException`；若存在，自动设置或校验 `department.level == parent.level + 1`。

### 3. 单次 SQL + 内存哈希组装树
- **选择**：
  - 全树查询：`SELECT * FROM t_department WHERE delete_at IS NULL ORDER BY level ASC, id ASC;`
  - 子树查询：`SELECT * FROM t_department WHERE delete_at IS NULL AND (path = :p OR path LIKE :p || '/%') ORDER BY level ASC, id ASC;`
  - 内存组装：将所有查出部门放入 `Map<String, DepartmentTreeNode>`，通过一次线性循环遍历，若 `pPath` 存在且在 map 中，挂载至父节点 `children`，否则归入 root 列表。时间复杂度为 O(N)。
- **替代方案**：递归查库（N+1 问题，性能差）或前端递归组装。单次 SQL + 内存组装兼具最佳性能与简洁性。

### 4. 部门用户查询传参设计
- **选择**：`readDepartmentUsers(@NotNull Long departmentId)` 和 `readDepartmentSuccessorUsers(@NotNull Long departmentId)` 直接入参 `Long departmentId`。
- **后继用户查询逻辑**：
  - 校验 `departmentId` 对应的部门存在，获取其 `path`。
  - 查询自身及所有后继部门的 ID 列表：`SELECT id FROM t_department WHERE path = :path OR path LIKE CONCAT(:path, '/%')`。
  - 从关联表 `t_department_user_map` 中查询 `department_id IN (:deptIds)` 的所有用户，并在内存/SQL 中执行去重。

## Risks / Trade-offs

- **[部门移动/重命名 Path 级联更新]** → 若后续业务需移动部门（改变父节点），其所有子部门的 `path` 均需同步替换前缀。当前阶段重点在于部门创建校验与读取，后续可增加 `moveDepartment` 工具方法事务级更新。
- **[ID 与 Path 强关联]** → 由于 path 包含部门 ID，创建子部门时入参中的 path 尾段需携带部门 ID。若采用客户端传入 ID 或预生成 ID，需校验 path 尾段与实体 ID 一致。
- **[原关联代码破坏]** → `OrgUnitMgr` 更名为 `OrgMgr`，`Post` 更名为 `Department`，为破坏性重构（BREAKING CHANGE）。通过完备的单测回归保证现有权限体系正常运转。
