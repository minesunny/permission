/*
 * Copyright 2023 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */
package com.minesunny.permission.core.manager;

import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.Session;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACException;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * RBAC 审查与查询业务接口。
 * <p>
 * 提供针对参与者（Actor/User/Department）、角色（Role）、权限（Permission）
 * 的单实体查询、关系检索与权限核验能力。单实体查询均支持以名称（自然主键）作为唯一标识。
 */
@Transactional
public interface ReviewMgr {

    /**
     * 根据参与者名称（自然主键）查询单个参与者，若不存在则抛出异常。
     *
     * @param actorName 待查询的参与者名称
     * @return 匹配的参与者实体对象（非空）
     * @throws RBACException 当参与者不存在或参数校验异常时抛出
     */
    @NotNull
    Actor readActor(@NotNull String actorName) throws RBACException;

    /**
     * 根据参与者实体携带的条件查询单个参与者，若不存在则抛出异常。
     *
     * @param actor 携带 actorName 的参与者对象
     * @return 匹配的参与者实体对象（非空）
     * @throws RBACException 当参与者不存在或参数校验异常时抛出
     */
    @NotNull
    Actor readActor(@NotNull Actor actor) throws RBACException;

    /**
     * 根据参与者名称（自然主键）查询单个参与者，若不存在则返回 null。
     *
     * @param actorName 待查询的参与者名称
     * @return 匹配的参与者实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    Actor findActor(@NotNull String actorName) throws RBACException;

    /**
     * 根据参与者实体携带的条件查询单个参与者，若不存在则返回 null。
     *
     * @param actor 携带 actorName 的参与者对象
     * @return 匹配的参与者实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    Actor findActor(@NotNull Actor actor) throws RBACException;

    /**
     * 根据查询条件查询匹配的参与者列表。
     *
     * @param actor 包含 specification 查询条件的参与者实体
     * @return 匹配的参与者列表
     * @throws RBACException 当查询异常时抛出
     */
    List<Actor> readActors(@NotNull Actor actor) throws RBACException;

    /**
     * 根据用户登录名（自然主键）查询单个用户，若不存在则抛出异常。
     *
     * @param username 待查询的用户登录名
     * @return 匹配的用户实体对象（非空）
     * @throws RBACException 当用户不存在或参数校验异常时抛出
     */
    @NotNull
    User readUser(@NotNull String username) throws RBACException;

    /**
     * 根据用户实体携带的条件查询单个用户，若不存在则抛出异常。
     *
     * @param user 携带 username 或 id 的用户实体
     * @return 匹配的用户实体对象（非空）
     * @throws RBACException 当用户不存在或参数校验异常时抛出
     */
    @NotNull
    User readUser(@NotNull User user) throws RBACException;

    /**
     * 根据用户登录名（自然主键）查询单个用户，若不存在则返回 null。
     *
     * @param username 待查询的用户登录名
     * @return 匹配的用户实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    User findUser(@NotNull String username) throws RBACException;

    /**
     * 根据用户实体携带的条件查询单个用户，若不存在则返回 null。
     *
     * @param user 携带 username 或 id 的用户实体
     * @return 匹配的用户实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    User findUser(@NotNull User user) throws RBACException;

    /**
     * 根据查询条件查询匹配的用户列表。
     *
     * @param user 包含 specification 查询条件的用户实体
     * @return 匹配的用户列表
     * @throws RBACException 当查询异常时抛出
     */
    @NotNull
    List<User> readUsers(@NotNull User user) throws RBACException;

    /**
     * 根据角色名称（自然主键）查询单个角色，若不存在则抛出异常。
     *
     * @param roleName 角色名称
     * @return 匹配的角色实体对象（非空）
     * @throws RBACException 当角色不存在或参数校验异常时抛出
     */
    @NotNull
    Role readRole(@NotNull String roleName) throws RBACException;

    /**
     * 根据角色实体携带的条件查询单个角色，若不存在则抛出异常。
     *
     * @param role 携带 roleName 的角色对象
     * @return 匹配的角色实体对象（非空）
     * @throws RBACException 当角色不存在或参数校验异常时抛出
     */
    @NotNull
    Role readRole(@NotNull Role role) throws RBACException;

    /**
     * 根据角色名称（自然主键）查询单个角色，若不存在则返回 null。
     *
     * @param roleName 角色名称
     * @return 匹配的角色实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    Role findRole(@NotNull String roleName) throws RBACException;

    /**
     * 根据角色实体携带的条件查询单个角色，若不存在则返回 null。
     *
     * @param role 携带 roleName 的角色对象
     * @return 匹配的角色实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    Role findRole(@NotNull Role role) throws RBACException;

    /**
     * 根据条件查询角色列表。
     *
     * @param role 包含 specification 查询条件的角色实体
     * @return 满足条件的角色列表
     * @throws RBACException 当查询异常时抛出
     */
    @NotNull
    List<Role> readRoles(@NotNull Role role) throws RBACException;

    /**
     * 根据权限名称（自然主键）查询单个权限，若不存在则抛出异常。
     *
     * @param permName 权限名称
     * @return 匹配的权限实体对象（非空）
     * @throws RBACException 当权限不存在或参数校验异常时抛出
     */
    @NotNull
    Permission readPermission(@NotNull String permName) throws RBACException;

    /**
     * 根据权限实体携带的条件查询单个权限，若不存在则抛出异常。
     *
     * @param permission 携带 permName 的权限对象
     * @return 匹配的权限实体对象（非空）
     * @throws RBACException 当权限不存在或参数校验异常时抛出
     */
    @NotNull
    Permission readPermission(@NotNull Permission permission) throws RBACException;

    /**
     * 根据权限名称（自然主键）查询单个权限，若不存在则返回 null。
     *
     * @param permName 权限名称
     * @return 匹配的权限实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    Permission findPermission(@NotNull String permName) throws RBACException;

    /**
     * 根据权限实体携带的条件查询单个权限，若不存在则返回 null。
     *
     * @param permission 携带 permName 的权限对象
     * @return 匹配的权限实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    Permission findPermission(@NotNull Permission permission) throws RBACException;

    /**
     * 根据条件查询权限列表。
     *
     * @param permission 包含 specification 查询条件的权限实体
     * @return 满足条件的权限列表
     * @throws RBACException 当查询异常时抛出
     */
    @NotNull
    List<Permission> readPermissions(@NotNull Permission permission) throws RBACException;

    /**
     * 根据角色名称查询该角色直接拥有的权限列表。
     *
     * @param roleName 角色名称
     * @return 角色拥有的权限列表
     * @throws RBACException 当角色不存在或查询异常时抛出
     */
    @NotNull
    List<Permission> rolePermissions(@NotNull String roleName) throws RBACException;

    /**
     * 查询指定角色直接拥有的权限列表。
     *
     * @param role 角色实体
     * @return 角色拥有的权限列表
     * @throws RBACException 当角色不存在或查询异常时抛出
     */
    @NotNull
    List<Permission> rolePermissions(@NotNull Role role) throws RBACException;

    /**
     * 根据参与者名称查询其通过所有已分配角色聚合获得的权限列表。
     *
     * @param actorName 参与者名称
     * @return 聚合获得的权限列表
     * @throws RBACException 当参与者不存在或查询异常时抛出
     */
    @NotNull
    List<Permission> actorPermissions(@NotNull String actorName) throws RBACException;

    /**
     * 查询指定参与者通过其所有角色聚合获得的权限列表。
     *
     * @param actor 参与者实体
     * @return 聚合获得的权限列表
     * @throws RBACException 当参与者不存在或查询异常时抛出
     */
    @NotNull
    List<Permission> actorPermissions(@NotNull Actor actor) throws RBACException;

    /**
     * 查询指定会话的激活角色列表。
     *
     * @param session 会话对象
     * @return 激活的角色列表
     * @throws RBACException 当查询异常时抛出
     */
    @NotNull
    List<Role> sessionRoles(@NotNull Session session) throws RBACException;

    /**
     * 查询指定会话所具有的权限并集。
     *
     * @param session 会话对象
     * @return 权限列表
     * @throws RBACException 当查询异常时抛出
     */
    @NotNull
    List<Permission> sessionPermissions(@NotNull Session session) throws RBACException;

    /**
     * 根据角色名称查询已分配给该角色的参与者列表。
     *
     * @param roleName 角色名称
     * @return 分配给该角色的参与者列表
     * @throws RBACException 当角色不存在或查询异常时抛出
     */
    @NotNull
    List<Actor> assignedActors(@NotNull String roleName) throws RBACException;

    /**
     * 查询已分配给指定角色的参与者列表。
     *
     * @param role 角色实体
     * @return 分配给该角色的参与者列表
     * @throws RBACException 当角色不存在或查询异常时抛出
     */
    @NotNull
    List<Actor> assignedActors(@NotNull Role role) throws RBACException;

    /**
     * 根据参与者名称查询其已分配的角色列表。
     *
     * @param actorName 参与者名称
     * @return 参与者已分配的角色列表
     * @throws RBACException 当参与者不存在或查询异常时抛出
     */
    @NotNull
    List<Role> assignedRoles(@NotNull String actorName) throws RBACException;

    /**
     * 查询指定参与者已分配的角色列表。
     *
     * @param actor 参与者实体
     * @return 参与者已分配的角色列表
     * @throws RBACException 当参与者不存在或查询异常时抛出
     */
    @NotNull
    List<Role> assignedRoles(@NotNull Actor actor) throws RBACException;

    /**
     * 查询拥有指定权限的角色列表。
     *
     * @param permission 权限实体
     * @return 包含该权限的角色列表
     * @throws RBACException 当权限不存在或查询异常时抛出
     */
    @NotNull
    List<Role> permissionRoles(@NotNull Permission permission) throws RBACException;

    /**
     * 查询拥有指定权限的所有参与者列表。
     *
     * @param permission 权限实体
     * @return 具有该权限的参与者列表
     * @throws RBACException 当权限不存在或查询异常时抛出
     */
    @NotNull
    List<Actor> permissionActors(@NotNull Permission permission) throws RBACException;

    /**
     * 根据名称判断参与者是否拥有指定角色。
     *
     * @param actorName 参与者名称
     * @param roleName  角色名称
     * @return 若已分配返回 {@code true}，否则返回 {@code false}
     * @throws RBACException 当校验参数失败时抛出
     */
    boolean hasRole(@NotNull String actorName, @NotNull String roleName) throws RBACException;

    /**
     * 判断参与者是否拥有指定角色。
     *
     * @param actor 参与者实体
     * @param role  角色实体
     * @return 若已分配返回 {@code true}，否则返回 {@code false}
     * @throws RBACException 当校验参数失败时抛出
     */
    boolean hasRole(@NotNull Actor actor, @NotNull Role role) throws RBACException;

    /**
     * 根据名称判断参与者是否具备指定权限（经由其分配的角色）。
     *
     * @param actorName 参与者名称
     * @param permName  权限名称
     * @return 若具备返回 {@code true}，否则返回 {@code false}
     * @throws RBACException 当校验参数失败时抛出
     */
    boolean hasPermission(@NotNull String actorName, @NotNull String permName) throws RBACException;

    /**
     * 判断参与者是否具备指定权限（经由其分配的角色）。
     *
     * @param actor      参与者实体
     * @param permission 权限实体
     * @return 若具备返回 {@code true}，否则返回 {@code false}
     * @throws RBACException 当校验参数失败时抛出
     */
    boolean hasPermission(@NotNull Actor actor, @NotNull Permission permission) throws RBACException;

    /**
     * 判定指定会话主体是否有权在目标资源对象上执行特定操作（NIST CheckAccess 系统支撑函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $CheckAccess(s, op, obj) \equiv \exists r \in SessionRoles(s), \exists p \in RolePermissions(r):$<br>
     * $(p.operation = op \wedge p.resource = obj \wedge p.effect \neq "DENY")$
     * </blockquote>
     *
     * @param session   活动会话
     * @param operation 待执行的操作动作（Operation $op \in OPS$）
     * @param resource  目标受保护资源（Object $obj \in OBS$）
     * @return 若会话激活角色授予了该操作权限且未被显式拒绝则返回 {@code true}，否则返回 {@code false}
     * @throws RBACException 当参数非法时抛出
     */
    boolean checkAccess(@NotNull Session session, @NotNull String operation, @NotNull String resource) throws RBACException;

    /**
     * 判定指定主体是否具备在目标资源对象上执行特定操作的权限（通过已分配角色）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $CheckAccess(u, op, obj) \equiv \exists p \in UserPermissions(u): (p.operation = op \wedge p.resource = obj \wedge p.effect \neq "DENY")$
     * </blockquote>
     *
     * @param actorName 参与者名称（自然主键）
     * @param operation 待执行的操作动作（Operation $op \in OPS$）
     * @param resource  目标受保护资源（Object $obj \in OBS$）
     * @return 若主体拥有对应操作权限返回 {@code true}，否则返回 {@code false}
     * @throws RBACException 当参与者不存在或参数非法时抛出
     */
    boolean checkAccess(@NotNull String actorName, @NotNull String operation, @NotNull String resource) throws RBACException;

    /**
     * 查询指定角色在特定资源对象上所允许执行的所有操作动作集合（NIST RoleOperationsOnObject 审查函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $RoleOperationsOnResource(r, obj) = \{op \in OPS \mid \exists p \in RolePermissions(r): (p.resource = obj \wedge p.operation = op)\}$
     * </blockquote>
     *
     * @param roleName 角色名称（自然主键）
     * @param resource 目标资源对象标识（$obj \in OBS$）
     * @return 该角色对该资源允许执行的操作集合（去重列表，若无权限返回空列表）
     * @throws RBACException 当角色不存在或参数非法时抛出
     */
    @NotNull
    List<String> roleOperationsOnResource(@NotNull String roleName, @NotNull String resource) throws RBACException;

    /**
     * 查询指定角色在特定资源对象上所允许执行的所有操作动作集合。
     *
     * @param role     角色实体
     * @param resource 目标资源对象标识（$obj \in OBS$）
     * @return 该角色对该资源允许执行的操作集合（去重列表，若无权限返回空列表）
     * @throws RBACException 当角色不存在或参数非法时抛出
     */
    @NotNull
    List<String> roleOperationsOnResource(@NotNull Role role, @NotNull String resource) throws RBACException;

    /**
     * 查询指定主体通过其所有已分配角色在特定资源对象上允许执行的所有操作动作集合（NIST UserOperationsOnObject 审查函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $UserOperationsOnResource(u, obj) = \{op \in OPS \mid \exists p \in UserPermissions(u): (p.resource = obj \wedge p.operation = op)\}$
     * </blockquote>
     *
     * @param actorName 参与者名称（自然主键）
     * @param resource  目标资源对象标识（$obj \in OBS$）
     * @return 该主体对该资源聚合允许执行的操作集合（去重列表，若无权限返回空列表）
     * @throws RBACException 当参与者不存在或参数非法时抛出
     */
    @NotNull
    List<String> userOperationsOnResource(@NotNull String actorName, @NotNull String resource) throws RBACException;

    /**
     * 查询指定主体通过其所有已分配角色在特定资源对象上允许执行的所有操作动作集合。
     *
     * @param actor    参与者实体
     * @param resource 目标资源对象标识（$obj \in OBS$）
     * @return 该主体对该资源聚合允许执行的操作集合（去重列表，若无权限返回空列表）
     * @throws RBACException 当参与者不存在或参数非法时抛出
     */
    @NotNull
    List<String> userOperationsOnResource(@NotNull Actor actor, @NotNull String resource) throws RBACException;

    /**
     * 查询指定主体拥有的所有已授权角色集合（NIST Hierarchical AuthorizedRoles 审查函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $AuthorizedRoles(u) = \{r \in ROLES \mid \exists r' \in AssignedRoles(u): r' \succeq r\}$
     * </blockquote>
     * 返回用户直接分配的角色以及沿角色继承链向下继承的所有子角色集合（去重）。
     *
     * @param actorName 参与者名称（自然主键）
     * @return 包含直接分配及下位继承角色的全量角色列表
     * @throws RBACException 当参与者不存在或参数校验失败时抛出
     */
    @NotNull
    List<Role> authorizedRoles(@NotNull String actorName) throws RBACException;

    /**
     * 查询指定主体拥有的所有已授权角色集合。
     *
     * @param actor 参与者实体
     * @return 包含直接分配及下位继承角色的全量角色列表
     * @throws RBACException 当参与者不存在或参数校验失败时抛出
     */
    @NotNull
    List<Role> authorizedRoles(@NotNull Actor actor) throws RBACException;

    /**
     * 查询对指定角色拥有执行权限的所有已授权主体集合（NIST Hierarchical AuthorizedUsers 审查函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $AuthorizedUsers(r) = \{u \in USERS \mid \exists r' \in ROLES: (u, r') \in UA \wedge r' \succeq r\}$
     * </blockquote>
     * 返回直接分配该角色的主体以及被分配了上位父角色的所有主体集合（去重）。
     *
     * @param roleName 角色名称（自然主键）
     * @return 包含直接分配用户与上位继承用户的全量主体列表
     * @throws RBACException 当角色不存在或参数校验失败时抛出
     */
    @NotNull
    List<Actor> authorizedActors(@NotNull String roleName) throws RBACException;

    /**
     * 查询对指定角色拥有执行权限的所有已授权主体集合。
     *
     * @param role 角色实体
     * @return 包含直接分配用户与上位继承用户的全量主体列表
     * @throws RBACException 当角色不存在或参数校验失败时抛出
     */
    @NotNull
    List<Actor> authorizedActors(@NotNull Role role) throws RBACException;

    /**
     * 查询指定角色拥有的全量已授权权限集合（包含直接权限与沿继承链继承的所有子角色权限）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $AuthorizedPermissions(r) = \bigcup_{r \succeq r'} DirectPermissions(r')$
     * </blockquote>
     *
     * @param roleName 角色名称（自然主键）
     * @return 聚合直接权限与下位继承权限的去重列表
     * @throws RBACException 当角色不存在或参数非法时抛出
     */
    @NotNull
    List<Permission> authorizedPermissions(@NotNull String roleName) throws RBACException;

    /**
     * 查询指定角色拥有的全量已授权权限集合。
     *
     * @param role 角色实体
     * @return 聚合直接权限与下位继承权限的去重列表
     * @throws RBACException 当角色不存在或参数非法时抛出
     */
    @NotNull
    List<Permission> authorizedPermissions(@NotNull Role role) throws RBACException;

    /**
     * 查询指定主体拥有的全量已授权权限集合（通过其所有直接角色与继承角色聚合获得）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $AuthorizedPermissions(u) = \bigcup_{r \in AuthorizedRoles(u)} DirectPermissions(r)$
     * </blockquote>
     *
     * @param actor 参与者实体
     * @return 主体全量已授权权限的去重列表
     * @throws RBACException 当参与者不存在或参数非法时抛出
     */
    @NotNull
    List<Permission> authorizedActorPermissions(@NotNull Actor actor) throws RBACException;

    /**
     * 查询指定主体拥有的全量已授权权限集合。
     *
     * @param actorName 参与者名称（自然主键）
     * @return 主体全量已授权权限的去重列表
     * @throws RBACException 当参与者不存在或参数非法时抛出
     */
    @NotNull
    List<Permission> authorizedActorPermissions(@NotNull String actorName) throws RBACException;
}
