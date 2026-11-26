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
package com.minesunny.permission.core.entity.perm;

import com.minesunny.jpa.entity.CachedIdEntity;
import com.minesunny.permission.core.entity.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.Set;

/**
 * RBAC 权限与访问控制策略实体（NIST / ANSI INCITS 359-2004 与 Extended Policy 规范）。
 * <p>
 * <b>标准理论定义（NIST RBAC Core Specification）</b>：
 * <blockquote>
 * "A <b>Permission</b> is an approval to perform an operation on one or more protected objects (resources).<br>
 * Formally, $PRMS = 2^{(OPS \times OBS)}$, where an operation $op \in OPS$ is an executable program/action,
 * and an object $ob \in OBS$ is an entity that contains or receives information."
 * </blockquote>
 * 在企业级策略框架（XACML Profile / Privacy-Aware RBAC）中，扩展支持包含 Policy 效力、环境谓词、合规目的与后置义务的完整控制能力。
 *
 * <h3>NIST RBAC 规范与实体属性/方法映射表</h3>
 * <table border="1">
 *   <caption>Permission 实体映射对照表</caption>
 *   <tr>
 *     <th>属性 / 方法</th>
 *     <th>NIST 规范术语 / 数学定义</th>
 *     <th>语义说明</th>
 *   </tr>
 *   <tr>
 *     <td>{@link #permName}</td>
 *     <td>$p \in PRMS$ (Natural Identity Key)</td>
 *     <td>权限全局唯一自然主键标识，纯粹作为命名索引</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #resource}</td>
 *     <td>$ob \in OBS$ (Target / Object)</td>
 *     <td>受保护的目标资源对象或资源标识（如 API 路由、数据库表或数据实体）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #operation}</td>
 *     <td>$op \in OPS$ (Operation / Action)</td>
 *     <td>对目标资源允许执行的具体操作动作（如 READ, WRITE, EXECUTE, DELETE 等）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #effect}</td>
 *     <td>$Effect \in \{ALLOW, DENY\}$ (Policy Rule Effect)</td>
 *     <td>策略判定效力：正向授权（ALLOW）或显式排斥（DENY），支持拒绝优先合并</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #condition}</td>
 *     <td>$Condition: Context \rightarrow \{true, false\}$ (Predicate Rule)</td>
 *     <td>策略动态环境谓词与行级数据过滤规则表达式（如 SpEL 或 JSON 策略条件）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #purpose}</td>
 *     <td>$Purpose \in PURPOSES$ (NIST 第 7 章 Privacy RBAC)</td>
 *     <td>合规与隐私保护目的（如业务目的 "Treatment" 或数据用途 "Billing"）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #obligation}</td>
 *     <td>$Obligations \subseteq OBLS$ (XACML / Privacy RBAC)</td>
 *     <td>授权通过后的强制后置执行义务动作指令（如数据脱敏、审计告警）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #roles}</td>
 *     <td>$\{r \in ROLES \mid (r, p) \in PA\}$</td>
 *     <td>授予该权限的角色集合，对应多对多关联中间表映射</td>
 *   </tr>
 * </table>
 */
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_permission")
@Getter
@Setter
@SuperBuilder
@SQLDelete(sql = "update t_permission set delete_at = current_timestamp where id = ? and version = ? and delete_at is null")
@SQLRestriction("delete_at is null")
public class Permission extends CachedIdEntity {

    /**
     * 权限全局唯一名称（自然主键）。
     * <p>
     * <b>NIST 规范数学定义</b>：$p \in PRMS$。
     */
    @Column(name = "perm_name", unique = true, nullable = false)
    @Comment(value = "perm name, unique natural key")
    private String permName;

    /**
     * 权限详细功能描述。
     */
    @Column(name = "description")
    @Comment(value = "detail description of this perm")
    private String description;

    /**
     * 受保护的资源标识或资源类型。
     * <p>
     * <b>NIST 规范数学定义</b>：$ob \in OBS$ (Target Object)。
     */
    @Column(name = "resource")
    @Comment(value = "permission resource object")
    private String resource;

    /**
     * 对该资源允许执行的操作行为。
     * <p>
     * <b>NIST 规范数学定义</b>：$op \in OPS$ (Operation)。
     */
    @Column(name = "operation")
    @Comment(value = "operation defined on the resource")
    private String operation;

    /**
     * 权限类型分类（如 API, BUTTON, MENU, DATA 等）。
     */
    @Column(name = "perm_type")
    @Comment(value = "permission type")
    private String permType;

    /**
     * 策略规则效力：{@code "ALLOW"}（正向授予，默认）或 {@code "DENY"}（显式排斥）。
     * <p>
     * <b>NIST / XACML 规范数学定义</b>：$Effect \in \{ALLOW, DENY\}$。
     */
    @Builder.Default
    @Column(name = "effect", nullable = false)
    @Comment(value = "policy rule effect: ALLOW (positive authorization) or DENY (explicit exclusion)")
    private String effect = "ALLOW";

    /**
     * 策略动态环境谓词或条件约束表达式（如数据行级范围过滤、SpEL 规则或 JSON 策略）。
     * <p>
     * <b>NIST 规范数学定义</b>：$Condition: Context \rightarrow \{true, false\}$。
     */
    @Column(name = "condition_rule", length = 1024)
    @Comment(value = "dynamic policy condition or predicate expression for fine-grained authorization")
    private String condition;

    /**
     * 业务访问目的（NIST 第 7 章 Privacy RBAC：Business Purpose / Data Purpose，如 "Treatment", "Billing", "Audit"）。
     * <p>
     * <b>NIST 规范数学定义</b>：$Purpose \in PURPOSES$。
     */
    @Column(name = "purpose")
    @Comment(value = "business purpose or data usage purpose for regulatory compliance")
    private String purpose;

    /**
     * 授权判定通过后的强制后置执行义务（Obligation，如脱敏指令、风控告警、特权日志记录等）。
     * <p>
     * <b>NIST / XACML 规范数学定义</b>：$Obligations \subseteq OBLS$。
     */
    @Column(name = "obligation")
    @Comment(value = "post-enforcement obligation action attached to the permission")
    private String obligation;

    /**
     * 拥有该权限的角色集合。
     * <p>
     * <b>NIST 规范数学定义</b>：$GrantedRoles(p) = \{r \in ROLES \mid (r, p) \in PA\}$。
     */
    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "permissions", targetEntity = Role.class)
    private Set<Role> roles;
}
