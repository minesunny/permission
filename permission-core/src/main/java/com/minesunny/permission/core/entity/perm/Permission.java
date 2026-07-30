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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.util.Set;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_permission")
@Getter
@Setter
@SuperBuilder
@SQLDelete(sql = "update t_permission set delete_at = current_timestamp, version = ?2 where id in (?1) and delete_at is null")
@Where(clause = "delete_at is null")
public class Permission extends CachedIdEntity {


    @Column(name = "perm_name", unique = true, nullable = false)
    @Comment(value = "perm name")
    private String permName;

    @Column(name = "description")
    @Comment(value = "detail description of this perm")
    private String description;

    @Column(name = "resource")
    @Comment(value = "permission resource")
    private String resource;

    @Column(name = "operation")
    @Comment(value = "define what operation of the resource")
    private String operation;

    @Column(name = "perm_type")
    @Comment(value = "perm type")
    private String permType;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "permissions",
            targetEntity = Role.class)
    private Set<Role> roles;

    @Transient
    private Policy policy;

    @Transient
    private Long policyOwnerId;
}
