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
package com.minesunny.permission.core.entity;

import com.minesunny.jpa.entity.CachedIdEntity;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import jakarta.persistence.Table;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.*;

import java.util.Calendar;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_role")
@Getter
@Setter
@SQLDelete(sql = "update t_role set delete_at = current_timestamp, version = ?2 where id in (?1) and delete_at is null")
@Where(clause = "delete_at is null")
public class Role extends CachedIdEntity {

    @Column(name = "role_name")
    @Comment(value = "role name")
    private String roleName;

    @Column(name = "description")
    @Comment(value = "detail description of this role")
    private String description;


    @ManyToMany
    @Comment("users has the role")
    @JoinTable(name = "t_role_user_map",
            joinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"))
    @Fetch(FetchMode.SELECT)
    private Set<User> users;

    @Comment(value = "perms  relate to this role")
    @ManyToMany
    @JoinTable(name = "t_role_permission_map",
            joinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id", referencedColumnName = "id"))
    @Fetch(FetchMode.SELECT)
    private Set<Permission> permissions;


    @Column
    private Calendar beginDateTime;

    @Column
    private Calendar endDateTIme;

    @Column
    private Calendar beginLockDateTime;
    @Column
    private Calendar endLockDateTime;
    @Column
    private int dayMask;


    public boolean locked(Calendar now) {
        now = Optional.ofNullable(now).orElse(Calendar.getInstance());
        if (Objects.isNull(beginLockDateTime)) {
            return false;
        }
        if (Objects.isNull(endLockDateTime)) {
            return true;
        }
        return beginLockDateTime.before(now) && endLockDateTime.after(now);
    }

    public boolean locked() {
        return locked(Calendar.getInstance());
    }

    public boolean activated(Calendar now) {
        now = Optional.ofNullable(now).orElse(Calendar.getInstance());
        if (Objects.isNull(beginDateTime)) {
            return !locked(now);
        }
        if (Objects.isNull(endDateTIme)) {
            return false;
        }

        int dayOfWeek = dayMask >> (8 - now.get(Calendar.DAY_OF_WEEK));
        return beginDateTime.before(now) && endDateTIme.after(now) && !locked(now) && dayOfWeek == 1;
    }

    public boolean activated() {
        return activated(Calendar.getInstance());
    }

}
