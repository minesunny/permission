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
package com.minesunny.permission.core.repository;

import com.minesunny.permission.core.entity.actor.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.Optional;

public interface UserRepository extends JpaRepositoryImplementation<User, Long> {
    @Query("select u from User u inner join u.roles roles where u.id = :id and roles.id = :id1")
    Optional<User> findByIdAndRoles_Id(@Param("id") @NonNull Long id, @Param("id1") @NonNull Long id1);

    @Query("select u from User u where u.id in :ids")
    Page<User> findByIdIn(@Param("ids") @NonNull Collection<Long> ids, Pageable pageable);

    Optional<User> findByUsername(String username);

}
