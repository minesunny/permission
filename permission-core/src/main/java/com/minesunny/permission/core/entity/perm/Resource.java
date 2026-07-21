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
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.SQLDelete;

@Entity
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@DiscriminatorColumn(name = "perm_type")
@Table(name = "t_resource")
@Setter
@Getter
@SQLDelete(sql = "update t_resource set delete_at = current_timestamp,version = ?2 where id in (?1) and delete_at is null")
public class Resource extends CachedIdEntity {


    @Column(name = "resource_name")
    @Comment(value = "resourceName")
    private String resourceName;

    @Column(name = "description")
    @Comment(value = "description resource")
    private String description;

    /**
     * Stable hashCode so a Resource can live in a HashSet across the
     * pre-/post-persist transition (when its id is assigned and the
     * id-based hashCode from {@code BaseEntity} would otherwise change).
     * Equals remains id-based and consistent with this constant hash.
     */
    @Override
    public int hashCode() {
        return Resource.class.hashCode();
    }
}
