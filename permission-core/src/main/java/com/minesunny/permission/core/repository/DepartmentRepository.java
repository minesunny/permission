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

import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 部门数据访问层接口，提供部门实体的增删改查及物化路径树查询能力。
 */
public interface DepartmentRepository extends JpaRepositoryImplementation<Department, Long> {

    /**
     * 根据部门名称查询部门。
     *
     * @param deptName 部门名称
     * @return 包含该部门的 {@link Optional}，未找到时为空
     */
    Optional<Department> findByDeptName(String deptName);

    /**
     * 根据物化路径查询部门。
     *
     * @param path 物化路径，例如 {@code /1/2/5}
     * @return 包含该部门的 {@link Optional}，未找到时为空
     */
    Optional<Department> findByPath(String path);

    /**
     * 查询全量部门，并按层级和标识升序排序。
     *
     * @return 排序后的全量部门列表
     */
    @Query("SELECT d FROM Department d ORDER BY d.level ASC, d.id ASC")
    List<Department> findAllOrderByLevelAscIdAsc();

    /**
     * 根据指定根路径查询该部门及其所有后继子孙部门，按层级和标识升序排序。
     *
     * @param path 目标根路径
     * @return 包含目标部门及其所有后继子部门的列表
     */
    @Query("SELECT d FROM Department d WHERE d.path = :path OR d.path LIKE CONCAT(:path, '/%') ORDER BY d.level ASC, d.id ASC")
    List<Department> findSubTreeByPath(@Param("path") String path);

    /**
     * 查询指定部门的直属用户列表。
     *
     * @param departmentId 部门标识
     * @return 直属于该部门的用户列表
     */
    @Query("SELECT u FROM Department d JOIN d.users u WHERE d.id = :departmentId")
    List<User> findDirectUsersByDepartmentId(@Param("departmentId") Long departmentId);

    /**
     * 根据部门名称查询该部门的直属用户列表。
     *
     * @param deptName 部门名称
     * @return 直属于该部门的用户列表
     */
    @Query("SELECT u FROM Department d JOIN d.users u WHERE d.deptName = :deptName")
    List<User> findDirectUsersByDeptName(@Param("deptName") String deptName);

    /**
     * 查询指定路径部门及其所有后继子孙部门的汇总用户列表（去重）。
     *
     * @param path 目标部门物化路径
     * @return 去重后的汇总后继用户列表
     */
    @Query("SELECT DISTINCT u FROM Department d JOIN d.users u WHERE d.path = :path OR d.path LIKE CONCAT(:path, '/%')")
    List<User> findSuccessorUsersByPath(@Param("path") String path);

}
