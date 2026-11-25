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
package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.MineSpecification;
import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.jpa.manager.MapMgr;
import com.minesunny.permission.core.dto.DepartmentTreeNode;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.exception.*;
import com.minesunny.permission.core.i18n.RBACMessageKeys;
import com.minesunny.permission.core.manager.OrgMgr;
import com.minesunny.permission.core.repository.ActorRepository;
import com.minesunny.permission.core.repository.DepartmentRepository;
import com.minesunny.permission.core.util.DepartmentPathUtils;
import com.minesunny.permission.core.util.DepartmentTreeUtils;
import com.minesunny.permission.core.util.NaturalKeys;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

import static com.minesunny.permission.core.type.MapTableConstants.*;

/**
 * 组织架构与部门管理业务实现类。
 * <p>
 * 基于物化路径（Materialized Path）实现部门层级校验、整树与子树高效检索、
 * 以及直属用户与后继子孙用户的查询与成员管理。
 */
@Service
@Primary
@RequiredArgsConstructor
public class OrgMgrImpl implements OrgMgr {

    private final DepartmentRepository departmentRepository;
    private final ActorRepository actorRepository;
    private final MapMgr mapMgr;

    // -------------------------------------------------------------------------
    // Department CRUD
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Department createDepartment(@NotNull Department department) throws RBACException {
        NaturalKeys.requireDeptName(department);
        NaturalKeys.requirePath(department);

        String path = DepartmentPathUtils.normalizePath(department.getPath());

        if (departmentRepository.findByDeptName(department.getDeptName()).isPresent()) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_DEPARTMENT_EXISTS,
                    "createDepartment", department.getDeptName());
        }

        if (departmentRepository.findByPath(path).isPresent()) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_DEPARTMENT_PATH_EXISTS,
                    "createDepartment", path);
        }

        String pPath = DepartmentPathUtils.extractParentPath(path);
        if (pPath == null) {
            // 根部门
            department.setLevel(1);
        } else {
            // 子部门，查询父部门是否存在
            Department parent = departmentRepository.findByPath(pPath)
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_PARENT_DEPARTMENT,
                            "createDepartment", pPath));
            department.setLevel(parent.getLevel() + 1);
        }

        Department newDept = new Department();
        BeanUtils.copyProperties(department, newDept, "users");
        newDept.setPath(path);
        if (!StringUtils.hasText(newDept.getActorName())) {
            newDept.setActorName(department.getDeptName());
        }

        try {
            return departmentRepository.saveAndFlush(newDept);
        } catch (DataIntegrityViolationException e) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_DEPARTMENT_ARCHIVED, e,
                    "createDepartment", department.getDeptName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Department updateDepartment(@NotNull String deptName, @NotNull Department department) throws RBACException {
        Department storeDept = departmentRepository.findByDeptName(deptName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                        "updateDepartment", deptName));

        String newDeptName = department.getDeptName();
        if (StringUtils.hasText(newDeptName) && !newDeptName.equals(deptName)
                && departmentRepository.findByDeptName(newDeptName).isPresent()) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_DEPARTMENT_EXISTS,
                    "updateDepartment", newDeptName);
        }

        String newPath = department.getPath();
        if (StringUtils.hasText(newPath)) {
            newPath = DepartmentPathUtils.normalizePath(newPath);
            String oldPath = storeDept.getPath();
            if (!newPath.equals(oldPath)) {
                // 1. 防环校验：新路径不能位于原路径的子树分支之下（即不能把节点移动到自身子孙节点下）
                if (newPath.startsWith(oldPath + "/")) {
                    throw new RBACTopologyException(
                            RBACMessageKeys.TOPOLOGY_DESCENDANT_MOVEMENT,
                            "updateDepartment", deptName, newPath);
                }

                // 2. 检查新路径是否已被占用
                if (departmentRepository.findByPath(newPath).isPresent()) {
                    throw new RBACConflictException(
                            RBACMessageKeys.CONFLICT_DEPARTMENT_PATH_EXISTS,
                            "updateDepartment", newPath);
                }

                // 3. 计算新层级
                String pPath = DepartmentPathUtils.extractParentPath(newPath);
                int newLevel;
                if (pPath == null) {
                    newLevel = 1;
                } else {
                    Department parent = departmentRepository.findByPath(pPath)
                            .orElseThrow(() -> new RBACNotFoundException(
                                    RBACMessageKeys.NOT_FOUND_PARENT_DEPARTMENT,
                                    "updateDepartment", pPath));
                    newLevel = parent.getLevel() + 1;
                }
                int levelDelta = newLevel - storeDept.getLevel();

                // 4. 级联更新该部门下所有的子孙部门
                List<Department> subTree = departmentRepository.findSubTreeByPath(oldPath);
                List<Department> descendants = new ArrayList<>();
                for (Department child : subTree) {
                    if (child.getId().equals(storeDept.getId())) {
                        continue;
                    }
                    String suffix = child.getPath().substring(oldPath.length());
                    String childNewPath = newPath + suffix;
                    if (departmentRepository.findByPath(childNewPath).isPresent()) {
                        throw new RBACConflictException(
                                RBACMessageKeys.CONFLICT_DESCENDANT_PATH_EXISTS,
                                "updateDepartment", childNewPath);
                    }
                    child.setPath(childNewPath);
                    child.setLevel(child.getLevel() + levelDelta);
                    descendants.add(child);
                }
                if (!descendants.isEmpty()) {
                    departmentRepository.saveAll(descendants);
                }

                storeDept.setLevel(newLevel);
                storeDept.setPath(newPath);
            }
        }

        List<String> excludes = new ArrayList<>(List.of("id", "path", "level", "users"));
        if (!StringUtils.hasText(newDeptName)) {
            excludes.add("deptName");
            excludes.add("actorName");
        }
        BeanUtils.copyProperties(department, storeDept, excludes.toArray(new String[0]));
        if (StringUtils.hasText(newDeptName)) {
            storeDept.setDeptName(newDeptName);
            storeDept.setActorName(newDeptName);
        }
        return departmentRepository.saveAndFlush(storeDept);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteDepartment(@NotNull String deptName) throws RBACException {
        NaturalKeys.require(deptName, "department's deptName");
        Department storeDept = departmentRepository.findByDeptName(deptName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                        "deleteDepartment", deptName));

        // 校验是否存在子部门
        List<Department> subTree = departmentRepository.findSubTreeByPath(storeDept.getPath());
        if (subTree.size() > 1) {
            throw new RBACTopologyException(
                    RBACMessageKeys.TOPOLOGY_CHILD_DEPARTMENTS,
                    "deleteDepartment", storeDept.getDeptName());
        }

        // 清理部门用户映射与角色映射
        Map<String, List<Long>> deptMap = new HashMap<>();
        deptMap.put("department_id", List.of(storeDept.getId()));
        Map<String, List<Long>> actorMap = new HashMap<>();
        actorMap.put("actor_id", List.of(storeDept.getId()));
        try {
            mapMgr.delete(DEPARTMENT_USER, deptMap);
            mapMgr.delete(ROLE_ACTOR, actorMap);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }

        departmentRepository.delete(storeDept);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteDepartment(@NotNull Department department) throws RBACException {
        Department storeDept = readDepartment(department);
        List<Department> subTree = departmentRepository.findSubTreeByPath(storeDept.getPath());
        if (subTree.size() > 1) {
            throw new RBACTopologyException(
                    RBACMessageKeys.TOPOLOGY_CHILD_DEPARTMENTS,
                    "deleteDepartment", storeDept.getDeptName());
        }

        Map<String, List<Long>> deptMap = new HashMap<>();
        deptMap.put("department_id", List.of(storeDept.getId()));
        Map<String, List<Long>> actorMap = new HashMap<>();
        actorMap.put("actor_id", List.of(storeDept.getId()));
        try {
            mapMgr.delete(DEPARTMENT_USER, deptMap);
            mapMgr.delete(ROLE_ACTOR, actorMap);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }

        departmentRepository.delete(storeDept);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Department findDepartment(@NotNull String deptName) throws RBACException {
        NaturalKeys.require(deptName, "department's deptName");
        return departmentRepository.findByDeptName(deptName).orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Department findDepartment(@NotNull Department department) throws RBACException {
        if (StringUtils.hasText(department.getDeptName())) {
            return findDepartment(department.getDeptName());
        }
        if (StringUtils.hasText(department.getPath())) {
            return departmentRepository.findByPath(DepartmentPathUtils.normalizePath(department.getPath())).orElse(null);
        }
        if (department.getId() != null) {
            return departmentRepository.findById(department.getId()).orElse(null);
        }
        if (StringUtils.hasText(department.getActorName())) {
            return findDepartment(department.getActorName());
        }
        throw new RBACValidationException(
                RBACMessageKeys.VALIDATION_DEPT_IDENTIFIER_REQUIRED,
                "findDepartment");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Department readDepartment(@NotNull String deptName) throws RBACException {
        Department dept = findDepartment(deptName);
        if (dept == null) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                    "readDepartment", deptName);
        }
        return dept;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Department readDepartment(@NotNull Department department) throws RBACException {
        Department dept = findDepartment(department);
        if (dept == null) {
            Object identifier = StringUtils.hasText(department.getDeptName()) ? department.getDeptName()
                    : StringUtils.hasText(department.getPath()) ? department.getPath()
                    : department.getId() != null ? department.getId()
                    : department.getActorName();
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                    "readDepartment", identifier);
        }
        return dept;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull List<Department> readDepartments(@NotNull Department department) throws RBACException {
        MineSpecification<Department> search = department.specification();
        if (Objects.isNull(search)) {
            return Collections.emptyList();
        }
        return departmentRepository.findAll(search);
    }

    // -------------------------------------------------------------------------
    // Tree Query
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull List<DepartmentTreeNode> readDepartmentTree() throws RBACException {
        List<Department> allDepts = departmentRepository.findAllOrderByLevelAscIdAsc();
        return DepartmentTreeUtils.buildTree(allDepts);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull DepartmentTreeNode readDepartmentSubTree(@NotNull String deptName) throws RBACException {
        NaturalKeys.require(deptName, "department's deptName");
        Department rootDept = departmentRepository.findByDeptName(deptName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                        "readDepartmentSubTree", deptName));
        List<Department> subDepts = departmentRepository.findSubTreeByPath(rootDept.getPath());
        List<DepartmentTreeNode> treeRoots = DepartmentTreeUtils.buildTree(subDepts);
        if (treeRoots.isEmpty()) {
            return DepartmentTreeNode.builder().department(rootDept).build();
        }
        return treeRoots.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull DepartmentTreeNode readDepartmentSubTree(@NotNull Department department) throws RBACException {
        Department rootDept = readDepartment(department);
        List<Department> subDepts = departmentRepository.findSubTreeByPath(rootDept.getPath());
        List<DepartmentTreeNode> treeRoots = DepartmentTreeUtils.buildTree(subDepts);
        if (treeRoots.isEmpty()) {
            return DepartmentTreeNode.builder().department(rootDept).build();
        }
        return treeRoots.get(0);
    }

    // -------------------------------------------------------------------------
    // User - Department Relations
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void addUser(@NotNull String deptName, @NotNull String username) throws RBACException {
        NaturalKeys.require(deptName, "department's deptName");
        NaturalKeys.require(username, "user's username");
        Department storeDept = departmentRepository.findByDeptName(deptName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                        "addUser", deptName));
        User storeUser = actorRepository.findByActorName(username)
                .filter(a -> a instanceof User)
                .map(a -> (User) a)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_USER,
                        "addUser", username));

        Map<String, List<Long>> map = new HashMap<>();
        map.put("department_id", List.of(storeDept.getId()));
        map.put("user_id", List.of(storeUser.getId()));
        try {
            if (mapMgr.exist(DEPARTMENT_USER, map)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_USER_IN_DEPARTMENT,
                        "addUser", storeUser.getId(), storeDept.getId());
            }
            mapMgr.save(DEPARTMENT_USER, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addUser(@NotNull Department department, @NotNull User user) throws RBACException {
        Department storeDept = readDepartment(department);
        User storeUser;
        if (user.getId() != null) {
            storeUser = actorRepository.findById(user.getId())
                    .filter(a -> a instanceof User)
                    .map(a -> (User) a)
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_USER,
                            "addUser", user.getId()));
        } else if (StringUtils.hasText(user.getUsername())) {
            storeUser = actorRepository.findByActorName(user.getUsername())
                    .filter(a -> a instanceof User)
                    .map(a -> (User) a)
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_USER,
                            "addUser", user.getUsername()));
        } else if (StringUtils.hasText(user.getActorName())) {
            storeUser = actorRepository.findByActorName(user.getActorName())
                    .filter(a -> a instanceof User)
                    .map(a -> (User) a)
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_USER,
                            "addUser", user.getActorName()));
        } else {
            throw new RBACValidationException(
                    RBACMessageKeys.VALIDATION_USER_IDENTIFIER_REQUIRED,
                    "addUser");
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("department_id", List.of(storeDept.getId()));
        map.put("user_id", List.of(storeUser.getId()));
        try {
            if (mapMgr.exist(DEPARTMENT_USER, map)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_USER_IN_DEPARTMENT,
                        "addUser", storeUser.getId(), storeDept.getId());
            }
            mapMgr.save(DEPARTMENT_USER, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeUser(@NotNull String deptName, @NotNull String username) throws RBACException {
        NaturalKeys.require(deptName, "department's deptName");
        NaturalKeys.require(username, "user's username");
        Department storeDept = departmentRepository.findByDeptName(deptName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                        "removeUser", deptName));
        User storeUser = actorRepository.findByActorName(username)
                .filter(a -> a instanceof User)
                .map(a -> (User) a)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_USER,
                        "removeUser", username));

        Map<String, List<Long>> map = new HashMap<>();
        map.put("department_id", List.of(storeDept.getId()));
        map.put("user_id", List.of(storeUser.getId()));
        try {
            if (!mapMgr.exist(DEPARTMENT_USER, map)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_USER_NOT_IN_DEPARTMENT,
                        "removeUser", storeUser.getId(), storeDept.getId());
            }
            mapMgr.delete(DEPARTMENT_USER, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeUser(@NotNull Department department, @NotNull User user) throws RBACException {
        Department storeDept = readDepartment(department);
        User storeUser;
        if (user.getId() != null) {
            storeUser = actorRepository.findById(user.getId())
                    .filter(a -> a instanceof User)
                    .map(a -> (User) a)
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_USER,
                            "removeUser", user.getId()));
        } else if (StringUtils.hasText(user.getUsername())) {
            storeUser = actorRepository.findByActorName(user.getUsername())
                    .filter(a -> a instanceof User)
                    .map(a -> (User) a)
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_USER,
                            "removeUser", user.getUsername()));
        } else if (StringUtils.hasText(user.getActorName())) {
            storeUser = actorRepository.findByActorName(user.getActorName())
                    .filter(a -> a instanceof User)
                    .map(a -> (User) a)
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_USER,
                            "removeUser", user.getActorName()));
        } else {
            throw new RBACValidationException(
                    RBACMessageKeys.VALIDATION_USER_IDENTIFIER_REQUIRED,
                    "removeUser");
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("department_id", List.of(storeDept.getId()));
        map.put("user_id", List.of(storeUser.getId()));
        try {
            if (!mapMgr.exist(DEPARTMENT_USER, map)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_USER_NOT_IN_DEPARTMENT,
                        "removeUser", storeUser.getId(), storeDept.getId());
            }
            mapMgr.delete(DEPARTMENT_USER, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull List<User> readDepartmentUsers(@NotNull String deptName) throws RBACException {
        NaturalKeys.require(deptName, "department's deptName");
        Department dept = departmentRepository.findByDeptName(deptName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                        "readDepartmentUsers", deptName));
        return departmentRepository.findDirectUsersByDepartmentId(dept.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull List<User> readDepartmentUsers(@NotNull Long departmentId) throws RBACException {
        if (!departmentRepository.existsById(departmentId)) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                    "readDepartmentUsers", departmentId);
        }
        return departmentRepository.findDirectUsersByDepartmentId(departmentId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull List<User> readDepartmentSuccessorUsers(@NotNull String deptName) throws RBACException {
        NaturalKeys.require(deptName, "department's deptName");
        Department dept = departmentRepository.findByDeptName(deptName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                        "readDepartmentSuccessorUsers", deptName));
        return departmentRepository.findSuccessorUsersByPath(dept.getPath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull List<User> readDepartmentSuccessorUsers(@NotNull Long departmentId) throws RBACException {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                        "readDepartmentSuccessorUsers", departmentId));
        return departmentRepository.findSuccessorUsersByPath(dept.getPath());
    }


}
