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
import com.minesunny.permission.core.type.GenderType;
import com.minesunny.permission.core.type.RegisterType;
import jakarta.persistence.Table;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.*;

import java.util.Date;
import java.util.Set;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_user")
@SQLDelete(sql = "update t_user set delete_at = current_timestamp,version = ?1 where id in (?2) and delete_at is null")
@Where(clause = "delete_at is null")
@Getter
@Setter
public class User extends CachedIdEntity {

    @Column(name = "username")
    @Comment(value = "user login name, unique")
    private String username;


    @Column(name = "description")
    @Comment(value = "description")
    private String description;


    @Column(name = "email")
    @Comment(value = "user email")
    private String email;

    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    @Comment(value = "user gender")
    private GenderType gender;

    @Column(name = "birthday")
    @Comment(value = "user birthday")
    private String birthday;

    @Column(name = "lock_date")
    @Comment(value = "user lock date, null means not locked")
    private Date lockDate;


    @Column(name = "register_ip")
    @Comment(value = "user register ip")
    private String registerIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "register_type")
    private RegisterType registerType;

    @ManyToMany(mappedBy = "users", targetEntity = OrgUnit.class)
    @Fetch(FetchMode.SELECT)
    private Set<OrgUnit> orgUnits;


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "t_user_org_unit_map",
            joinColumns = @JoinColumn(name = "org_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"))
    @Fetch(FetchMode.SELECT)
    private Set<OrgUnit> managerOrgUnits;

    @ManyToOne(targetEntity = Post.class)
    @Fetch(FetchMode.SELECT)
    private Post post;

    @ManyToMany(mappedBy = "users", targetEntity = Role.class)
    @Fetch(FetchMode.SELECT)
    private Set<Role> roles;
}
