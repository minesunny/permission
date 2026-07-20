# Gradle 改造 + 自然键身份模型重构

日期：2026-07-20
范围：整个 `permission` 仓库（根 + `permission-core` 模块）

## 目标

1. 把 Maven 多模块项目改造成 Gradle（Kotlin DSL）多模块项目。
2. 去除业务代码中对代理主键 `id` 的判空（`Objects.nonNull/isNull(x.getId())`），改用自然键
   （`username` / `roleName` / `permName` / `postName` / `orgName`）作为实体的身份标识。
3. `readUser` / `readRole` / `readPermission` / `readPost` / `readOrgUnit` 内部按自然键查询。

## 决策（已与作者确认）

| 主题 | 决策 |
| --- | --- |
| Gradle DSL | Kotlin DSL，保留多模块（根 + permission-core） |
| 改造范围 | 全面改用自然键：add / update / delete / assign / grant / bind 等全部方法 |
| 唯一约束 | 自然键加 `@Column(unique = true, nullable = false)`，DB 层强制唯一 |
| Resource 判空 | `roleOperationsOnResource` / `userOperationsOnResource` 中 `resource.getId()` 校验**保留**（Resource 不在自然键范围内，map 查询需要真实 id） |
| Java 版本 | 升级到 **JDK 21**，去掉 `--enable-preview`（代码未使用预览特性，已 grep 确认） |

## A. Gradle 改造

### 删除
- 根 `pom.xml`、`permission-core/pom.xml`
- Maven wrapper：`permission-core/mvnw`、`permission-core/mvnw.cmd`、`permission-core/.mvn/**`
- `permission-core/HELP.md`（IDE 生成的 Maven 说明，可选删除）

### 新增
- `settings.gradle.kts`
  - `rootProject.name = "permission"`
  - `include("permission-core")`
- 根 `build.gradle.kts`（`subprojects {}` 统一配置）
  - 插件：`java`、`io.spring.dependency-management`
  - `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }`，或 `sourceCompatibility/targetCompatibility = 21`
  - `repositories { mavenLocal(); mavenCentral() }`
- `gradle.properties`
  - `org.gradle.jvmargs=-Xmx2g`
  - 集中版本：`springBootVersion=3.1.1`、`lombokVersion=1.18.26`、`commonsTextVersion=1.10.0`、`h2Version=2.2.220`、`postgresqlVersion=42.6.1`、`slf4jVersion=2.0.7`、`jetbrainsAnnotationsVersion=24.0.1`、`uidGeneratorVersion=1.0.3.RELEASE`、`fortressVersion=2.0.8`
- `permission-core/build.gradle.kts`
  - `dependencyManagement { imports { mavenBom("org.springframework.boot:spring-boot-dependencies:${springBootVersion}") } }`
  - 依赖（搬运自 `permission-core/pom.xml`）：
    - `org.springframework.boot:spring-boot-starter-data-jpa`
    - `org.springframework.boot:spring-boot-starter-data-jdbc`
    - `org.springframework.boot:spring-boot-starter-test`（test）
    - `com.h2database:h2`（test）
    - `org.projectlombok:lombok`（optional / provided，配 `annotationProcessor`）
    - `org.jetbrains:annotations`（optional）
    - `org.apache.commons:commons-text`
    - `junit:junit`（test）
    - `com.github.wujun234:uid-generator-spring-boot-starter:${uidGeneratorVersion}`
    - `org.apache.directory.fortress:fortress-realm-impl:${fortressVersion}`
    - `com.minesunny.jpa:base-jpa`（`compileOnly`，等价原 `provided`；版本走 mavenLocal 的 1.0-SNAPSHOT）
  - `base-jpa` 走 `mavenLocal()`（本地有 `1.0-SNAPSHOT`）
- Gradle Wrapper：用 Gradle 8.7 生成（兼容 JDK 21 + Spring Boot 3.1）
  - `gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.properties`、`gradle/wrapper/gradle-wrapper.jar`

### `.gitignore`
已忽略 `build/`、`target/`，无需改动；Gradle wrapper jar 不会被忽略。

## B. 实体自然键

| 实体 | 字段 | 改动 |
| --- | --- | --- |
| `User` | `username` | **新增** `@Column(name = "username", unique = true, nullable = false)` + `@Comment` |
| `Role` | `roleName` | 补 `unique = true, nullable = false` |
| `Permission` | `permName` | 补 `unique = true, nullable = false` |
| `Post` | `postName` | 补 `unique = true, nullable = false` |
| `OrgUnit` | `orgName` | 补 `unique = true, nullable = false` |

> `username`/`roleName` 等已在 builder 体系内（`@SuperBuilder`），builder 自动支持。

## C. Repository 自然键查询

新增方法（Spring Data 方法名派生）：

```java
// UserRepository
Optional<User> findByUsername(String username);

// RoleRepository
Optional<Role> findByRoleName(String roleName);

// PermissionRepository
Optional<Permission> findByPermName(String permName);

// PostRepository
Optional<Post> findByPostName(String postName);

// OrgRepository
Optional<OrgUnit> findByOrgName(String orgName);
```

`UserRepository` 现有 `findByIdAndRoles_Id`、`findByIdIn` 保留不动。

## D. Manager 改造（删除全部 `Objects.*Null(x.getId())`）

通用规则：所有身份判定改走自然键；`id` 仅作为内部主键，业务代码不再对 `getId()` 判空。

### ReviewMgrImpl
- `readUser`：`StringUtils.hasText(user.getUsername())` 校验 → `userRepository.findByUsername(...)`
- `readRole`：校验 `roleName` → `roleRepository.findByRoleName(...)`
- `readPermission`：校验 `permName` → `permissionRepository.findByPermName(...)`
- `roleOperationsOnResource` / `userOperationsOnResource`：**保留** `resource.getId()` 校验（例外，见决策表）
- 其余方法（`rolePermissions` / `assignedRoles` 等）调用上面三个 read 即可，自然受益

### AdminMgrImpl（注入的 repository 不变，不引入 ReviewMgr）
- `addUser`：要求 `username` 非空；`findByUsername` 命中则抛「已存在」；`BeanUtils.copyProperties` 后保存
- `updateUser`：`findByUsername` 取持久对象（含 id），不存在则抛；复制时忽略 `id`、`username`、关联字段；保存
- `deleteUser`：`findByUsername` 取持久对象；用其 `id` 删除 `ORG_UNIT_USER` / `ROLE_USER` / `POST_USR` map；`delete`
- `addRole` / `updateRole` / `deleteRole`：同上，用 `roleName`
- `addPermission` / `updatePermission` / `deletePermission`：同上，用 `permName`（保留 resource 类型一致性校验）
- `assignUser` / `deassignUser`：按 `username`、`roleName` 解析出 `userId`、`roleId`；`existsById` 校验后做 map 操作
- `grantPermission` / `revokePermission`：按 `permName`、`roleName` 解析出 id 后做 map 操作

### OrgUnitMgrImpl
- `addPost` / `updatePost` / `deletePost` / `readPost`：用 `postName`
- `addOrgUnit` / `updateOrgUnit` / `deleteOrgUnit` / `readOrgUnit`：用 `orgName`
- `bindPost` / `unbindPost`：按 `postName`、`username` 解析 id（user 仍走 `reviewMgr.readUser`）
- `bindOrgUnit` / `unbindOrgUnit` / `bindManagerUser` / `unbindManagerUser`：按 `username`、`orgName` 解析 id
- `bindOrgUnitNode`（例外）：`OrgSdtNode.nodeId` 指向 orgUnit id；用 `orgRepository.findById(nodeId)` 直接校验存在（不经过已改为 orgName 的 `readOrgUnit`）；保留 `nodeId` 非空校验
- `unbindOrgUnitNode`：逻辑不变（基于 sdt node 自身 id）

> 顺带修掉现存 bug（不属于本次要求但影响测试）：`bindPost`/`unbindPost`/`bindOrgUnit`/`unbindOrgUnit`/`bindManagerUser`/`unbindManagerUser` 里 map 的 `user_id` 误填成了 `orgUnit.getId()`/`post.getId()`，改成解析出的真实 `userId`。

## E. 常量 & 测试

### `RBACEntityConstants`
- `NOT_EXIST_USER = User.builder().username("notExistUser").build()`（同理 `roleName`/`permName`/`postName`/`orgName` 各取不存在的值）
- `USER` / `ROLE` / `PERMISSION` / `POST` / `ORG_UNIT`：保留为空对象，触发「自然键必填」校验
- `NOT_EXIST_*` 不再用 `id(-1L)`

### 测试改造（三个测试类）
- `ReviewMgrImplTest` / `AdminMgrImplTest` / `OrgUnitMgrImplTest`
- 因 `@Commit` + `@TestInstance(PER_CLASS)`，同测试类内数据累积，自然键必须**全类唯一**
- `addUser(new User())` → `addUser(User.builder().username("<unique>").build())`，role/permission/post/orgUnit 同理
- 断言语义保持；`readUser(savedUser)` 仍成立（savedUser 带回 username）

## F. 验证

- `./gradlew :permission-core:test` 全绿
- `./gradlew build` 通过
- 仓库内不再出现 `Objects.nonNull(` / `Objects.isNull(` 配合 `getId()` 的写法（Resource 例外除外）

## 风险与备注

- `base-jpa` 是 `com.minesunny.jpa:base-jpa:1.0-SNAPSHOT`，仅存在于本地 m2；构建依赖 `mavenLocal()`，换机器需先安装。
- `fortress-realm-impl` 与 `uid-generator-spring-boot-starter` 来自 Maven Central / JitPack（原 pom 直接用版本号），保持不变。
- 不改 `application.properties`、`PermissionAutoConfiguration`、`Auth`/`Session`/`Operation`/`Resource`/`OrgSdtNode` 等非身份实体。
