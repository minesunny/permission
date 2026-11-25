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
import com.minesunny.permission.core.entity.sod.DSDSet;
import com.minesunny.permission.core.entity.sod.SSDSet;
import com.minesunny.permission.core.exception.RBACException;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * NIST RBAC 约束职责分离（Separation of Duty, SoD）管理与审查业务接口。
 * <p>
 * 全面涵盖 ANSI INCITS 359-2004 标准中的静态职责分离（SSD）与动态职责分离（DSD）管理函数与审查函数：
 * <ul>
 *     <li><b>SSD 管理函数</b>：{@code CreateSsdSet}, {@code DeleteSsdSet}, {@code AddSsdRoleMember}, {@code DeleteSsdRoleMember}, {@code SetSsdSetCardinality}</li>
 *     <li><b>SSD 审查函数</b>：{@code SsdRoleSets}, {@code SsdRoleSetRoles}, {@code SsdRoleSetCardinality}</li>
 *     <li><b>DSD 管理函数</b>：{@code CreateDsdSet}, {@code DeleteDsdSet}, {@code AddDsdRoleMember}, {@code DeleteDsdRoleMember}, {@code SetDsdSetCardinality}</li>
 *     <li><b>DSD 审查函数</b>：{@code DsdRoleSets}, {@code DsdRoleSetRoles}, {@code DsdRoleSetCardinality}</li>
 * </ul>
 */
@Transactional
public interface SoDMgr {

    // -------------------------------------------------------------------------
    // NIST SSD 管理函数 (SSD Administrative Commands)
    // -------------------------------------------------------------------------

    /**
     * 创建静态职责分离 (SSD) 约束集合（NIST CreateSsdSet 函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $CreateSsdSet(ssd\_name, role\_set, n) \implies SSD' = SSD \cup \{(ssd\_name, role\_set, n)\}$<br>
     * 约束前置条件：$ssd\_name \notin \pi_{name}(SSD) \land n \ge 2 \land n \le |role\_set|$。
     * </blockquote>
     *
     * @param ssdName     SSD 约束集唯一标识名称（自然主键）
     * @param roleNames   互斥角色名称集合
     * @param cardinality 冲突基数阈值（需满足 $2 \le n \le |role\_set|$）
     * @return 已持久化的 SSD 规则集合实体
     * @throws RBACException 当集合已存在、参数非法或角色不存在时抛出
     */
    @NotNull
    SSDSet createSSDSet(@NotNull String ssdName, @NotNull Set<String> roleNames, int cardinality) throws RBACException;

    /**
     * 删除指定的静态职责分离 (SSD) 约束集合（NIST DeleteSsdSet 函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $DeleteSsdSet(ssd\_name) \implies SSD' = SSD \setminus \{(ssd\_name, role\_set, n)\}$
     * </blockquote>
     *
     * @param ssdName SSD 约束集名称（自然主键）
     * @throws RBACException 当指定集合不存在时抛出
     */
    void deleteSSDSet(@NotNull String ssdName) throws RBACException;

    /**
     * 向指定的 SSD 约束集合添加一个互斥角色成员（NIST AddSsdRoleMember 函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $AddSsdRoleMember(ssd\_name, role) \implies role\_set' = role\_set \cup \{role\}$
     * </blockquote>
     *
     * @param ssdName  SSD 约束集名称
     * @param roleName 待加入的角色名称
     * @throws RBACException 当集合不存在、角色不存在或角色已在集合中时抛出
     */
    void addSSDRoleMember(@NotNull String ssdName, @NotNull String roleName) throws RBACException;

    /**
     * 从指定的 SSD 约束集合移除一个互斥角色成员（NIST DeleteSsdRoleMember 函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $DeleteSsdRoleMember(ssd\_name, role) \implies role\_set' = role\_set \setminus \{role\}$<br>
     * 约束前置条件：$|role\_set'| \ge cardinality$。
     * </blockquote>
     *
     * @param ssdName  SSD 约束集名称
     * @param roleName 待移除的角色名称
     * @throws RBACException 当集合不存在、角色非成员或移除后导致角色数小于基数时抛出
     */
    void deleteSSDRoleMember(@NotNull String ssdName, @NotNull String roleName) throws RBACException;

    /**
     * 更新指定 SSD 约束集合的基数阈值（NIST SetSsdSetCardinality 函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $SetSsdSetCardinality(ssd\_name, n) \implies cardinality' = n \quad (2 \le n \le |role\_set|)$
     * </blockquote>
     *
     * @param ssdName     SSD 约束集名称
     * @param cardinality 新的基数阈值
     * @throws RBACException 当集合不存在或基数不满足 $2 \le n \le |role\_set|$ 时抛出
     */
    void setSSDSetCardinality(@NotNull String ssdName, int cardinality) throws RBACException;

    // -------------------------------------------------------------------------
    // NIST SSD 审查函数 (SSD Supporting Review Functions)
    // -------------------------------------------------------------------------

    /**
     * 查询系统中定义的所有 SSD 约束集合（NIST SsdRoleSets 函数）。
     *
     * @return 全量 SSD 集合列表
     */
    @NotNull
    List<SSDSet> ssdRoleSets();

    /**
     * 获取指定 SSD 集合中的所有互斥角色成员（NIST SsdRoleSetRoles 函数）。
     *
     * @param ssdName SSD 约束集名称
     * @return 互斥角色实体集合
     * @throws RBACException 当集合不存在时抛出
     */
    @NotNull
    Set<Role> ssdRoleSetRoles(@NotNull String ssdName) throws RBACException;

    /**
     * 获取指定 SSD 集合的基数阈值（NIST SsdRoleSetCardinality 函数）。
     *
     * @param ssdName SSD 约束集名称
     * @return 基数阈值 $n$
     * @throws RBACException 当集合不存在时抛出
     */
    int ssdRoleSetCardinality(@NotNull String ssdName) throws RBACException;

    // -------------------------------------------------------------------------
    // NIST DSD 管理函数 (DSD Administrative Commands)
    // -------------------------------------------------------------------------

    /**
     * 创建动态职责分离 (DSD) 约束集合（NIST CreateDsdSet 函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $CreateDsdSet(dsd\_name, role\_set, n) \implies DSD' = DSD \cup \{(dsd\_name, role\_set, n)\}$<br>
     * 约束前置条件：$dsd\_name \notin \pi_{name}(DSD) \land n \ge 2 \land n \le |role\_set|$。
     * </blockquote>
     *
     * @param dsdName     DSD 约束集唯一标识名称（自然主键）
     * @param roleNames   互斥角色名称集合
     * @param cardinality 冲突基数阈值（需满足 $2 \le n \le |role\_set|$）
     * @return 已持久化的 DSD 规则集合实体
     * @throws RBACException 当集合已存在、参数非法或角色不存在时抛出
     */
    @NotNull
    DSDSet createDSDSet(@NotNull String dsdName, @NotNull Set<String> roleNames, int cardinality) throws RBACException;

    /**
     * 删除指定的动态职责分离 (DSD) 约束集合（NIST DeleteDsdSet 函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $DeleteDsdSet(dsd\_name) \implies DSD' = DSD \setminus \{(dsd\_name, role\_set, n)\}$
     * </blockquote>
     *
     * @param dsdName DSD 约束集名称（自然主键）
     * @throws RBACException 当指定集合不存在时抛出
     */
    void deleteDSDSet(@NotNull String dsdName) throws RBACException;

    /**
     * 向指定的 DSD 约束集合添加一个互斥角色成员（NIST AddDsdRoleMember 函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $AddDsdRoleMember(dsd\_name, role) \implies role\_set' = role\_set \cup \{role\}$
     * </blockquote>
     *
     * @param dsdName  DSD 约束集名称
     * @param roleName 待加入的角色名称
     * @throws RBACException 当集合不存在、角色不存在或角色已在集合中时抛出
     */
    void addDSDRoleMember(@NotNull String dsdName, @NotNull String roleName) throws RBACException;

    /**
     * 从指定的 DSD 约束集合移除一个互斥角色成员（NIST DeleteDsdRoleMember 函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $DeleteDsdRoleMember(dsd\_name, role) \implies role\_set' = role\_set \setminus \{role\}$<br>
     * 约束前置条件：$|role\_set'| \ge cardinality$。
     * </blockquote>
     *
     * @param dsdName  DSD 约束集名称
     * @param roleName 待移除的角色名称
     * @throws RBACException 当集合不存在、角色非成员或移除后导致角色数小于基数时抛出
     */
    void deleteDSDRoleMember(@NotNull String dsdName, @NotNull String roleName) throws RBACException;

    /**
     * 更新指定 DSD 约束集合的基数阈值（NIST SetDsdSetCardinality 函数）。
     * <p>
     * <b>NIST 规范形式化定义</b>：
     * <blockquote>
     * $SetDsdSetCardinality(dsd\_name, n) \implies cardinality' = n \quad (2 \le n \le |role\_set|)$
     * </blockquote>
     *
     * @param dsdName     DSD 约束集名称
     * @param cardinality 新的基数阈值
     * @throws RBACException 当集合不存在或基数不满足 $2 \le n \le |role\_set|$ 时抛出
     */
    void setDSDSetCardinality(@NotNull String dsdName, int cardinality) throws RBACException;

    // -------------------------------------------------------------------------
    // NIST DSD 审查函数 (DSD Supporting Review Functions)
    // -------------------------------------------------------------------------

    /**
     * 查询系统中定义的所有 DSD 约束集合（NIST DsdRoleSets 函数）。
     *
     * @return 全量 DSD 集合列表
     */
    @NotNull
    List<DSDSet> dsdRoleSets();

    /**
     * 获取指定 DSD 集合中的所有互斥角色成员（NIST DsdRoleSetRoles 函数）。
     *
     * @param dsdName DSD 约束集名称
     * @return 互斥角色实体集合
     * @throws RBACException 当集合不存在时抛出
     */
    @NotNull
    Set<Role> dsdRoleSetRoles(@NotNull String dsdName) throws RBACException;

    /**
     * 获取指定 DSD 集合的基数阈值（NIST DsdRoleSetCardinality 函数）。
     *
     * @param dsdName DSD 约束集名称
     * @return 基数阈值 $n$
     * @throws RBACException 当集合不存在时抛出
     */
    int dsdRoleSetCardinality(@NotNull String dsdName) throws RBACException;
}
