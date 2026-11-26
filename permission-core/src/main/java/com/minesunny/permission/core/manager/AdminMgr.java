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
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACException;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;

/**
 * RBAC 核心管理接口，负责 Actor、Role、Permission 的生命周期及授权关系管理。
 * <p>
 * 单实体查询与操作优先支持使用名称（自然主键）作为唯一标识。
 */
@Transactional
public interface AdminMgr {

    /**
     * 创建新的 RBAC 参与者（User / Department）。
     *
     * @param actor 待添加的参与者实体对象
     * @param <T>   Actor 具体子类型
     * @return 已持久化的参与者实体
     * @throws RBACException 当参与者名称已存在或数据校验失败时抛出
     */
    @NotNull
    <T extends Actor> T addActor(@NotNull T actor) throws RBACException;

    /**
     * 更新指定参与者的属性信息。
     *
     * @param actorName 待更新参与者的当前名称（自然主键）
     * @param actor     包含更新后属性的对象
     * @param <T>       Actor 具体子类型
     * @return 更新后已持久化的参与者实体
     * @throws RBACException 当目标参与者不存在或更新异常时抛出
     */
    @NotNull
    <T extends Actor> T updateActor(@NotNull String actorName, @NotNull T actor) throws RBACException;

    /**
     * 根据参与者名称删除指定参与者。
     *
     * @param actorName 待删除参与者的名称（自然主键）
     * @throws RBACException 当参与者不存在或删除异常时抛出
     */
    void deleteActor(@NotNull String actorName) throws RBACException;

    /**
     * 删除指定参与者，并清理其所有角色映射与组织映射关系。
     *
     * @param actor 待删除的参与者实体（需指定 actorName 或 id）
     * @throws RBACException 当参与者不存在或删除异常时抛出
     */
    void deleteActor(@NotNull Actor actor) throws RBACException;

    /**
     * 根据参与者名称查询单个参与者，若不存在则抛出异常。
     *
     * @param actorName 待查询的参与者名称（自然主键）
     * @return 匹配的参与者实体对象（非空）
     * @throws RBACException 当参与者不存在或参数校验异常时抛出
     */
    @NotNull
    Actor readActor(@NotNull String actorName) throws RBACException;

    /**
     * 根据参与者名称查询单个参与者，若不存在则返回 null。
     *
     * @param actorName 待查询的参与者名称（自然主键）
     * @return 匹配的参与者实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    Actor findActor(@NotNull String actorName) throws RBACException;

    /**
     * 创建新用户实体。
     *
     * @param user 待创建的用户实体
     * @return 已持久化的用户实体
     * @throws RBACException 当用户名已存在或数据校验失败时抛出
     */
    @NotNull
    User addUser(@NotNull User user) throws RBACException;

    /**
     * 更新指定用户的属性信息。
     *
     * @param username 待更新用户的当前登录名（自然主键）
     * @param user     包含更新后属性的用户实体
     * @return 更新后已持久化的用户实体
     * @throws RBACException 当目标用户不存在或更新冲突时抛出
     */
    @NotNull
    User updateUser(@NotNull String username, @NotNull User user) throws RBACException;

    /**
     * 根据用户名删除指定用户，并清理其角色映射与部门映射。
     *
     * @param username 待删除用户的登录名（自然主键）
     * @throws RBACException 当用户不存在或删除失败时抛出
     */
    void deleteUser(@NotNull String username) throws RBACException;

    /**
     * 删除指定用户，并清理其角色映射与部门映射。
     *
     * @param user 待删除的用户实体
     * @throws RBACException 当用户不存在或删除失败时抛出
     */
    void deleteUser(@NotNull User user) throws RBACException;

    /**
     * 根据用户名查询单个用户，若不存在则抛出异常。
     *
     * @param username 用户登录名（自然主键）
     * @return 匹配的用户实体对象（非空）
     * @throws RBACException 当用户不存在或参数校验失败时抛出
     */
    @NotNull
    User readUser(@NotNull String username) throws RBACException;

    /**
     * 根据用户名查询单个用户，若不存在则返回 null。
     *
     * @param username 用户登录名（自然主键）
     * @return 匹配的用户实体对象，未找到时返回 null
     * @throws RBACException 当参数校验失败时抛出
     */
    User findUser(@NotNull String username) throws RBACException;

    /**
     * 创建新角色。
     *
     * @param role 待创建的角色实体
     * @return 已持久化的角色实体
     * @throws RBACException 当角色已存在或数据不合法时抛出
     */
    @NotNull
    Role addRole(@NotNull Role role) throws RBACException;

    /**
     * 更新指定角色的属性信息。
     *
     * @param roleName 当前角色的名称（自然主键）
     * @param role     包含新属性的角色对象
     * @return 更新后已持久化的角色实体
     * @throws RBACException 当角色不存在或更新异常时抛出
     */
    @NotNull
    Role updateRole(@NotNull String roleName, @NotNull Role role) throws RBACException;

    /**
     * 根据角色名称删除指定角色。
     *
     * @param roleName 待删除角色的名称（自然主键）
     * @throws RBACException 当角色不存在或删除异常时抛出
     */
    void deleteRole(@NotNull String roleName) throws RBACException;

    /**
     * 删除指定角色。
     *
     * @param role 待删除的角色实体
     * @throws RBACException 当角色不存在或删除异常时抛出
     */
    void deleteRole(@NotNull Role role) throws RBACException;

    /**
     * 根据角色名称查询单个角色，若不存在则抛出异常。
     *
     * @param roleName 待查询的角色名称（自然主键）
     * @return 匹配的角色实体对象（非空）
     * @throws RBACException 当角色不存在或参数校验异常时抛出
     */
    @NotNull
    Role readRole(@NotNull String roleName) throws RBACException;

    /**
     * 根据角色名称查询单个角色，若不存在则返回 null。
     *
     * @param roleName 待查询的角色名称（自然主键）
     * @return 匹配的角色实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    Role findRole(@NotNull String roleName) throws RBACException;

    /**
     * 根据参与者名称与角色名称为参与者分配角色。
     *
     * @param actorName 参与者名称
     * @param roleName  角色名称
     * @throws RBACException 当参与者或角色不存在、或已分配时抛出
     */
    void assignActor(@NotNull String actorName, @NotNull String roleName) throws RBACException;

    /**
     * 为参与者分配角色。
     *
     * @param actor 参与者实体
     * @param role  角色实体
     * @throws RBACException 当参与者或角色不存在、或已分配时抛出
     */
    void assignActor(@NotNull Actor actor, @NotNull Role role) throws RBACException;

    /**
     * 根据参与者名称与角色名称撤销参与者的角色分配。
     *
     * @param actorName 参与者名称
     * @param roleName  角色名称
     * @throws RBACException 当参与者或角色不存在、或未分配时抛出
     */
    void deassignActor(@NotNull String actorName, @NotNull String roleName) throws RBACException;

    /**
     * 撤销参与者的角色分配。
     *
     * @param actor 参与者实体
     * @param role  角色实体
     * @throws RBACException 当参与者或角色不存在、或未分配时抛出
     */
    void deassignActor(@NotNull Actor actor, @NotNull Role role) throws RBACException;

    /**
     * 创建新权限项。
     *
     * @param permission 待创建的权限实体
     * @return 已持久化的权限实体
     * @throws RBACException 当权限已存在或数据不合法时抛出
     */
    @NotNull
    Permission addPermission(@NotNull Permission permission) throws RBACException;

    /**
     * 更新指定权限项。
     *
     * @param permName   当前权限名称（自然主键）
     * @param permission 包含新属性的权限实体
     * @return 更新后已持久化的权限实体
     * @throws RBACException 当权限不存在或更新异常时抛出
     */
    @NotNull
    Permission updatePermission(@NotNull String permName, @NotNull Permission permission) throws RBACException;

    /**
     * 根据权限名称删除指定权限。
     *
     * @param permName 待删除权限的名称（自然主键）
     * @throws RBACException 当权限不存在或删除异常时抛出
     */
    void deletePermission(@NotNull String permName) throws RBACException;

    /**
     * 删除指定权限。
     *
     * @param permission 待删除的权限实体
     * @throws RBACException 当权限不存在或删除异常时抛出
     */
    void deletePermission(@NotNull Permission permission) throws RBACException;

    /**
     * 根据权限名称查询单个权限，若不存在则抛出异常。
     *
     * @param permName 待查询的权限名称（自然主键）
     * @return 匹配的权限实体对象（非空）
     * @throws RBACException 当权限不存在或参数校验异常时抛出
     */
    @NotNull
    Permission readPermission(@NotNull String permName) throws RBACException;

    /**
     * 根据权限名称查询单个权限，若不存在则返回 null。
     *
     * @param permName 待查询的权限名称（自然主键）
     * @return 匹配的权限实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    Permission findPermission(@NotNull String permName) throws RBACException;

    /**
     * 根据名称为指定角色授予权限。
     *
     * @param permName 权限名称
     * @param roleName 角色名称
     * @throws RBACException 当角色或权限不存在时抛出
     */
    void grantPermission(@NotNull String permName, @NotNull String roleName) throws RBACException;

    /**
     * 为指定角色授予权限。
     *
     * @param permission 权限实体
     * @param role       角色实体
     * @throws RBACException 当角色或权限不存在时抛出
     */
    void grantPermission(@NotNull Permission permission, @NotNull Role role) throws RBACException;

    /**
     * 根据名称从指定角色撤销权限。
     *
     * @param permName 权限名称
     * @param roleName 角色名称
     * @throws RBACException 当角色或权限不存在时抛出
     */
    void revokePermission(@NotNull String permName, @NotNull String roleName) throws RBACException;

    /**
     * 从指定角色撤销权限。
     *
     * @param permission 权限实体
     * @param role       角色实体
     * @throws RBACException 当角色或权限不存在时抛出
     */
    void revokePermission(@NotNull Permission permission, @NotNull Role role) throws RBACException;

    /**
     * 根据部门名称查询单个部门，若不存在则抛出异常。
     *
     * @param deptName 部门名称（自然主键）
     * @return 匹配的部门实体对象（非空）
     * @throws RBACException 当部门不存在或参数校验异常时抛出
     */
    @NotNull
    Department readDepartment(@NotNull String deptName) throws RBACException;

    /**
     * 根据部门名称查询单个部门，若不存在则返回 null。
     *
     * @param deptName 部门名称（自然主键）
     * @return 匹配的部门实体对象，未找到时返回 null
     * @throws RBACException 当参数校验异常时抛出
     */
    Department findDepartment(@NotNull String deptName) throws RBACException;

    /**
     * 根据部门名称删除指定部门，并清理其用户映射与角色映射。
     *
     * @param deptName 待删除部门的名称（自然主键）
     * @throws RBACException 当部门不存在、存在子部门或删除失败时抛出
     */
    void deleteDepartment(@NotNull String deptName) throws RBACException;

    /**
     * 建立两个角色之间的直接继承偏序关系（NIST Hierarchical AddInheritance 管理函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $AddInheritance(r_{parent}, r_{child}) \implies RH' = RH \cup \{(r_{parent}, r_{child})\}$<br>
     * <b>约束</b>：
     * <ul>
     *   <li>$r_{parent} \neq r_{child}$（不可自身继承）；</li>
     *   <li>$\neg (r_{child} \succeq r_{parent})$（防环路：若 $r_{child}$ 已沿继承链是 $r_{parent}$ 的祖先，则禁止建立，防止死循环）。</li>
     * </ul>
     * </blockquote>
     *
     * @param parentRole 待建立继承关系的上位父角色（继承子角色权限）
     * @param childRole  待建立继承关系的下位子角色
     * @throws RBACException 当角色不存在、继承已存在或形成环路依赖时抛出
     */
    void addRoleInheritance(@NotNull Role parentRole, @NotNull Role childRole) throws RBACException;

    /**
     * 根据角色名称建立两个角色之间的直接继承偏序关系。
     *
     * @param parentRoleName 上位父角色名称（自然主键）
     * @param childRoleName  下位子角色名称（自然主键）
     * @throws RBACException 当角色不存在、继承已存在或形成环路依赖时抛出
     */
    void addRoleInheritance(@NotNull String parentRoleName, @NotNull String childRoleName) throws RBACException;

    /**
     * 解除两个角色之间的直接继承偏序关系（NIST Hierarchical DeleteInheritance 管理函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $DeleteInheritance(r_{parent}, r_{child}) \implies RH' = RH \setminus \{(r_{parent}, r_{child})\}$
     * </blockquote>
     *
     * @param parentRole 上位父角色
     * @param childRole  下位子角色
     * @throws RBACException 当角色不存在或继承关系不存在时抛出
     */
    void deleteRoleInheritance(@NotNull Role parentRole, @NotNull Role childRole) throws RBACException;

    /**
     * 根据角色名称解除两个角色之间的直接继承偏序关系。
     *
     * @param parentRoleName 上位父角色名称（自然主键）
     * @param childRoleName  下位子角色名称（自然主键）
     * @throws RBACException 当角色不存在或继承关系不存在时抛出
     */
    void deleteRoleInheritance(@NotNull String parentRoleName, @NotNull String childRoleName) throws RBACException;
}
