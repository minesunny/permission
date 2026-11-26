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

import com.minesunny.permission.core.entity.perm.Permission;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PermissionRepository extends JpaRepositoryImplementation<Permission, Long> {

    Optional<Permission> findByPermName(String permName);

    /**
     * 单 SQL JOIN 快速核验主体（Actor）是否通过直接分配的角色拥有指定权限。
     * 避免多层中间表查询以及实体反复水合，零 Entity 实例化开销。
     *
     * @param actorId  主体 ID
     * @param permName 权限名称（自然键）
     * @return 若存在有效授权链路返回 true，否则返回 false
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM t_role_actor_map ra
                JOIN t_role r ON ra.role_id = r.id AND r.delete_at IS NULL
                JOIN t_role_permission_map rp ON r.id = rp.role_id
                JOIN t_permission p ON rp.permission_id = p.id AND p.delete_at IS NULL
                WHERE ra.actor_id = :actorId
                  AND p.perm_name = :permName
                  AND (r.begin_date_time IS NULL OR r.begin_date_time <= CURRENT_TIMESTAMP)
                  AND (r.end_date_time IS NULL OR r.end_date_time >= CURRENT_TIMESTAMP)
            )
            """, nativeQuery = true)
    boolean hasPermissionFast(@Param("actorId") Long actorId, @Param("permName") String permName);

    /**
     * 单 SQL JOIN 快速查询主体（Actor）直接被授予的角色所包含的所有有效权限。
     * 将 3 次分段查询与多重 IN 拼接聚合为单个 SQL JOIN。
     *
     * @param actorId 主体 ID
     * @return 权限实体列表
     */
    @Query(value = """
            SELECT DISTINCT p.*
            FROM t_role_actor_map ra
            JOIN t_role r ON ra.role_id = r.id AND r.delete_at IS NULL
            JOIN t_role_permission_map rp ON r.id = rp.role_id
            JOIN t_permission p ON rp.permission_id = p.id AND p.delete_at IS NULL
            WHERE ra.actor_id = :actorId
              AND (r.begin_date_time IS NULL OR r.begin_date_time <= CURRENT_TIMESTAMP)
              AND (r.end_date_time IS NULL OR r.end_date_time >= CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    java.util.List<Permission> findPermissionsByActorIdFast(@Param("actorId") Long actorId);

    /**
     * 单 SQL JOIN 快速核验会话（Session）中已激活的角色是否拥有指定操作和资源。
     * 支持 Deny-Overrides 评估前的直接匹配。
     *
     * @param roleIds   会话中当前激活的角色 ID 列表
     * @param operation 操作
     * @param resource  资源
     * @param effect    策略效力 (ALLOW / DENY)
     * @return 是否存在匹配策略
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM t_role_permission_map rp
                JOIN t_permission p ON rp.permission_id = p.id AND p.delete_at IS NULL
                WHERE rp.role_id IN (:roleIds)
                  AND p.operation = :operation
                  AND p.resource = :resource
                  AND p.effect = :effect
            )
            """, nativeQuery = true)
    boolean hasSessionPermissionFast(
            @Param("roleIds") java.util.Collection<Long> roleIds,
            @Param("operation") String operation,
            @Param("resource") String resource,
            @Param("effect") String effect);
}