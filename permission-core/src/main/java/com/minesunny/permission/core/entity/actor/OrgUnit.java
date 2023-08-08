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
import com.minesunny.permission.core.type.OrgType;
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

import java.util.Set;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_org_unit")
@Getter
@Setter
@SQLDelete(sql = "update t_org_unit set delete_at = current_timestamp,version = ?1 where id in (?2) and delete_at is null")
public class OrgUnit extends CachedIdEntity {

    @Column(name = "org_name")
    @Comment(value = "org name")
    private String orgName;


    @Column(name = "description")
    @Comment(value = "detail description of this dept")
    private String description;


    @Column(name = "create_user_id")
    @Comment(value = "the user id which create this dept")
    private Long createUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "org_type")
    private OrgType orgType;


    @ManyToMany(mappedBy = "managerOrgUnits", targetEntity = User.class)
    @Fetch(FetchMode.SELECT)
    private Set<User> managerUsers;

    @ManyToMany
    @JoinTable(name = "t_org_unit_user_map",
            joinColumns = @JoinColumn(name = "org_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"))
    @Fetch(FetchMode.SELECT)
    private Set<User> users;


}
