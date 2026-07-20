# Gradle 改造 + 自然键身份模型重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Maven 多模块项目改造成 Gradle(Kotlin DSL)，并去除业务代码中对代理主键 `id` 的判空，改用自然键 (`username`/`roleName`/`permName`/`postName`/`orgName`) 作为实体身份。

**Architecture:** 单子模块 (`permission-core`)，Gradle Kotlin DSL；身份判定全部走自然键，`id` 退化为内部主键；自然键加 DB 唯一+非空约束。测试是行为的规格，每个 task 结束时全套测试必须绿。

**Tech Stack:** Java 21（去掉 `--enable-preview`）、Spring Boot 3.1.1（BOM）、Spring Data JPA、Lombok、H2(test)、Gradle 8.7、本地 `com.minesunny.jpa:base-jpa`。

## Global Constraints

- 编译/运行 JDK：**21**（本机已装 Zulu 21）。Gradle 版本：**8.7**。
- DSL：**Kotlin DSL**（`*.gradle.kts`）。保留多模块：根 + `permission-core`。
- `base-jpa` 走 `mavenLocal()`，坐标 `com.minesunny.jpa:base-jpa:1.0-SNAPSHOT`；main 侧 `compileOnly`，test 侧 `testImplementation`（对等原 Maven `provided`）。
- 自然键最终约束：`@Column(unique = true, nullable = false)`。
- **禁止**业务代码里出现 `Objects.nonNull(x.getId())` / `Objects.isNull(x.getId())` 形态（`Resource`/`OrgSdtNode.nodeId` 两个例外除外，见 Task 4/5）。
- 测试因 `@Commit` + `@TestInstance(PER_CLASS)` + 跨类共享 ApplicationContext，**自然键必须在所有测试类范围内全局唯一**（用前缀 `rev-`/`adm-`/`org-` 区分）。
- 文件路径均相对仓库根 `/Users/maien/mine-query/permission`。
- 验证命令统一用 `./gradlew`（wrapper）。

---

## File Structure

**Gradle（新增/删除）：**
- Create: `settings.gradle.kts`、`build.gradle.kts`（根）、`gradle.properties`、`gradle/wrapper/*`、`gradlew`、`gradlew.bat`
- Create: `permission-core/build.gradle.kts`
- Delete: `pom.xml`、`permission-core/pom.xml`、`permission-core/mvnw`、`permission-core/mvnw.cmd`、`permission-core/.mvn/**`、`permission-core/HELP.md`

**实体（自然键）：**
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/entity/actor/User.java`（新增 `username`）
- Modify: `Role.java`、`perm/Permission.java`、`actor/Post.java`、`actor/OrgUnit.java`（补 unique+nullable）

**Repository：**
- Modify: `UserRepository`/`RoleRepository`/`PermissionRepository`/`PostRepository`/`OrgRepository`（各加一个 `findByX`）

**Manager：**
- Modify: `manager/impl/ReviewMgrImpl.java`（3 个 read 方法）
- Modify: `manager/impl/AdminMgrImpl.java`（全量重写）
- Modify: `manager/impl/OrgUnitMgrImpl.java`（全量重写）

**常量 & 测试：**
- Modify: `type/RBACEntityConstants.java`
- Modify: 三个测试类

---

## Task 1: Gradle 改造（基线：旧代码 + 旧测试在 JDK21/Gradle 下通过）

**Files:**
- Create: `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`permission-core/build.gradle.kts`、Gradle wrapper
- Delete: `pom.xml`、`permission-core/pom.xml`、`permission-core/mvnw`、`permission-core/mvnw.cmd`、`permission-core/.mvn/**`、`permission-core/HELP.md`

**Interfaces:**
- Consumes: 无（基线）
- Produces: 可运行的 `./gradlew`（后续所有 task 的验证入口）

- [ ] **Step 1: 删除 Maven 文件**

```bash
cd /Users/maien/mine-query/permission
git rm pom.xml permission-core/pom.xml
git rm permission-core/mvnw permission-core/mvnw.cmd
git rm -r permission-core/.mvn 2>/dev/null || true
git rm permission-core/HELP.md 2>/dev/null || true
rm -rf permission-core/.mvn permission-core/HELP.md
```

- [ ] **Step 2: 写 `settings.gradle.kts`**

```kotlin
rootProject.name = "permission"

include("permission-core")
```

- [ ] **Step 3: 写 `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2g
org.gradle.caching=true

springBootVersion=3.1.1
dependencyManagementVersion=1.1.6
lombokVersion=1.18.26
commonsTextVersion=1.10.0
h2Version=2.2.220
postgresqlVersion=42.6.1
slf4jVersion=2.0.7
jetbrainsAnnotationsVersion=24.0.1
fortressCoreVersion=2.0.8
baseJpaVersion=1.0-SNAPSHOT
```

- [ ] **Step 4: 写根 `build.gradle.kts`**

```kotlin
plugins {
    java
    id("io.spring.dependency-management") version "1.1.6" apply false
}
```

- [ ] **Step 5: 写 `permission-core/build.gradle.kts`**

```kotlin
plugins {
    java
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.minesunny.permission"
version = "0.0.2"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.apache.commons:commons-text:${property("commonsTextVersion")}")
    implementation("org.apache.directory.fortress:fortress-core:${property("fortressCoreVersion")}")

    compileOnly("com.minesunny.jpa:base-jpa:${property("baseJpaVersion")}")
    compileOnly("org.jetbrains:annotations:${property("jetbrainsAnnotationsVersion")}")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
    testImplementation("junit:junit")
    testImplementation("com.minesunny.jpa:base-jpa:${property("baseJpaVersion")}")
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 6: 生成 Gradle Wrapper（8.7）**

若机器上无 `gradle`，下载发行版引导一次：

```bash
cd /Users/maien/mine-query/permission
if ! command -v gradle >/dev/null 2>&1; then
  curl -fsSL -o /tmp/gradle-8.7-bin.zip https://services.gradle.org/distributions/gradle-8.7-bin.zip
  unzip -q /tmp/gradle-8.7-bin.zip -d /tmp
  /tmp/gradle-8.7/bin/gradle wrapper --gradle-version 8.7 --distribution-type bin
  rm -rf /tmp/gradle-8.7 /tmp/gradle-8.7-bin.zip
else
  gradle wrapper --gradle-version 8.7 --distribution-type bin
fi
./gradlew --version
```

Expected: 打印 `Gradle 8.7` 且 `JVM: 21`。

- [ ] **Step 7: 跑现有测试，确认基线绿**

```bash
cd /Users/maien/mine-query/permission
./gradlew :permission-core:test
```

Expected: `BUILD SUCCESSFUL`，三个测试类全过（旧代码、旧 id 判空逻辑、JDK21 编译通过）。

- [ ] **Step 8: 提交**

```bash
git add -A
git commit -m "build: convert maven multi-module project to gradle (kotlin dsl, jdk 21)"
```

---

## Task 2: 实体加 `username` 字段 + Repository 自然键查询（纯新增，保持绿）

**Files:**
- Modify: `permission-core/src/main/java/com/minesunny/permission/core/entity/actor/User.java`
- Modify: 5 个 Repository 文件

**Interfaces:**
- Consumes: Task 1 的 `./gradlew`
- Produces: `User.getUsername()`、`UserRepository.findByUsername(String)`、`RoleRepository.findByRoleName(String)`、`PermissionRepository.findByPermName(String)`、`PostRepository.findByPostName(String)`、`OrgRepository.findByOrgName(String)`

> 本 task 的 `username` 字段**先不加** unique/nullable 约束（避免立刻打破旧测试）；Task 6 统一补约束。

- [ ] **Step 1: `User.java` 新增 `username` 字段**

在 `public class User extends CachedIdEntity {` 之后、`description` 之前插入：

```java
    @Column(name = "username")
    @Comment(value = "user login name, unique")
    private String username;
```

- [ ] **Step 2: `UserRepository` 加 `findByUsername`**

在 `permission-core/src/main/java/com/minesunny/permission/core/repository/UserRepository.java` 接口体内新增：

```java
    Optional<User> findByUsername(String username);
```

- [ ] **Step 3: 其余 4 个 Repository 各加一个 finder**

`RoleRepository`：
```java
    Optional<Role> findByRoleName(String roleName);
```
`PermissionRepository`：
```java
    Optional<Permission> findByPermName(String permName);
```
`PostRepository`：
```java
    Optional<Post> findByPostName(String postName);
```
`OrgRepository`：
```java
    Optional<OrgUnit> findByOrgName(String orgName);
```
（`Optional` 已 import 或在各自文件按需补 `import java.util.Optional;`。）

- [ ] **Step 4: 编译 + 测试**

```bash
cd /Users/maien/mine-query/permission
./gradlew :permission-core:test
```

Expected: `BUILD SUCCESSFUL`（纯新增，旧测试不受影响）。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: add username field and natural-key finders on repositories"
```

---

## Task 3: ReviewMgrImpl + AdminMgrImpl 改用自然键（User/Role/Permission 身份）

**Files:**
- Modify: `manager/impl/ReviewMgrImpl.java`
- Modify: `manager/impl/AdminMgrImpl.java`
- Modify: `type/RBACEntityConstants.java`
- Modify: `test/.../ReviewMgrImplTest.java`、`test/.../AdminMgrImplTest.java`
- Modify: `test/.../OrgUnitMgrImplTest.java`（仅 bindPost/unbindPost 的 user 加 username，避免 Task 3 后变红；Task 5 再全量重写）

**Interfaces:**
- Consumes: Task 2 的 `findByUsername/findByRoleName/findByPermName`
- Produces: `AdminMgr` 的 add/update/delete/assign/grant 全部按自然键工作；`readUser/readRole/readPermission` 按自然键查询

> 身份规则：`username`/`roleName`/`permName` 必填（缺失抛 `ValidationException`）；重复 add 抛「已存在」；update/delete/assign/grant 先按自然键解析出持久对象拿到 `id`，再做后续操作。`Resource` 的 id 判空**保留**。

- [ ] **Step 1: 改 `ReviewMgrImpl` 的三个 read 方法**

文件 `manager/impl/ReviewMgrImpl.java`：

新增 import：
```java
import org.springframework.util.StringUtils;
```

替换 `readUser`：
```java
    @Override
    public Optional<User> readUser(@NotNull User user) throws ServiceException {
        if (!StringUtils.hasText(user.getUsername())) {
            throw new ValidationException("[readUser]:    The RBAC user's username is required and cannot be null");
        }
        return userRepository.findByUsername(user.getUsername());
    }
```

替换 `readRole`：
```java
    @Override
    public Optional<Role> readRole(@NotNull Role role) throws ServiceException {
        if (!StringUtils.hasText(role.getRoleName())) {
            throw new ValidationException("[readRole]:    The RBAC role's roleName is required and cannot be null");
        }
        return roleRepository.findByRoleName(role.getRoleName());
    }
```

替换 `readPermission`：
```java
    @Override
    public Optional<Permission> readPermission(@NotNull Permission permission) throws ServiceException {
        if (!StringUtils.hasText(permission.getPermName())) {
            throw new ValidationException("[readPermission]:    The RBAC permission's permName is required and cannot be null");
        }
        return permissionRepository.findByPermName(permission.getPermName());
    }
```

`roleOperationsOnResource` / `userOperationsOnResource` 中 `resource.getId()` 校验**保留不动**；其余方法不动。

- [ ] **Step 2: 全量重写 `AdminMgrImpl`**

把 `manager/impl/AdminMgrImpl.java` 整文件替换为：

```java
package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.jpa.manager.MapMgr;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.entity.perm.Resource;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.repository.PermissionRepository;
import com.minesunny.permission.core.repository.RoleRepository;
import com.minesunny.permission.core.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.minesunny.permission.core.type.MapTableConstants.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMgrImpl implements AdminMgr {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MapMgr mapMgr;

    @Override
    public @NotNull User addUser(@NotNull User user) throws ServiceException {
        requireUsername(user);
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new ValidationException("[addUser]:  The RBAC user entity [username: %s ] already exists".formatted(user.getUsername()));
        }
        User newUser = new User();
        BeanUtils.copyProperties(user, newUser, "roles", "post", "managerOrgUnits", "orgUnits");
        return userRepository.saveAndFlush(newUser);
    }

    @Override
    public @NotNull User updateUser(@NotNull User user) throws ServiceException {
        requireUsername(user);
        User storeUser = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new ValidationException("[updateUser]:    The RBAC user entity [username: %s] does not exist".formatted(user.getUsername())));
        BeanUtils.copyProperties(user, storeUser, "id", "username", "roles", "post", "managerOrgUnits", "orgUnits");
        return userRepository.saveAndFlush(storeUser);
    }

    @Override
    @Transactional
    public void deleteUser(@NotNull User user) throws ServiceException {
        requireUsername(user);
        User storeUser = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new ValidationException("[deleteUser]:    The RBAC user entity [username: %s] does not exist".formatted(user.getUsername())));
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(storeUser.getId()));
        mapMgr.delete(ORG_UNIT_USER, map);
        mapMgr.delete(ROLE_USER, map);
        mapMgr.delete(POST_USR, map);
        userRepository.delete(storeUser);
    }

    @Override
    public @NotNull Role addRole(@NotNull Role role) throws ServiceException {
        requireRoleName(role);
        if (roleRepository.findByRoleName(role.getRoleName()).isPresent()) {
            throw new ValidationException("[addRole]:    The RBAC role entity [roleName: %s ] already exists".formatted(role.getRoleName()));
        }
        Role newRole = new Role();
        BeanUtils.copyProperties(role, newRole, "permissions", "users");
        return roleRepository.saveAndFlush(newRole);
    }

    @Override
    public @NotNull Role updateRole(@NotNull Role role) throws ServiceException {
        requireRoleName(role);
        Role storeRole = roleRepository.findByRoleName(role.getRoleName())
                .orElseThrow(() -> new ValidationException("[updateRole]:    The RBAC role entity [roleName: %s] does not exist".formatted(role.getRoleName())));
        BeanUtils.copyProperties(role, storeRole, "id", "roleName", "users", "permissions");
        return roleRepository.saveAndFlush(storeRole);
    }

    @Override
    @Transactional
    public void deleteRole(@NotNull Role role) throws ServiceException {
        requireRoleName(role);
        Role storeRole = roleRepository.findByRoleName(role.getRoleName())
                .orElseThrow(() -> new ValidationException("[deleteRole]:    The RBAC role entity [roleName: %s] does not exist".formatted(role.getRoleName())));
        roleRepository.delete(storeRole);
    }

    @Override
    public void assignUser(@NotNull User user, @NotNull Role role) throws ServiceException {
        UserRoleIds ids = resolveUserRole(user, role, "assignUser");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(ids.userId()));
        map.put("role_id", List.of(ids.roleId()));
        if (mapMgr.exist(ROLE_USER, map)) {
            throw new ValidationException("[assignUser]:    The RBAC role[id: %s] entity has assigned to user[id: %s] entity".formatted(ids.roleId(), ids.userId()));
        }
        mapMgr.save(ROLE_USER, map);
    }

    @Override
    public void deassignUser(@NotNull User user, @NotNull Role role) throws ServiceException {
        UserRoleIds ids = resolveUserRole(user, role, "deassignUser");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(ids.userId()));
        map.put("role_id", List.of(ids.roleId()));
        if (!mapMgr.exist(ROLE_USER, map)) {
            throw new ValidationException("[deassignUser]:    The RBAC role[id: %s] entity has deassigned from user[id: %s] entity".formatted(ids.roleId(), ids.userId()));
        }
        mapMgr.delete(ROLE_USER, map);
    }

    @Override
    public @NotNull Permission addPermission(@NotNull Permission permission) throws ServiceException {
        requirePermName(permission);
        if (permissionRepository.findByPermName(permission.getPermName()).isPresent()) {
            throw new ValidationException("[addPermission]:    The RBAC permission entity [permName: %s ] already exists".formatted(permission.getPermName()));
        }
        validateSingleResourceType(permission.getResources(), "addPermission");
        return permissionRepository.saveAndFlush(permission);
    }

    @Override
    public @NotNull Permission updatePermission(@NotNull Permission permission) throws ServiceException {
        requirePermName(permission);
        Permission storePermission = permissionRepository.findByPermName(permission.getPermName())
                .orElseThrow(() -> new ValidationException("[updatePermission]:    The RBAC permission entity [permName: %s] does not exist".formatted(permission.getPermName())));
        validateSingleResourceType(permission.getResources(), "updatePermission");
        BeanUtils.copyProperties(permission, storePermission, "id", "permName");
        return permissionRepository.saveAndFlush(storePermission);
    }

    @Override
    @Transactional
    public void deletePermission(@NotNull Permission permission) throws ServiceException {
        requirePermName(permission);
        Permission storePermission = permissionRepository.findByPermName(permission.getPermName())
                .orElseThrow(() -> new ValidationException("[deletePermission]:    The RBAC permission entity [permName: %s] does not exist".formatted(permission.getPermName())));
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(storePermission.getId()));
        mapMgr.delete(ROLE_PERMISSION, map);
        permissionRepository.delete(storePermission);
    }

    @Override
    public void grantPermission(@NotNull Permission permission, @NotNull Role role) throws ServiceException {
        PermissionRoleIds ids = resolvePermissionRole(permission, role, "grantPermission");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(ids.permissionId()));
        map.put("role_id", List.of(ids.roleId()));
        if (mapMgr.exist(ROLE_PERMISSION, map)) {
            throw new ValidationException("[grantPermission]:    The RBAC permission[id: %s] entity has grant to role[id: %s] entity".formatted(ids.permissionId(), ids.roleId()));
        }
        mapMgr.save(ROLE_PERMISSION, map);
    }

    @Override
    public void revokePermission(@NotNull Permission permission, @NotNull Role role) throws ServiceException {
        PermissionRoleIds ids = resolvePermissionRole(permission, role, "revokePermission");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(ids.permissionId()));
        map.put("role_id", List.of(ids.roleId()));
        if (!mapMgr.exist(ROLE_PERMISSION, map)) {
            throw new ValidationException("[revokePermission]:    The RBAC permission[id: %s] entity has revoke from role[id: %s] entity".formatted(ids.permissionId(), ids.roleId()));
        }
        mapMgr.delete(ROLE_PERMISSION, map);
    }

    private void validateSingleResourceType(Set<Resource> resources, String op) {
        if (Objects.nonNull(resources) && !resources.isEmpty()) {
            Set<String> resourceNames = resources.stream().map(r -> r.getClass().getSimpleName()).collect(Collectors.toSet());
            if (resourceNames.size() > 1) {
                throw new ValidationException("[%s]: The RBAC permission entity must have a single resource [resource: %s] type set".formatted(op, Arrays.toString(StringUtils.toStringArray(resourceNames))));
            }
        }
    }

    private void requireUsername(User user) {
        if (!StringUtils.hasText(user.getUsername())) {
            throw new ValidationException("The RBAC user entity's username is required and cannot be null");
        }
    }

    private void requireRoleName(Role role) {
        if (!StringUtils.hasText(role.getRoleName())) {
            throw new ValidationException("The RBAC role entity's roleName is required and cannot be null");
        }
    }

    private void requirePermName(Permission permission) {
        if (!StringUtils.hasText(permission.getPermName())) {
            throw new ValidationException("The RBAC permission entity's permName is required and cannot be null");
        }
    }

    private record UserRoleIds(Long userId, Long roleId) {}

    private record PermissionRoleIds(Long permissionId, Long roleId) {}

    private UserRoleIds resolveUserRole(User user, Role role, String op) throws ValidationException {
        requireUsername(user);
        requireRoleName(role);
        User storeUser = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new ValidationException("[%s]:    The RBAC user[username: %s] does not exist".formatted(op, user.getUsername())));
        Role storeRole = roleRepository.findByRoleName(role.getRoleName())
                .orElseThrow(() -> new ValidationException("[%s]:    The RBAC role[roleName: %s] does not exist".formatted(op, role.getRoleName())));
        return new UserRoleIds(storeUser.getId(), storeRole.getId());
    }

    private PermissionRoleIds resolvePermissionRole(Permission permission, Role role, String op) throws ValidationException {
        requirePermName(permission);
        requireRoleName(role);
        Permission storePermission = permissionRepository.findByPermName(permission.getPermName())
                .orElseThrow(() -> new ValidationException("[%s]:    The RBAC permission[permName: %s] does not exist".formatted(op, permission.getPermName())));
        Role storeRole = roleRepository.findByRoleName(role.getRoleName())
                .orElseThrow(() -> new ValidationException("[%s]:    The RBAC role[roleName: %s] does not exist".formatted(op, role.getRoleName())));
        return new PermissionRoleIds(storePermission.getId(), storeRole.getId());
    }
}
```

- [ ] **Step 3: 改 `RBACEntityConstants`（NOT_EXIST_* 改用自然键）**

整文件替换为：

```java
package com.minesunny.permission.core.type;

import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.OrgUnit;
import com.minesunny.permission.core.entity.actor.Post;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Operation;
import com.minesunny.permission.core.entity.perm.Permission;

public interface RBACEntityConstants {

    Role NOT_EXIST_ROLE = Role.builder().roleName("notExistRole").build();

    Role ROLE = new Role();
    User NOT_EXIST_USER = User.builder().username("notExistUser").build();
    User USER = new User();

    OrgUnit NOT_EXIST_ORG_UNIT = OrgUnit.builder().orgName("notExistOrgUnit").build();

    OrgUnit ORG_UNIT = new OrgUnit();


    Permission NOT_EXIST_PERMISSION = Permission.builder().permName("notExistPerm").build();


    Permission PERMISSION = new Permission();


    Post NOT_EXIST_POST = Post.builder().postName("notExistPost").build();

    Post POST = new Post();

    Operation OPERATION = Operation.builder().build();
}
```

- [ ] **Step 4: 全量重写 `ReviewMgrImplTest`（所有 User/Role/Permission 带全类唯一自然键）**

整文件替换 `test/.../manager/impl/ReviewMgrImplTest.java`：

```java
package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.type.RBACEntityConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Commit;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection =
        EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class ReviewMgrImplTest {
    @Autowired
    private AdminMgr adminMgr;
    @Autowired
    private ReviewMgr reviewMgr;

    @Test
    @Commit
    void testReadUser() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readUser(RBACEntityConstants.USER));
        User user = User.builder().username("rev-readUser").description("test readUser").build();

        Optional<User> readUser = reviewMgr.readUser(adminMgr.addUser(user));
        assertTrue(readUser.isPresent());
        assertEquals("test readUser", readUser.get().getDescription());

        user.andEq("description");
        List<User> users = reviewMgr.readUsers(user);
        assertFalse(users.isEmpty());
        assertEquals("test readUser", users.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadUsers() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readUsers(RBACEntityConstants.USER));
        assertDoesNotThrow(() -> adminMgr.addUser(User.builder().username("rev-readUsers-1").description("abcdefg").build()));
        assertDoesNotThrow(() -> adminMgr.addUser(User.builder().username("rev-readUsers-2").description("abc").build()));

        List<User> users = reviewMgr.readUsers(User.builder().description("abc").build().like("description", true));
        assertEquals(2, users.size());
    }

    @Test
    @Commit
    void testReadRole() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readRole(RBACEntityConstants.ROLE));
        Role role = Role.builder().roleName("rev-readRole").description("test testReadRole").build();

        Optional<Role> readRole = reviewMgr.readRole(adminMgr.addRole(role));
        assertTrue(readRole.isPresent());
        assertEquals("test testReadRole", readRole.get().getDescription());

        role.andEq("description");
        List<Role> roles = reviewMgr.readRoles(role);
        assertFalse(roles.isEmpty());
        assertEquals("test testReadRole", roles.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadRoles() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readRoles(RBACEntityConstants.ROLE));
        assertDoesNotThrow(() -> adminMgr.addRole(Role.builder().roleName("rev-readRoles-1").description("abcdefg").build()));
        assertDoesNotThrow(() -> adminMgr.addRole(Role.builder().roleName("rev-readRoles-2").description("abc").build()));

        List<Role> roles = reviewMgr.readRoles(Role.builder().description("abc").build().like("description", true));
        assertEquals(2, roles.size());
    }

    @Test
    @Commit
    void testReadPermission() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readPermission(RBACEntityConstants.PERMISSION));
        Permission permission = Permission.builder().permName("rev-readPerm").description("test testReadPermission").build();

        Optional<Permission> readPermission = reviewMgr.readPermission(adminMgr.addPermission(permission));
        assertTrue(readPermission.isPresent());
        assertEquals(readPermission.get().getDescription(), "test testReadPermission");

        permission.andEq("description");
        List<Permission> permissions = reviewMgr.readPermissions(permission);
        assertFalse(permissions.isEmpty());
        assertEquals("test testReadPermission", permissions.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadPermissions() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readPermission(RBACEntityConstants.PERMISSION));
        assertDoesNotThrow(() -> adminMgr.addPermission(Permission.builder().permName("rev-readPerms-1").description("abcdefg").build()));
        assertDoesNotThrow(() -> adminMgr.addPermission(Permission.builder().permName("rev-readPerms-2").description("abc").build()));

        List<Permission> permissions = reviewMgr.readPermissions(Permission.builder().description("abc").build().like("description", true));
        assertEquals(2, permissions.size());
    }

    @Test
    @Commit
    void testRolePermissions() throws ServiceException {
        assertEquals(0, reviewMgr.rolePermissions(RBACEntityConstants.NOT_EXIST_ROLE).size());

        Role role = adminMgr.addRole(Role.builder().roleName("rev-rolePerms").build());
        Permission p1 = adminMgr.addPermission(Permission.builder().permName("rev-rolePerms-p1").build());
        Permission p2 = adminMgr.addPermission(Permission.builder().permName("rev-rolePerms-p2").build());
        adminMgr.grantPermission(p1, role);
        adminMgr.grantPermission(p2, role);
        List<Permission> permissions = reviewMgr.rolePermissions(role);

        assertTrue(permissions.contains(p1));
        assertTrue(permissions.contains(p2));
    }

    @Test
    @Commit
    void testUserPermissions() throws ServiceException {
        assertEquals(reviewMgr.userPermissions(RBACEntityConstants.NOT_EXIST_USER).size(), 0);

        Role role = adminMgr.addRole(Role.builder().roleName("rev-userPerms-role").build());
        User user = adminMgr.addUser(User.builder().username("rev-userPerms-user").build());
        Permission p1 = adminMgr.addPermission(Permission.builder().permName("rev-userPerms-p1").build());
        Permission p2 = adminMgr.addPermission(Permission.builder().permName("rev-userPerms-p2").build());
        adminMgr.grantPermission(p1, role);
        adminMgr.grantPermission(p2, role);
        adminMgr.assignUser(user, role);

        List<Permission> permissions = reviewMgr.userPermissions(user);

        assertTrue(permissions.contains(p1));
        assertTrue(permissions.contains(p2));
    }

    @Test
    @Commit
    void testAssignedUsers() throws ServiceException {
        assertEquals(reviewMgr.assignedUsers(RBACEntityConstants.NOT_EXIST_ROLE).size(), 0);

        Role role = adminMgr.addRole(Role.builder().roleName("rev-assignedUsers-role").build());
        User user1 = adminMgr.addUser(User.builder().username("rev-assignedUsers-u1").build());
        User user2 = adminMgr.addUser(User.builder().username("rev-assignedUsers-u2").build());

        adminMgr.assignUser(user1, role);
        adminMgr.assignUser(user2, role);

        List<User> users = reviewMgr.assignedUsers(role);
        assertTrue(users.contains(user1));
        assertTrue(users.contains(user2));
    }

    @Test
    @Commit
    void testAssignedRoles() throws ServiceException {
        assertEquals(reviewMgr.assignedRoles(RBACEntityConstants.NOT_EXIST_USER).size(), 0);

        Role role1 = adminMgr.addRole(Role.builder().roleName("rev-assignedRoles-r1").build());
        Role role2 = adminMgr.addRole(Role.builder().roleName("rev-assignedRoles-r2").build());
        User user = adminMgr.addUser(User.builder().username("rev-assignedRoles-user").build());

        adminMgr.assignUser(user, role1);
        adminMgr.assignUser(user, role2);

        List<Role> roles = reviewMgr.assignedRoles(user);
        assertTrue(roles.contains(role1));
        assertTrue(roles.contains(role2));
    }
}
```

- [ ] **Step 5: 全量重写 `AdminMgrImplTest`（带全类唯一自然键）**

整文件替换 `test/.../manager/impl/AdminMgrImplTest.java`：

```java
package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.example.e1.ExampleResource1;
import com.minesunny.permission.core.entity.example.e2.ExampleResource2;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.entity.perm.Resource;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.type.RBACEntityConstants;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Commit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection =
        EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class AdminMgrImplTest {
    @Autowired
    EntityManager entityManager;
    @Autowired
    private AdminMgr adminMgr;
    @Autowired
    private ReviewMgr reviewMgr;

    @Test
    @Commit
    void testAddUser() throws ServiceException {
        User user = User.builder().username("adm-addUser").build();
        User addUser = adminMgr.addUser(user);
        Assertions.assertNotNull(addUser);
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.addUser(addUser));
    }

    @Test
    @Commit
    void testUpdateUser() throws ServiceException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updateUser(RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updateUser(RBACEntityConstants.NOT_EXIST_USER));
        User addUser = adminMgr.addUser(User.builder().username("adm-updateUser").build());
        addUser.setDescription("update user");
        Assertions.assertEquals(adminMgr.updateUser(addUser).getDescription(), "update user");
    }

    @Test
    @Commit
    void testDeleteUser() throws ServiceException {
        User addUser = adminMgr.addUser(User.builder().username("adm-deleteUser").build());
        adminMgr.deleteUser(addUser);
        Assertions.assertTrue(reviewMgr.readUser(addUser).isEmpty());
    }

    @Test
    @Commit
    void testAddRole() throws ServiceException {
        Role role = Role.builder().roleName("adm-addRole").build();
        Role addRole = adminMgr.addRole(role);
        Assertions.assertNotNull(addRole);
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.addRole(addRole));
    }

    @Test
    @Commit
    void testUpdateRole() throws ServiceException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updateRole(RBACEntityConstants.ROLE));
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updateRole(RBACEntityConstants.NOT_EXIST_ROLE));
        Role addRole = adminMgr.addRole(Role.builder().roleName("adm-updateRole").build());
        addRole.setDescription("update role");
        Assertions.assertEquals(adminMgr.updateRole(addRole).getDescription(), "update role");
    }

    @Test
    @Commit
    void testDeleteRole() throws ServiceException {
        Role addRole = adminMgr.addRole(Role.builder().roleName("adm-deleteRole").build());
        adminMgr.deleteRole(addRole);
        Assertions.assertTrue(reviewMgr.readRole(addRole).isEmpty());
    }

    @Test
    @Commit
    void testAssignUser() throws ServiceException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.assignUser(RBACEntityConstants.NOT_EXIST_USER, RBACEntityConstants.NOT_EXIST_ROLE));

        User user = adminMgr.addUser(User.builder().username("adm-assignUser").build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.assignUser(user, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-assignRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.assignUser(user, role));
        List<Role> roles = reviewMgr.assignedRoles(user);
        Assertions.assertTrue(roles.contains(role));

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.assignUser(user, role));
    }

    @Test
    @Commit
    void testDeassignUser() throws ServiceException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.deassignUser(RBACEntityConstants.NOT_EXIST_USER, RBACEntityConstants.NOT_EXIST_ROLE));

        User user = adminMgr.addUser(User.builder().username("adm-deassignUser").build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.deassignUser(user, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-deassignRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.assignUser(user, role));
        List<Role> roles = reviewMgr.assignedRoles(user);
        Assertions.assertTrue(roles.contains(role));

        Assertions.assertDoesNotThrow(() -> adminMgr.deassignUser(user, role));
        roles = reviewMgr.assignedRoles(user);
        Assertions.assertFalse(roles.contains(role));

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.deassignUser(user, role));
    }

    @Test
    @Commit
    void testAddPermission() throws ServiceException {
        Resource resource1 = ExampleResource1.builder().message("this is a ExampleResource1").build();
        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-addPerm1").resources(Set.of(resource1)).build());
        Assertions.assertTrue(reviewMgr.readPermission(permission1).isPresent());

        Resource resource2 = ExampleResource2.builder().message("this is a ExampleResource2").build();
        Permission permission2 = adminMgr.addPermission(Permission.builder().permName("adm-addPerm2").resources(Set.of(resource2)).build());
        Assertions.assertTrue(reviewMgr.readPermission(permission2).isPresent());

        Assertions.assertThrows(ValidationException.class, () -> adminMgr.addPermission(permission1));
        Assertions.assertThrows(ValidationException.class, () -> adminMgr.addPermission(permission2));

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.addPermission(Permission.builder().permName("adm-addPerm3").resources(Set.of(resource1, resource2)).build()));
    }

    @Test
    @Commit
    void updatePermission() throws ServiceException {
        Assertions.assertThrows(ValidationException.class, () -> adminMgr.updatePermission(RBACEntityConstants.PERMISSION));
        Assertions.assertThrows(ValidationException.class, () -> adminMgr.updatePermission(RBACEntityConstants.NOT_EXIST_PERMISSION));

        Resource resource1 = ExampleResource1.builder().message("this is a ExampleResource1").build();
        Set<Resource> objects = new HashSet<>();
        objects.add(resource1);
        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-updatePerm").resources(objects).build());
        Resource resource2 = ExampleResource2.builder().message("this is a ExampleResource2").build();
        permission1.getResources().add(resource2);

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updatePermission(permission1));

        permission1.getResources().remove(resource2);
        Resource resource11 = ExampleResource1.builder().message("this is a ExampleResource1").build();
        permission1.getResources().add(resource11);
        Assertions.assertDoesNotThrow(
                () -> adminMgr.updatePermission(permission1));
    }

    @Test
    @Commit
    void deletePermission() throws ServiceException {
        Assertions.assertThrows(ValidationException.class, () -> adminMgr.deletePermission(RBACEntityConstants.PERMISSION));

        Resource resource1 = ExampleResource1.builder().message("this is a ExampleResource1").build();
        Set<Resource> objects = new HashSet<>();
        objects.add(resource1);
        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-deletePerm").resources(objects).build());
        Assertions.assertTrue(reviewMgr.readPermission(permission1).isPresent());

        adminMgr.deletePermission(permission1);
        Assertions.assertFalse(reviewMgr.readPermission(permission1).isPresent());
    }

    @Test
    @Commit
    void testGrantPermission() throws ServiceException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.grantPermission(RBACEntityConstants.NOT_EXIST_PERMISSION, RBACEntityConstants.NOT_EXIST_ROLE));
        Resource resource1 = ExampleResource1.builder().message("this is a ExampleResource1").build();
        Set<Resource> objects = new HashSet<>();
        objects.add(resource1);
        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-grantPerm").resources(objects).build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.grantPermission(permission1, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-grantRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.grantPermission(permission1, role));
        List<Permission> permissions = reviewMgr.rolePermissions(role);
        Assertions.assertTrue(permissions.contains(permission1));

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.grantPermission(permission1, role));
    }

    @Test
    @Commit
    void testRevokePermission() throws ServiceException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.revokePermission(RBACEntityConstants.NOT_EXIST_PERMISSION, RBACEntityConstants.NOT_EXIST_ROLE));
        Resource resource1 = ExampleResource1.builder().message("this is a ExampleResource1").build();
        Set<Resource> objects = new HashSet<>();
        objects.add(resource1);
        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-revokePerm").resources(objects).build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.revokePermission(permission1, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-revokeRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.grantPermission(permission1, role));
        List<Permission> permissions = reviewMgr.rolePermissions(role);
        Assertions.assertTrue(permissions.contains(permission1));
        Assertions.assertDoesNotThrow(() -> adminMgr.revokePermission(permission1, role));
        permissions = reviewMgr.rolePermissions(role);
        Assertions.assertFalse(permissions.contains(permission1));

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.revokePermission(permission1, role));
    }
}
```

- [ ] **Step 6: 给 `OrgUnitMgrImplTest` 的 bindPost/unbindPost user 加 username（最小改动，避免 Task 3 后变红）**

文件 `test/.../manager/impl/OrgUnitMgrImplTest.java`：

在 `bindPost()` 方法里把
```java
        User user = User.builder().build();
        User savedUser = adminMgr.addUser(user);
```
改为：
```java
        User user = User.builder().username("org-bindPost-user").build();
        User savedUser = adminMgr.addUser(user);
```

在 `unbindPost()` 方法里把
```java
        User user = User.builder().build();
        User savedUser = adminMgr.addUser(user);
```
改为：
```java
        User user = User.builder().username("org-unbindPost-user").build();
        User savedUser = adminMgr.addUser(user);
```

（Post 的 postName 等改动留到 Task 5 全量重写。）

- [ ] **Step 7: 跑测试**

```bash
cd /Users/maien/mine-query/permission
./gradlew :permission-core:test
```

Expected: `BUILD SUCCESSFUL`，三类全过。

> 若失败：常见原因是某个测试仍把无 username/roleName/permName 的实体喂给 read/add；按报错补自然键。

- [ ] **Step 8: 提交**

```bash
git add -A
git commit -m "refactor: identify user/role/permission by natural key in review/admin managers"
```

---

## Task 4: OrgUnitMgrImpl 改用自然键（Post/OrgUnit 身份）+ 修 map 里 user_id 写错的 bug

**Files:**
- Modify: `manager/impl/OrgUnitMgrImpl.java`（全量重写）
- Modify: `test/.../manager/impl/OrgUnitMgrImplTest.java`（全量重写）

**Interfaces:**
- Consumes: Task 2 的 `findByPostName/findByOrgName`、Task 3 的 `ReviewMgr.readUser`
- Produces: `OrgUnitMgr` 的 Post/OrgUnit 增删改查 + bind/unbind 全部按自然键

> 规则同 Task 3（自然键必填、按自然键解析 id、重复抛「已存在」）。`bindOrgUnitNode` 用 `orgRepository.findById(nodeId)` 直接校验（不经过已改为 orgName 的 `readOrgUnit`）；`OrgSdtNode.nodeId` 非空校验**保留**。同时修掉 bind/unbind 系列 map 里 `user_id` 误填成 `orgUnit.getId()`/`post.getId()` 的 bug。

- [ ] **Step 1: 全量重写 `OrgUnitMgrImpl`**

整文件替换 `manager/impl/OrgUnitMgrImpl.java`：

```java
package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.MineSpecification;
import com.minesunny.jpa.entity.SdtNode;
import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.jpa.manager.MapMgr;
import com.minesunny.jpa.manager.SdtNodeMgr;
import com.minesunny.permission.core.entity.actor.OrgUnit;
import com.minesunny.permission.core.entity.actor.Post;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.sdt.OrgSdtNode;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.OrgUnitMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.repository.OrgRepository;
import com.minesunny.permission.core.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

import static com.minesunny.permission.core.type.MapTableConstants.*;

@Service
@RequiredArgsConstructor
public class OrgUnitMgrImpl implements OrgUnitMgr {
    private final SdtNodeMgr nodeMgr;
    private final OrgRepository orgRepository;
    private final MapMgr mapMgr;
    private final ReviewMgr reviewMgr;
    private final PostRepository postRepository;

    @Override
    public @NotNull Post addPost(@NotNull Post post) throws ServiceException {
        requirePostName(post);
        if (postRepository.findByPostName(post.getPostName()).isPresent()) {
            throw new ValidationException("[addPost]:  The post entity [postName: %s ] already exists".formatted(post.getPostName()));
        }
        Post newPost = new Post();
        BeanUtils.copyProperties(post, newPost, "users");
        return postRepository.saveAndFlush(newPost);
    }

    @Override
    public @NotNull Post updatePost(@NotNull Post post) throws ServiceException {
        requirePostName(post);
        Post storePost = postRepository.findByPostName(post.getPostName())
                .orElseThrow(() -> new ValidationException("[updatePost]:    The post entity [postName: %s] does not exist".formatted(post.getPostName())));
        BeanUtils.copyProperties(post, storePost, "id", "postName", "users");
        return postRepository.saveAndFlush(storePost);
    }

    @Override
    public void deletePost(@NotNull Post post) throws ServiceException {
        requirePostName(post);
        Post storePost = postRepository.findByPostName(post.getPostName())
                .orElseThrow(() -> new ValidationException("[deletePost]:    The post entity [postName: %s] does not exist".formatted(post.getPostName())));
        Map<String, List<Long>> map = new HashMap<>();
        map.put("post_id", List.of(storePost.getId()));
        mapMgr.delete(POST_USER, map);
        postRepository.delete(storePost);
    }

    @Override
    public Optional<Post> readPost(@NotNull Post post) throws ServiceException {
        requirePostName(post);
        return postRepository.findByPostName(post.getPostName());
    }

    @Override
    public @NotNull List<Post> readPosts(@NotNull Post post) throws ServiceException {
        MineSpecification<Post> search = post.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readPosts]:    The post searchCondition is required and cannot be null");
        }
        return postRepository.findAll(search);
    }

    @Override
    public void bindPost(@NotNull Post post, @NotNull User user) throws ServiceException {
        Long postId = resolvePostId(post, "bindPost");
        Long userId = resolveUserId(user, "bindPost");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(userId));
        map.put("post_id", List.of(postId));
        if (mapMgr.exist(POST_USR, map)) {
            throw new ValidationException("[bindPost]:    The user[id: %s] already has post[id: %s]".formatted(userId, postId));
        }
        mapMgr.save(POST_USER, map);
    }

    @Override
    public void unbindPost(@NotNull Post post, @NotNull User user) throws ServiceException {
        Long postId = resolvePostId(post, "unbindPost");
        Long userId = resolveUserId(user, "unbindPost");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(userId));
        map.put("post_id", List.of(postId));
        if (!mapMgr.exist(POST_USR, map)) {
            throw new ValidationException("[unbindPost]:    The user[id: %s] has no post[id: %s]".formatted(userId, postId));
        }
        mapMgr.delete(POST_USER, map);
    }

    @Override
    public @NotNull OrgUnit addOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        requireOrgName(orgUnit);
        if (orgRepository.findByOrgName(orgUnit.getOrgName()).isPresent()) {
            throw new ValidationException("[addOrgUnit]:  The OrgUnit entity [orgName: %s ] already exists".formatted(orgUnit.getOrgName()));
        }
        OrgUnit newOrgUnit = new OrgUnit();
        BeanUtils.copyProperties(orgUnit, newOrgUnit, "managerUsers", "users");
        return orgRepository.saveAndFlush(newOrgUnit);
    }

    @Override
    public @NotNull OrgUnit updateOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        requireOrgName(orgUnit);
        OrgUnit storeOrgUnit = orgRepository.findByOrgName(orgUnit.getOrgName())
                .orElseThrow(() -> new ValidationException("[updateOrgUnit]:    The orgUnit entity [orgName: %s] does not exist".formatted(orgUnit.getOrgName())));
        BeanUtils.copyProperties(orgUnit, storeOrgUnit, "id", "orgName", "users", "managerUsers");
        return orgRepository.saveAndFlush(storeOrgUnit);
    }

    @Override
    public void deleteOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        requireOrgName(orgUnit);
        OrgUnit storeOrgUnit = orgRepository.findByOrgName(orgUnit.getOrgName())
                .orElseThrow(() -> new ValidationException("[deleteOrgUnit]:    The orgUnit entity [orgName: %s] does not exist".formatted(orgUnit.getOrgName())));
        Long orgId = storeOrgUnit.getId();
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(orgId));
        mapMgr.delete(ORG_UNIT_USER, map);
        mapMgr.delete(USER_ORG_UNIT, map);
        // 删除组织架构树
        OrgSdtNode orgSdtNode = OrgSdtNode.builder().nodeId(orgId).build();
        orgSdtNode.andEq("nodeId");
        List<SdtNode> sdtNodes = nodeMgr.readSdtNodes(orgSdtNode);
        for (SdtNode sdtNode : sdtNodes) {
            nodeMgr.unbindSdtNode(sdtNode);
        }
        orgRepository.delete(storeOrgUnit);
    }

    @Override
    public Optional<OrgUnit> readOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        requireOrgName(orgUnit);
        return orgRepository.findByOrgName(orgUnit.getOrgName());
    }

    @Override
    public @NotNull List<OrgUnit> readOrgUnits(@NotNull OrgUnit orgUnit) throws ServiceException {
        MineSpecification<OrgUnit> search = orgUnit.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readOrgUnits]:    The orgUnit searchCondition is required and cannot be null");
        }
        return orgRepository.findAll(search);
    }

    @Override
    public void bindOrgUnit(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        long[] ids = resolveUserOrg(user, orgUnit, "bindOrgUnit");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(ids[1]));
        map.put("user_id", List.of(ids[0]));
        if (mapMgr.exist(ORG_UNIT_USER, map)) {
            throw new ValidationException("[bindOrgUnit]:    The user has bind with orgUnit");
        }
        mapMgr.save(ORG_UNIT_USER, map);
    }

    @Override
    public void unbindOrgUnit(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        long[] ids = resolveUserOrg(user, orgUnit, "unbindOrgUnit");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(ids[1]));
        map.put("user_id", List.of(ids[0]));
        if (!mapMgr.exist(ORG_UNIT_USER, map)) {
            throw new ValidationException("[unbindOrgUnit]:    The user has not bind with orgUnit");
        }
        mapMgr.delete(ORG_UNIT_USER, map);
    }

    @Override
    public void bindOrgUnitNode(@NotNull OrgSdtNode childOrgNode, @NotNull OrgSdtNode parentOrgNode) throws ServiceException {
        if (Objects.isNull(childOrgNode.getNodeId())) {
            throw new ValidationException("[bindOrgUnitNode]:    The childOrgNode entity's nodeId is required and cannot be null");
        }
        if (orgRepository.findById(childOrgNode.getNodeId()).isEmpty()) {
            throw new ValidationException("[bindOrgUnitNode]:    The childOrgNode entity does not exist");
        }
        if (Objects.isNull(parentOrgNode.getNodeId())) {
            throw new ValidationException("[bindOrgUnitNode]:    The parentOrgNode entity's nodeId is required and cannot be null");
        }
        if (orgRepository.findById(parentOrgNode.getNodeId()).isEmpty()) {
            throw new ValidationException("[bindOrgUnitNode]:    The parentOrgNode entity does not exist");
        }
        if (Objects.isNull(childOrgNode.getId())) {
            childOrgNode.setId(nodeMgr.addSdtNode(childOrgNode).getId());
        }
        if (Objects.isNull(parentOrgNode.getId())) {
            parentOrgNode.setId(nodeMgr.addSdtNode(parentOrgNode).getId());
        }
        nodeMgr.bindSdtNode(childOrgNode, parentOrgNode);
    }

    @Override
    public void unbindOrgUnitNode(@NotNull OrgSdtNode childOrgNode) throws ServiceException {
        if (Objects.isNull(childOrgNode.getId())) {
            throw new ValidationException("[unbindOrgUnitNode]:    The childOrgNode entity does not exist");
        }
        Optional<SdtNode> optionalChildSdtNode = nodeMgr.readSdtNode(childOrgNode);
        if (optionalChildSdtNode.isEmpty()) {
            throw new ValidationException("[unbindOrgUnitNode]:    The childOrgNode entity does not exist");
        }
        nodeMgr.unbindSdtNode(optionalChildSdtNode.get());
    }

    @Override
    public void bindManagerUser(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        long[] ids = resolveUserOrg(user, orgUnit, "bindManagerUser");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(ids[1]));
        map.put("user_id", List.of(ids[0]));
        if (mapMgr.exist(USER_ORG_UNIT, map)) {
            throw new ValidationException("[bindManagerUser]:    The manager user has bind with orgUnit");
        }
        mapMgr.save(USER_ORG_UNIT, map);
    }

    @Override
    public void unbindManagerUser(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        long[] ids = resolveUserOrg(user, orgUnit, "unbindManagerUser");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(ids[1]));
        map.put("user_id", List.of(ids[0]));
        if (!mapMgr.exist(USER_ORG_UNIT, map)) {
            throw new ValidationException("[unbindManagerUser]:    The manager user has not bind with orgUnit");
        }
        mapMgr.delete(USER_ORG_UNIT, map);
    }

    private void requirePostName(Post post) {
        if (!StringUtils.hasText(post.getPostName())) {
            throw new ValidationException("The post entity's postName is required and cannot be null");
        }
    }

    private void requireOrgName(OrgUnit orgUnit) {
        if (!StringUtils.hasText(orgUnit.getOrgName())) {
            throw new ValidationException("The orgUnit entity's orgName is required and cannot be null");
        }
    }

    private Long resolvePostId(Post post, String op) throws ValidationException {
        requirePostName(post);
        return postRepository.findByPostName(post.getPostName())
                .orElseThrow(() -> new ValidationException("[%s]:    The post[postName: %s] does not exist".formatted(op, post.getPostName())))
                .getId();
    }

    private Long resolveUserId(User user, String op) throws ServiceException {
        return reviewMgr.readUser(user)
                .orElseThrow(() -> new ValidationException("[%s]:    The user does not exist".formatted(op)))
                .getId();
    }

    private long[] resolveUserOrg(User user, OrgUnit orgUnit, String op) throws ServiceException {
        Long userId = resolveUserId(user, op);
        requireOrgName(orgUnit);
        Long orgId = orgRepository.findByOrgName(orgUnit.getOrgName())
                .orElseThrow(() -> new ValidationException("[%s]:    The orgUnit[orgName: %s] does not exist".formatted(op, orgUnit.getOrgName())))
                .getId();
        return new long[]{userId, orgId};
    }
}
```

- [ ] **Step 2: 全量重写 `OrgUnitMgrImplTest`（Post/OrgUnit 带全类唯一自然键）**

整文件替换 `test/.../manager/impl/OrgUnitMgrImplTest.java`：

```java
package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.actor.Post;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.OrgUnitMgr;
import com.minesunny.permission.core.type.RBACEntityConstants;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection =
        EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class OrgUnitMgrImplTest {

    @Autowired
    OrgUnitMgr orgUnitMgr;
    @Autowired
    AdminMgr adminMgr;
    @Autowired
    EntityManager entityManager;

    @Test
    void addPost() throws ServiceException {
        Post post = Post.builder().postName("org-addPost").build();
        Post savedPost = orgUnitMgr.addPost(post);
        Assertions.assertTrue(orgUnitMgr.readPost(savedPost).isPresent());

        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.addPost(savedPost));
    }

    @Test
    void updatePost() throws ServiceException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.updatePost(Post.builder().build()));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.updatePost(RBACEntityConstants.NOT_EXIST_POST));

        Post post = Post.builder().postName("org-updatePost").build();
        Post savedPost = orgUnitMgr.addPost(post);

        savedPost.setPostName("org-updatePost");
        savedPost.setDescription("update post desc");

        orgUnitMgr.updatePost(savedPost);
        Optional<Post> optionalPost = orgUnitMgr.readPost(savedPost);
        Assertions.assertTrue(optionalPost.isPresent());
        Assertions.assertEquals("update post desc", optionalPost.get().getDescription());
    }

    @Test
    void deletePost() throws ServiceException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.deletePost(Post.builder().build()));

        Post post = Post.builder().postName("org-deletePost").build();
        Post savedPost = orgUnitMgr.addPost(post);
        orgUnitMgr.deletePost(savedPost);
        Assertions.assertTrue(orgUnitMgr.readPost(savedPost).isEmpty());
    }

    @Test
    void readPost() {
    }

    @Test
    void readPosts() {
    }

    @Test
    void bindPost() throws ServiceException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(RBACEntityConstants.POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.NOT_EXIST_USER));

        Post post = Post.builder().postName("org-bindPost").build();
        Post savedPost = orgUnitMgr.addPost(post);
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(savedPost, RBACEntityConstants.NOT_EXIST_USER));
        User user = User.builder().username("org-bindPost-user").build();
        User savedUser = adminMgr.addUser(user);

        Assertions.assertDoesNotThrow(() -> orgUnitMgr.bindPost(savedPost, savedUser));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(savedPost, savedUser));
        Optional<Post> optionalPost = orgUnitMgr.readPost(savedPost);
        Assertions.assertTrue(optionalPost.isPresent());

        Assertions.assertTrue(optionalPost.get().getUsers().contains(savedUser));
    }

    @Test
    void unbindPost() throws ServiceException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(RBACEntityConstants.POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.NOT_EXIST_USER));

        Post post = Post.builder().postName("org-unbindPost").build();
        Post savedPost = orgUnitMgr.addPost(post);
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(savedPost, RBACEntityConstants.NOT_EXIST_USER));
        User user = User.builder().username("org-unbindPost-user").build();
        User savedUser = adminMgr.addUser(user);
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(savedPost, savedUser));
        Assertions.assertDoesNotThrow(() -> orgUnitMgr.bindPost(savedPost, savedUser));
        Optional<Post> optionalPost = orgUnitMgr.readPost(savedPost);
        Assertions.assertTrue(optionalPost.isPresent());
        Assertions.assertTrue(optionalPost.get().getUsers().contains(savedUser));

        Assertions.assertDoesNotThrow(() -> orgUnitMgr.unbindPost(savedPost, savedUser));

        Optional<Post> optionalPost2 = orgUnitMgr.readPost(savedPost);
        Assertions.assertTrue(optionalPost2.isPresent());
        Assertions.assertFalse(optionalPost2.get().getUsers().contains(savedUser));
    }

    @Test
    void addOrgUnit() {
    }

    @Test
    void updateOrgUnit() {
    }

    @Test
    void deleteOrgUnit() {
    }

    @Test
    void readOrgUnit() {
    }

    @Test
    void readOrgUnits() {
    }

    @Test
    void bindOrgUnit() {
    }

    @Test
    void unbindOrgUnit() {
    }

    @Test
    void testBindOrgUnit() {
    }

    @Test
    void testUnbindOrgUnit() {
    }
}
```

- [ ] **Step 3: 跑测试**

```bash
cd /Users/maien/mine-query/permission
./gradlew :permission-core:test
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "refactor: identify post/orgunit by natural key in orgunit manager; fix user_id map bug"
```

---

## Task 5: 自然键加 DB 唯一 + 非空约束

**Files:**
- Modify: `entity/actor/User.java`、`entity/Role.java`、`entity/perm/Permission.java`、`entity/actor/Post.java`、`entity/actor/OrgUnit.java`

**Interfaces:**
- Consumes: Task 3/4（所有测试实体已带自然键）
- Produces: DB schema 强制自然键唯一且非空

> 到此为止所有测试创建的实体都带了自然键，加约束不会打破测试。

- [ ] **Step 1: `User.username` 补约束**

把 Task 2 加的：
```java
    @Column(name = "username")
    @Comment(value = "user login name, unique")
    private String username;
```
改为：
```java
    @Column(name = "username", unique = true, nullable = false)
    @Comment(value = "user login name, unique")
    private String username;
```

- [ ] **Step 2: `Role.roleName` 补约束**

`Role.java` 里：
```java
    @Column(name = "role_name")
```
改为：
```java
    @Column(name = "role_name", unique = true, nullable = false)
```

- [ ] **Step 3: `Permission.permName` 补约束**

`Permission.java` 里：
```java
    @Column(name = "perm_name")
```
改为：
```java
    @Column(name = "perm_name", unique = true, nullable = false)
```

- [ ] **Step 4: `Post.postName` 补约束**

`Post.java` 里：
```java
    @Column(name = "post_name")
```
改为：
```java
    @Column(name = "post_name", unique = true, nullable = false)
```

- [ ] **Step 5: `OrgUnit.orgName` 补约束**

`OrgUnit.java` 里：
```java
    @Column(name = "org_name")
```
改为：
```java
    @Column(name = "org_name", unique = true, nullable = false)
```

- [ ] **Step 6: 跑测试（H2 会按约束建表）**

```bash
cd /Users/maien/mine-query/permission
./gradlew :permission-core:test
```

Expected: `BUILD SUCCESSFUL`。

> 若失败：H2 报 unique/nullable 约束违例 → 说明某处测试用了重复或空的自然键，按报错修。

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat: enforce uniqueness and non-null on natural keys"
```

---

## Task 6: 终检 + 清理

**Files:**
- 无新增；仅校验

**Interfaces:**
- Consumes: Task 1–5
- Produces: 验收通过

- [ ] **Step 1: 全仓 grep 确认 RBAC 身份实体已无 getId 判空**

```bash
cd /Users/maien/mine-query/permission
echo "--- all getId() null checks (RBAC identity entities must be ABSENT) ---"
grep -rnE --include="*.java" "Objects\.(nonNull|isNull)\([^)]*\.getId\(\)\)" permission-core/src/main
echo "--- now check ONLY the 5 RBAC identity entities (must be empty) ---"
grep -rnE --include="*.java" "Objects\.(nonNull|isNull)\((user|role|permission|post|orgUnit|storeUser|storeRole|storePermission|storePost|storeOrgUnit|findUser|findRole|findPost|findOrgUnit|findPermission)\.getId\(\)\)" permission-core/src/main
echo "--- exit $? (1 = none found, GOOD) ---"
```

Expected：
- 第一条 grep 命中 5 行，全部是 **`resource.getId()`**（`ReviewMgrImpl` 2 处）或 **OrgSdtNode 自身**（`OrgUnitMgrImpl` 的 `childOrgNode.getId()`/`parentOrgNode.getId()` 共 3 处）。这些是 spec 确认的例外：Resource 不在自然键范围；OrgSdtNode 是树节点实体、其 `getId()` 是「未持久化则先创建」逻辑而非身份判空。
- 第二条 grep（仅查 RBAC 身份实体变量）**无输出**（exit 1）。

> 说明：`Role.locked/activated` 里 `Objects.isNull(beginLockDateTime)` 等是对 Calendar 字段判空，不是 getId()，保留。

- [ ] **Step 2: 完整 build**

```bash
cd /Users/maien/mine-query/permission
./gradlew build
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 更新 README（构建命令从 mvn 改 gradle）**

`README.md` 追加/替换为：

```markdown
# permission

RBAC permission core library.

## Build

```
./gradlew build
./gradlew :permission-core:test
```

Requires JDK 21. Depends on local `com.minesunny.jpa:base-jpa` (install to `mavenLocal()` first).
```

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "docs: update readme for gradle build"
```

---

## Self-Review 记录

- **Spec 覆盖**：Gradle(Kotlin DSL, 多模块)→Task 1；username 新增→Task 2/5；findByX→Task 2；readUser/Role/Permission 用自然键→Task 3；删 getId 判空→Task 3/4（Resource/nodeId 例外→Task 3/4 标注）；Post/OrgUnit 用 postName/orgName→Task 4；唯一约束→Task 5；测试更新→Task 3/4。✅
- **占位符扫描**：无 TBD/TODO；每步均有完整代码或精确命令。✅
- **类型一致性**：`findByUsername/findByRoleName/findByPermName/findByPostName/findByOrgName` 在 Task 2 定义，Task 3/4 消费，签名一致；`User.getUsername()` 等一致；`UserRoleIds`/`PermissionRoleIds` record 定义与使用一致。✅
- **已知边界**：`base-jpa` 依赖 `mavenLocal()`，换机需先 install（README 已注明）。
