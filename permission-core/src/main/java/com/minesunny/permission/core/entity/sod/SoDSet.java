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
package com.minesunny.permission.core.entity.sod;

import com.minesunny.jpa.entity.CachedIdEntity;
import com.minesunny.permission.core.entity.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.Set;

/**
 * 职责分离（Separation of Duty, SoD）约束实体基类（NIST / ANSI INCITS 359-2004 规范）。
 * <p>
 * <b>标准理论定义（NIST RBAC SoD Specification）</b>：
 * <blockquote>
 * "A Separation of Duty relation consists of a triplet $(name, role\_set, cardinality)$,
 * where $name$ identifies the business transaction/conflict of interest boundary,<br>
 * $role\_set \subseteq ROLES$ constitutes the mutually exclusive roles, and<br>
 * $cardinality \ge 2$ designates the maximum number of conflicting roles allowed simultaneously."
 * </blockquote>
 *
 * <h3>NIST RBAC 规范与实体属性映射表</h3>
 * <table border="1">
 *   <caption>SoD 实体映射对照表</caption>
 *   <tr>
 *     <th>属性</th>
 *     <th>NIST 规范术语 / 数学定义</th>
 *     <th>语义说明</th>
 *   </tr>
 *   <tr>
 *     <td>{@link #name}</td>
 *     <td>$name \in NAMES$</td>
 *     <td>职责分离约束集的业务唯一名称标识（自然主键）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #cardinality}</td>
 *     <td>$n \in \mathbb{N}^+, 2 \le n \le |role\_set|$</td>
 *     <td>冲突角色基数阈值：共同拥有的角色数必须严格小于该基数，默认为 2（两两互斥）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #roles}</td>
 *     <td>$role\_set \subseteq ROLES$</td>
 *     <td>受此职责分离约束管辖的互斥角色集合</td>
 *   </tr>
 * </table>
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "sod_type")
@Table(name = "t_sod_set")
@Comment("职责分离约束(SoD)基类")
@SQLDelete(sql = "update t_sod_set set delete_at = current_timestamp where id = ? and version = ? and delete_at is null")
@SQLRestriction("delete_at is null")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public abstract class SoDSet extends CachedIdEntity {

    /**
     * 职责分离约束集名称（自然主键，全局唯一）。
     * <p>
     * <b>NIST 规范数学定义</b>：$SSD\_Set\_Name \in NAMES$ 或 $DSD\_Set\_Name \in NAMES$。
     */
    @Column(name = "name", unique = true, nullable = false)
    @Comment(value = "unique name of the SoD constraint set")
    private String name;

    /**
     * 详细业务描述说明。
     */
    @Column(name = "description")
    @Comment(value = "description of the conflict of interest / business process")
    private String description;

    /**
     * 冲突基数阈值（Cardinality）。
     * <p>
     * <b>NIST 规范数学定义</b>：$n \in \mathbb{N}^+ (n \ge 2)$。
     * 同一主体/会话所拥有的受约束角色数必须满足 $|AssignedRoles \cap role\_set| < n$。
     * 当 $n=2$ 时表示两两完全互斥（至多拥有 1 个角色）。
     */
    @Column(name = "cardinality", nullable = false)
    @Comment(value = "cardinality limit, must be >= 2")
    private Integer cardinality;

    /**
     * 该 SoD 约束集所包含的互斥角色集合。
     * <p>
     * <b>NIST 规范数学定义</b>：$role\_set \subseteq ROLES$。
     */
    @ManyToMany
    @JoinTable(name = "t_sod_role_map",
            joinColumns = @JoinColumn(name = "sod_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"))
    @Fetch(FetchMode.SELECT)
    private Set<Role> roles;
}
