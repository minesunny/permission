# 组织架构遗留模型废弃与下线计划（Deprecation Cycle）

> **目标版本**：v2.0.0 彻底移除  
> **生效阶段**：v1.x（标记废弃与引导迁移） → v2.0.0（物理移除与架构瘦身）  
> **核心目标**：彻底剥离旧版 `Post`（岗位）、`OrgUnit`（组织单元）、`OrgSdtNode`（SDT 树节点），以及数据访问层 `PostRepository`、`OrgRepository`，解除对外部库 `minesunny.jpa` 中 `SdtNodeMgr`（SDT 闭包表）的强依赖，完成单租户企业级纯净组织架构模型转型。

---

## 一、废弃与下线资产清单（Deprecation Matrix）

| 资产类型 | 资产名称 | 所在路径 | 替代方案（Replacement） | 预定下线版本 |
| :--- | :--- | :--- | :--- | :--- |
| **实体类** | `Post` | `com.minesunny.permission.core.entity.actor.Post` | `Department`（统一以部门物化路径维护） | **v2.0.0** |
| **实体类** | `OrgUnit` | `com.minesunny.permission.core.entity.actor.OrgUnit` | `Department` | **v2.0.0** |
| **实体类** | `OrgSdtNode` | `com.minesunny.permission.core.entity.sdt.OrgSdtNode` | `Department.path` & `level` | **v2.0.0** |
| **实体字段** | `User.post` | `User.java` 中的 `post` 属性 | `User.departments`（多对多部门） | **v2.0.0** |
| **实体字段** | `User.orgUnits` | `User.java` 中的 `orgUnits` 属性 | `User.departments` | **v2.0.0** |
| **实体字段** | `User.managerOrgUnits` | `User.java` 中的 `managerOrgUnits` 属性 | `Department.leaderUsername` / 业务扩展 | **v2.0.0** |
| **枚举类型** | `OrgType` | `com.minesunny.permission.core.type.OrgType` | 无需使用（部门层级统一由 level 表达） | **v2.0.0** |
| **数据访问** | `PostRepository` | `com.minesunny.permission.core.repository.PostRepository` | `DepartmentRepository` | **v2.0.0** |
| **数据访问** | `OrgRepository` | `com.minesunny.permission.core.repository.OrgRepository` | `DepartmentRepository` | **v2.0.0** |
| **业务接口** | `OrgUnitMgr` | `com.minesunny.permission.core.manager.OrgUnitMgr` | `OrgMgr` | **v2.0.0** |
| **外部依赖** | `SdtNodeMgr` | `com.minesunny.jpa.manager.SdtNodeMgr` | 内存 O(N) 构建算法（完全解耦外部 SDT） | **v2.0.0** |
| **物理表** | `t_post`, `t_post_user_map` | 数据库表 | `t_department`, `t_department_user_map` | **v2.0.0** |
| **物理表** | `t_org_unit`, `t_org_unit_user_map`, `t_user_org_unit_map` | 数据库表 | `t_department`, `t_department_user_map` | **v2.0.0** |
| **物理表** | `t_org_sdt` | SDT 闭包表 | 废弃，不再需要闭包表 | **v2.0.0** |

---

## 二、废弃生命周期三阶段规划（Three-Stage Deprecation Lifecycle）

```
┌───────────────────────────────┐
│ Phase 1: 当前版本 (v1.x)       │ ──> 显式标注 @Deprecated(forRemoval=true)
│ 标注文档与过渡兼容              │ ──> 保持向后二进制与源码兼容
└──────────────┬────────────────┘
               ▼
┌───────────────────────────────┐
│ Phase 2: 过渡版本 (v1.9)       │ ──> 提供数据平滑迁移 SQL/脚本
│ 迁移脚本与测试解耦              │ ──> 核心业务与单测 100% 切换至新 API
└──────────────┬────────────────┘
               ▼
┌───────────────────────────────┐
│ Phase 3: 主要版本 (v2.0.0)     │ ──> 物理删除废弃类与方法
│ 彻底清理与依赖解耦              │ ──> 移除 SdtNodeMgr 依赖与旧表 DDL
└───────────────────────────────┘
```

### 阶段一：当前版本（v1.x）—— 标注文档与过渡兼容
* **代码规范**：
  * 对所有上述待废弃类、方法和字段，添加统一的标准注解：
    ```java
    @Deprecated(since = "1.1.0", forRemoval = true)
    ```
  * 在 JavaDoc 中明确说明废弃原因、计划移除版本以及对应的替代 API：
    ```java
    /**
     * @deprecated 将在 v2.0.0 中彻底移除。请改用基于物化路径的部门管理接口 {@link OrgMgr}。
     */
    ```
* **运行时保证**：
  * 保持既有代码在编译与执行时的平滑过渡，不产生阻断性错误。
  * `OrgMgrImpl` 继续保留兼容方法的代理实现，保障已发布第三方调用的稳定性。

### 阶段二：过渡版本（v1.9）—— 迁移方案与测试解耦
* **测试套件解耦**：
  * 将核心测试用例从 `OrgUnitMgrImplTest` 完全迁移至 `OrgMgrImplTest`。
  * 将 `OrgUnitMgrImplTest` 隔离归类至 `legacy` 测试包，仅做回归验证。
* **数据迁移方案（Data Migration Script）**：
  * 针对生产环境中已存在 `t_post` 或 `t_org_unit` 数据的项目，提供自动化数据迁移 SQL 模板：
    ```sql
    -- 1. 将原岗位/组织单元数据合并迁移至 t_department
    INSERT INTO t_department (id, dept_name, path, level, description, create_at, update_at, version)
    SELECT id, post_name, CONCAT('/', id), 1, description, create_at, update_at, version
    FROM t_post
    WHERE delete_at IS NULL;

    -- 2. 将原用户关联关系同步至 t_department_user_map
    INSERT INTO t_department_user_map (department_id, user_id)
    SELECT post_id, user_id FROM t_post_user_map;
    ```

### 阶段三：目标主要版本（v2.0.0）—— 物理清理与瘦身
* **物理删除代码文件**：
  * 删除 `Post.java`、`OrgUnit.java`、`OrgSdtNode.java`、`OrgType.java`。
  * 删除 `PostRepository.java`、`OrgRepository.java`。
  * 删除 `OrgUnitMgr.java`，`OrgMgrImpl` 不再实现 `OrgUnitMgr`。
* **依赖彻底解耦**：
  * 从 `OrgMgrImpl` 构造器中移除 `SdtNodeMgr nodeMgr`、`OrgRepository`、`PostRepository` 注入。
  * `AdminMgrImpl` 中清理掉对 `Post`、`OrgUnit` 的 instanceof 分支与 map 清理逻辑。
  * `MapTableConstants` 移除 `POST_USER`、`ORG_UNIT_USER`、`USER_ORG_UNIT`。
* **数据库清理**：
  * 执行 `DROP TABLE IF EXISTS t_post, t_post_user_map, t_org_unit, t_org_unit_user_map, t_user_org_unit_map, t_org_sdt;`

---

## 三、检查清单与实施任务（Task Checklist）

- [x] **Step 1: 制定并归档废弃计划文档** (`docs/superpowers/plans/2026-11-19-deprecation-cycle-post-and-sdt.md`)
- [x] **Step 2: 执行物理清理与架构瘦身（Stage 3 彻底解耦与移除）**
  - [x] 物理删除 `Post.java`、`OrgUnit.java`、`OrgSdtNode.java`、`OrgType.java`
  - [x] 物理删除 `PostRepository.java`、`OrgRepository.java`
  - [x] 物理删除 `OrgUnitMgr.java` 与遗留单元测试 `OrgUnitMgrImplTest.java`
  - [x] 清理 `User.java` 中的 `post`, `orgUnits`, `managerOrgUnits` 字段与关联映射
  - [x] 清理 `AdminMgrImpl.java` 中对 `Post`, `OrgUnit`, `POST_USER`, `ORG_UNIT_USER` 的依赖
  - [x] 清理 `OrgMgrImpl.java` 中对 `OrgUnitMgr` 接口实现、`nodeMgr` (SdtNodeMgr)、`orgRepository`、`postRepository` 的注入及所有遗留方法
  - [x] 清理 `MapTableConstants.java` 中的 `POST_USER`, `ORG_UNIT_USER`, `USER_ORG_UNIT` 常量
  - [x] 清理 `NaturalKeys.java` 中的 `requirePostName`, `requireOrgName` 方法
  - [x] 清理 `RBACEntityConstants.java` 中的 `OrgUnit`, `Post` 相关测试常量
- [x] **Step 3: 验证构建与测试套件**
  - [x] 运行 `./gradlew compileJava` 验证编译零错误
  - [x] 运行 `./gradlew test --rerun-tasks` 验证 38 个核心测试用例 100% 通过
  - [x] 运行 `./gradlew javadoc` 验证符合 JavaDoc 规范
  - [x] 运行 `./gradlew clean check` 全任务构建成功
- [x] **Step 4: 完成单租户企业级纯净组织架构模型转型**
