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
 * 权限体系中所有"参与者"的公共抽象:User / Post / OrgUnit 均继承自 Actor。
 *
 * 注意: 采用 InheritanceType.JOINED 而非 TABLE_PER_CLASS,
 * 因为 TABLE_PER_CLASS 不支持 @DiscriminatorColumn(会被 JPA 规范忽略)。
 * JOINED 策略下,本类的字段(含 CachedIdEntity 中的 id / delete_at / version)
 * 存储在 t_actor 表, 子类各自的字段存储在自己的表中, 通过 id 外键关联。
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "actor_type")
@Table(name = "t_actor")
@Comment("权限系统参与者(用户/岗位/组织)基类")
@SQLDelete(sql = "update t_actor set delete_at = current_timestamp where id = ? and version = ? and delete_at is null")
@SQLRestriction("delete_at is null")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public  class Actor extends CachedIdEntity {

    @Column(name = "actor_name", unique = true, nullable = false)
    @Comment(value = "actor name, unique across all actor types (user/post/org unit)")
    private String actorName;

    @ManyToMany(mappedBy = "actors", targetEntity = Role.class)
    @Fetch(FetchMode.SELECT)
    private Set<Role> roles;

    @ManyToMany
    @Comment("Roles that this actor has authority to manage and assign")
    @JoinTable(name = com.minesunny.permission.core.type.MapTableConstants.MANAGED_ROLE_ACTOR,
            joinColumns = @JoinColumn(name = "actor_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"))
    @Fetch(FetchMode.SELECT)
    private Set<Role> managedRoles;

}