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

import com.minesunny.permission.core.entity.Role;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoleRepository extends JpaRepositoryImplementation<Role, Long> {

    Optional<Role> findByRoleName(String roleName);

    /**
     * 单 SQL JOIN 快速核验主体（Actor）是否被直接授予了指定角色。
     *
     * @param actorId  主体 ID
     * @param roleName 角色名称
     * @return 若分配了该角色且角色未删除返回 true，否则返回 false
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM t_role_actor_map ra
                JOIN t_role r ON ra.role_id = r.id AND r.delete_at IS NULL
                WHERE ra.actor_id = :actorId
                  AND r.role_name = :roleName
            )
            """, nativeQuery = true)
    boolean hasRoleFast(@Param("actorId") Long actorId, @Param("roleName") String roleName);
}
