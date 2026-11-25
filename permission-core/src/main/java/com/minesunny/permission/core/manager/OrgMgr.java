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

import com.minesunny.permission.core.dto.DepartmentTreeNode;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.exception.RBACException;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 组织架构与部门管理业务接口。
 * <p>
 * 提供基于物化路径（Materialized Path）模型的部门生命周期管理、组织架构树查询、
 * 以及部门直属用户与后继子孙用户查询能力。单实体操作与查询统一支持以名称（{@code deptName}）
 * 作为唯一自然主键。
 */
@Transactional
public interface OrgMgr {

    /**
     * 创建新部门并对物化路径及其父路径进行校验。
     * <p>
     * 校验规则如下：
     * <ul>
     *   <li>部门名称 {@code deptName} 与物化路径 {@code path} 必填且全局唯一。</li>
     *   <li>若路径无父级（如 {@code /1}），判定为根节点，其层级 {@code level} 设为 1。</li>
     *   <li>若路径包含多级（如 {@code /1/2/5}），截取最后一个斜杠前的部分作为父路径 {@code pPath}（如 {@code /1/2}），
     *       并校验父部门在数据库中必须已存在；存在时新部门的 {@code level} 自动设为父部门层级加 1。</li>
     * </ul>
     *
     * @param department 待创建的部门实体，必须包含非空的 {@code deptName} 与 {@code path}
     * @return 已保存并持久化的部门实体
     * @throws RBACException 当参数缺失、部门已存在、父路径不存在或数据校验不通过时抛出
     */
    @NotNull
    Department createDepartment(@NotNull Department department) throws RBACException;

    /**
     * 更新指定部门的属性信息。
     *
     * @param deptName 当前待更新部门的部门名称（作为自然主键）
     * @param department 包含更新后属性的部门对象
     * @return 更新后已持久化的部门实体
     * @throws RBACException 当目标部门不存在、新名称已被占用或校验失败时抛出
     */
    @NotNull
    Department updateDepartment(@NotNull String deptName, @NotNull Department department) throws RBACException;

    /**
     * 根据部门名称删除指定部门，并同时清理其在关联映射表中的所有用户关联关系。
     *
     * @param deptName 待删除的部门名称（自然主键），不可为空
     * @throws RBACException 当目标部门不存在、包含子部门或删除操作失败时抛出
     */
    void deleteDepartment(@NotNull String deptName) throws RBACException;

    /**
     * 删除指定部门，并同时清理其在关联映射表中的所有用户关联关系。
     *
     * @param department 待删除的部门实体（需指定 {@code deptName}、{@code id} 或 {@code path}）
     * @throws RBACException 当目标部门不存在、包含子部门或删除操作失败时抛出
     */
    void deleteDepartment(@NotNull Department department) throws RBACException;

    /**
     * 根据部门名称（唯一主键）查询单个部门信息，若不存在则抛出异常。
     *
     * @param deptName 部门名称（自然主键）
     * @return 匹配的部门实体对象（非空）
     * @throws RBACException 当部门不存在或参数为空/校验失败时抛出
     */
    @NotNull
    Department readDepartment(@NotNull String deptName) throws RBACException;

    /**
     * 根据部门实体携带的标识条件查询单个部门信息，若不存在则抛出异常。
     *
     * @param department 携带查询标识条件的部门实体
     * @return 匹配的部门实体对象（非空）
     * @throws RBACException 当部门不存在或查询参数校验失败时抛出
     */
    @NotNull
    Department readDepartment(@NotNull Department department) throws RBACException;

    /**
     * 根据部门名称（唯一主键）查询单个部门信息，若不存在则返回 null。
     *
     * @param deptName 部门名称（自然主键）
     * @return 匹配的部门实体对象，未找到时返回 null
     * @throws RBACException 当参数为空或校验失败时抛出
     */
    Department findDepartment(@NotNull String deptName) throws RBACException;

    /**
     * 根据部门实体携带的标识条件查询单个部门信息，若不存在则返回 null。
     *
     * @param department 携带查询标识条件的部门实体
     * @return 匹配的部门实体对象，未找到时返回 null
     * @throws RBACException 当查询参数校验失败时抛出
     */
    Department findDepartment(@NotNull Department department) throws RBACException;

    /**
     * 根据条件查询部门列表。
     *
     * @param department 包含查询条件的部门实体
     * @return 满足条件的部门列表
     * @throws RBACException 当查询条件为空或执行异常时抛出
     */
    @NotNull
    List<Department> readDepartments(@NotNull Department department) throws RBACException;

    /**
     * 查询全量组织架构树。
     * <p>
     * 通过单次查询获取按层级升序排序的所有部门，并在内存中以 O(N) 复杂度组装为树形结构。
     * 若存在多个顶级根部门，将全部作为列表元素返回。
     *
     * @return 全量组织架构顶级根节点列表（子节点递归嵌套于 {@code children} 中）
     * @throws RBACException 当查询过程中发生数据访问异常时抛出
     */
    @NotNull
    List<DepartmentTreeNode> readDepartmentTree() throws RBACException;

    /**
     * 根据部门名称查询指定部门及其所有后继子孙部门构成的子树。
     *
     * @param deptName 指定的根节点部门名称（自然主键）
     * @return 以指定部门为根的 {@link DepartmentTreeNode} 子树对象
     * @throws RBACException 当目标部门不存在或查询异常时抛出
     */
    @NotNull
    DepartmentTreeNode readDepartmentSubTree(@NotNull String deptName) throws RBACException;

    /**
     * 查询指定部门及其所有后继子孙部门构成的子树。
     *
     * @param department 指定的根节点部门（可通过 {@code deptName}、{@code path} 或 {@code id} 唯一定位）
     * @return 以指定部门为根的 {@link DepartmentTreeNode} 子树对象
     * @throws RBACException 当目标部门不存在或查询异常时抛出
     */
    @NotNull
    DepartmentTreeNode readDepartmentSubTree(@NotNull Department department) throws RBACException;

    /**
     * 将已有用户根据名称关联添加到指定部门中。
     *
     * @param deptName 目标部门名称，必须在系统中已存在
     * @param username 待添加的用户名，必须在系统中已存在
     * @throws RBACException 当部门不存在、用户不存在、或用户已在该部门中时抛出
     */
    void addUser(@NotNull String deptName, @NotNull String username) throws RBACException;

    /**
     * 将已有用户关联添加到已有部门中。
     *
     * @param department 目标部门，必须在系统中已存在
     * @param user 待添加的用户，必须在系统中已存在
     * @throws RBACException 当部门不存在、用户不存在、或用户已在该部门中时抛出
     */
    void addUser(@NotNull Department department, @NotNull User user) throws RBACException;

    /**
     * 将用户从指定部门中移除关联（按名称）。
     *
     * @param deptName 目标部门名称，必须在系统中已存在
     * @param username 待移除的用户名，必须在系统中已存在
     * @throws RBACException 当部门不存在、用户不存在、或用户未加入该部门时抛出
     */
    void removeUser(@NotNull String deptName, @NotNull String username) throws RBACException;

    /**
     * 将用户从指定部门中移除关联。
     *
     * @param department 目标部门，必须在系统中已存在
     * @param user 待移除的用户，必须在系统中已存在
     * @throws RBACException 当部门不存在、用户不存在、或用户未加入该部门时抛出
     */
    void removeUser(@NotNull Department department, @NotNull User user) throws RBACException;

    /**
     * 根据部门名称查询该部门的直接用户列表（不包含子部门用户）。
     *
     * @param deptName 目标部门名称，不可为 {@code null}
     * @return 该部门直属的用户列表，若无用户则返回空列表
     * @throws RBACException 当部门不存在或参数非法时抛出
     */
    @NotNull
    List<User> readDepartmentUsers(@NotNull String deptName) throws RBACException;

    /**
     * 根据部门标识 ID 查询该部门的直接用户列表（不包含子部门用户）。
     *
     * @param departmentId 目标部门标识 ID，不可为 {@code null}
     * @return 该部门直属的用户列表，若无用户则返回空列表
     * @throws RBACException 当部门 ID 不存在或参数非法时抛出
     */
    @NotNull
    List<User> readDepartmentUsers(@NotNull Long departmentId) throws RBACException;

    /**
     * 根据部门名称查询该部门及其所有后继下级部门的汇总用户列表（包含直接用户，结果去重）。
     *
     * @param deptName 目标部门名称，不可为 {@code null}
     * @return 包含该部门及其所有后继子部门成员的去重用户列表
     * @throws RBACException 当部门不存在或参数非法时抛出
     */
    @NotNull
    List<User> readDepartmentSuccessorUsers(@NotNull String deptName) throws RBACException;

    /**
     * 根据部门标识 ID 查询该部门及其所有后继下级部门的汇总用户列表（包含直接用户，结果去重）。
     *
     * @param departmentId 目标部门标识 ID，不可为 {@code null}
     * @return 包含该部门及其所有后继子部门成员的去重用户列表
     * @throws RBACException 当部门 ID 不存在或参数非法时抛出
     */
    @NotNull
    List<User> readDepartmentSuccessorUsers(@NotNull Long departmentId) throws RBACException;

}
