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
package com.minesunny.permission.core.entity.actor;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.Set;

/**
 * 组织架构部门实体，继承自参与者抽象基类 {@link Actor}。
 * <p>
 * 采用物化路径（Materialized Path）模型维护组织架构树，通过 {@code path} 记录树节点链路（例如 {@code /1/2/5}），
 * 通过 {@code level} 记录当前节点在树中的深度级别（根节点级别为 1，子节点为父节点级别加 1）。
 * 部门与其直属用户之间通过 {@code t_department_user_map} 维护多对多关联。
 */
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_department")
@Comment("组织架构部门信息")
@Setter
@Getter
@SuperBuilder
@DiscriminatorValue("department")
public class Department extends Actor {

    /**
     * 部门名称，在组织架构中唯一。
     */
    @Column(name = "dept_name", unique = true, nullable = false)
    @Comment(value = "department name, unique across organization")
    private String deptName;

    /**
     * 部门物化路径，例如 {@code /1}、{@code /1/2}、{@code /1/2/5}，记录自根节点至当前节点的 ID 链。
     */
    @Column(name = "path", unique = true, nullable = false)
    @Comment(value = "materialized path of this department node, e.g. /1/2/5")
    private String path;

    /**
     * 节点在组织架构树中的层级深度，根节点为 1，子节点依次递增。
     */
    @Column(name = "level", nullable = false)
    @Comment(value = "depth level in the tree hierarchy, starting from 1 for root nodes")
    private Integer level;

    /**
     * 部门详细描述信息。
     */
    @Column(name = "description")
    @Comment(value = "detailed description of this department")
    private String description;

    /**
     * 直属于该部门的用户集合。
     */
    @Comment(value = "users directly assigned to this department")
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = com.minesunny.permission.core.type.MapTableConstants.DEPARTMENT_USER,
            joinColumns = @JoinColumn(name = "department_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"))
    @Fetch(FetchMode.SELECT)
    private Set<User> users;

}
