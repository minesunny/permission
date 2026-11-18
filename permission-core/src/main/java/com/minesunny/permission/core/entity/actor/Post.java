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
import jakarta.persistence.Table;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.*;
import jakarta.persistence.DiscriminatorValue;

import java.util.Set;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_post")
@Comment("岗位信息")
@Setter
@Getter
@SuperBuilder
@DiscriminatorValue("post")
public class Post extends Actor {


    @Column(name = "post_name", unique = true, nullable = false)
    @Comment(value = "post name")
    private String postName;


    @Column(name = "description")
    @Comment(value = "detail description of this post")
    private String description;


    @Comment(value = "users in this dept")
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "t_post_user_map",
            joinColumns = @JoinColumn(name = "post_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"))
    @Fetch(FetchMode.SELECT)
    private Set<User> users;

}
