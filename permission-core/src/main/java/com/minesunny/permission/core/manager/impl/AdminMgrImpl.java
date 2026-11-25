package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.jpa.manager.MapMgr;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.*;
import com.minesunny.permission.core.i18n.RBACMessageKeys;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.repository.ActorRepository;
import com.minesunny.permission.core.repository.DepartmentRepository;
import com.minesunny.permission.core.repository.PermissionRepository;
import com.minesunny.permission.core.repository.RoleRepository;
import com.minesunny.permission.core.repository.UserRepository;
import com.minesunny.permission.core.util.NaturalKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.minesunny.permission.core.type.MapTableConstants.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMgrImpl implements AdminMgr {
    private final ActorRepository actorRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final DepartmentRepository departmentRepository;
    private final com.minesunny.permission.core.repository.sod.SSDSetRepository ssdSetRepository;
    private final MapMgr mapMgr;

    // -------------------------------------------------------------------------
    // Actor CRUD
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Actor> T addActor(@NotNull T actor) throws RBACException {
        NaturalKeys.requireActorName(actor);
        if (actorRepository.findByActorName(actor.getActorName()).isPresent()) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_ACTOR_EXISTS,
                    "addActor", actor.getActorName());
        }
        try {
            return (T) actorRepository.saveAndFlush(actor);
        } catch (DataIntegrityViolationException e) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_ACTOR_ARCHIVED, e,
                    "addActor", actor.getActorName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Actor> T updateActor(@NotNull String actorName, @NotNull T actor) throws RBACException {
        Actor storeActor = actorRepository.findByActorName(actorName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ACTOR,
                        "updateActor", actorName));
        String newActorName = actor.getActorName();
        if (Objects.nonNull(newActorName) && !newActorName.equals(actorName)
                && actorRepository.findByActorName(newActorName).isPresent()) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_ACTOR_EXISTS,
                    "updateActor", newActorName);
        }
        List<String> excludes = new ArrayList<>(List.of("id", "roles"));
        if (actor instanceof User) {
            excludes.add("departments");
        } else if (actor instanceof Department) {
            excludes.add("users");
        }
        BeanUtils.copyProperties(actor, storeActor, excludes.toArray(new String[0]));
        return (T) actorRepository.saveAndFlush(storeActor);
    }

    @Override
    public void deleteActor(@NotNull String actorName) throws RBACException {
        NaturalKeys.require(actorName, "actor's actorName");
        Actor storeActor = actorRepository.findByActorName(actorName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ACTOR,
                        "deleteActor", actorName));
        deleteActor(storeActor);
    }

    @Override
    public void deleteActor(@NotNull Actor actor) throws RBACException {
        Actor storeActor = actor;
        if (storeActor.getId() == null || storeActor.getActorName() == null) {
            if (actor.getId() != null) {
                storeActor = actorRepository.findById(actor.getId())
                        .orElseThrow(() -> new RBACNotFoundException(
                                RBACMessageKeys.NOT_FOUND_ACTOR,
                                "deleteActor", actor.getId()));
            } else {
                String resolvedName = actor.getActorName();
                if (!StringUtils.hasText(resolvedName)) {
                    if (actor instanceof User user && StringUtils.hasText(user.getUsername())) {
                        resolvedName = user.getUsername();
                    } else if (actor instanceof Department dept && StringUtils.hasText(dept.getDeptName())) {
                        resolvedName = dept.getDeptName();
                    }
                }
                final String actorName = NaturalKeys.require(resolvedName, RBACMessageKeys.VALIDATION_ACTOR_NAME_REQUIRED, "actor's actorName");
                storeActor = actorRepository.findByActorName(actorName)
                        .orElseThrow(() -> new RBACNotFoundException(
                                RBACMessageKeys.NOT_FOUND_ACTOR,
                                "deleteActor", actorName));
            }
        }
        if (storeActor instanceof Department dept && StringUtils.hasText(dept.getPath())) {
            List<Department> subTree = departmentRepository.findSubTreeByPath(dept.getPath());
            if (subTree.size() > 1) {
                throw new RBACTopologyException(
                        RBACMessageKeys.TOPOLOGY_CHILD_DEPARTMENTS,
                        "deleteActor", dept.getDeptName());
            }
        }
        Map<String, List<Long>> actorMap = new HashMap<>();
        actorMap.put("actor_id", List.of(storeActor.getId()));
        try {
            mapMgr.delete(ROLE_ACTOR, actorMap);
            if (storeActor instanceof User) {
                Map<String, List<Long>> userMap = new HashMap<>();
                userMap.put("user_id", List.of(storeActor.getId()));
                mapMgr.delete(DEPARTMENT_USER, userMap);
            } else if (storeActor instanceof Department) {
                Map<String, List<Long>> deptMap = new HashMap<>();
                deptMap.put("department_id", List.of(storeActor.getId()));
                mapMgr.delete(DEPARTMENT_USER, deptMap);
            }
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
        actorRepository.delete(storeActor);
        actorRepository.flush();
    }

    @Override
    public Actor findActor(@NotNull String actorName) throws RBACException {
        NaturalKeys.require(actorName, RBACMessageKeys.VALIDATION_ACTOR_NAME_REQUIRED, "actor's actorName");
        return actorRepository.findByActorName(actorName).orElse(null);
    }

    @Override
    public @NotNull Actor readActor(@NotNull String actorName) throws RBACException {
        Actor actor = findActor(actorName);
        if (actor == null) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_ACTOR,
                    "readActor", actorName);
        }
        return actor;
    }

    // -------------------------------------------------------------------------
    // User CRUD & Read
    // -------------------------------------------------------------------------

    @Override
    public @NotNull User addUser(@NotNull User user) throws RBACException {
        NaturalKeys.requireUsername(user);
        if (!StringUtils.hasText(user.getActorName())) {
            user.setActorName(user.getUsername());
        }
        if (userRepository.findByUsername(user.getUsername()).isPresent()
                || actorRepository.findByActorName(user.getActorName()).isPresent()) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_USER_EXISTS,
                    "addUser", user.getUsername());
        }
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_USER_ARCHIVED, e,
                    "addUser", user.getUsername());
        }
    }

    @Override
    public @NotNull User updateUser(@NotNull String username, @NotNull User user) throws RBACException {
        NaturalKeys.require(username, "user's username");
        User storeUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_USER,
                        "updateUser", username));
        String newUsername = user.getUsername();
        if (StringUtils.hasText(newUsername) && !newUsername.equals(username)) {
            if (userRepository.findByUsername(newUsername).isPresent()
                    || actorRepository.findByActorName(newUsername).isPresent()) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_USER_EXISTS,
                        "updateUser", newUsername);
            }
            storeUser.setUsername(newUsername);
            storeUser.setActorName(newUsername);
        }
        BeanUtils.copyProperties(user, storeUser, "id", "roles", "departments", "username", "actorName");
        return userRepository.saveAndFlush(storeUser);
    }

    @Override
    public void deleteUser(@NotNull String username) throws RBACException {
        NaturalKeys.require(username, RBACMessageKeys.VALIDATION_USERNAME_REQUIRED, "user's username");
        User storeUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_USER,
                        "deleteUser", username));
        deleteActor(storeUser);
    }

    @Override
    public void deleteUser(@NotNull User user) throws RBACException {
        NaturalKeys.requireUsername(user);
        deleteUser(user.getUsername());
    }

    @Override
    public User findUser(@NotNull String username) throws RBACException {
        NaturalKeys.require(username, RBACMessageKeys.VALIDATION_USERNAME_REQUIRED, "user's username");
        return userRepository.findByUsername(username).orElse(null);
    }

    @Override
    public @NotNull User readUser(@NotNull String username) throws RBACException {
        User user = findUser(username);
        if (user == null) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_USER,
                    "readUser", username);
        }
        return user;
    }

    // -------------------------------------------------------------------------
    // Role CRUD
    // -------------------------------------------------------------------------

    @Override
    public @NotNull Role addRole(@NotNull Role role) throws RBACException {
        NaturalKeys.requireRoleName(role);
        if (roleRepository.findByRoleName(role.getRoleName()).isPresent()) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_ROLE_EXISTS,
                    "addRole", role.getRoleName());
        }
        Role newRole = new Role();
        BeanUtils.copyProperties(role, newRole, "permissions", "actors");
        try {
            return roleRepository.saveAndFlush(newRole);
        } catch (DataIntegrityViolationException e) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_ROLE_ARCHIVED, e,
                    "addRole", role.getRoleName());
        }
    }

    @Override
    public @NotNull Role updateRole(@NotNull String roleName, @NotNull Role role) throws RBACException {
        Role storeRole = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE,
                        "updateRole", roleName));
        String newRoleName = role.getRoleName();
        if (Objects.nonNull(newRoleName) && !newRoleName.equals(roleName)
                && roleRepository.findByRoleName(newRoleName).isPresent()) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_ROLE_EXISTS,
                    "updateRole", newRoleName);
        }
        BeanUtils.copyProperties(role, storeRole, "id", "actors", "permissions");
        return roleRepository.saveAndFlush(storeRole);
    }

    @Override
    public void deleteRole(@NotNull String roleName) throws RBACException {
        NaturalKeys.require(roleName, RBACMessageKeys.VALIDATION_ROLE_NAME_REQUIRED, "role's roleName");
        Role storeRole = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE,
                        "deleteRole", roleName));
        Map<String, List<Long>> roleMap = new HashMap<>();
        roleMap.put("role_id", List.of(storeRole.getId()));
        Map<String, List<Long>> parentRoleMap = new HashMap<>();
        parentRoleMap.put("parent_role_id", List.of(storeRole.getId()));
        Map<String, List<Long>> childRoleMap = new HashMap<>();
        childRoleMap.put("child_role_id", List.of(storeRole.getId()));
        try {
            mapMgr.delete(ROLE_ACTOR, roleMap);
            mapMgr.delete(ROLE_PERMISSION, roleMap);
            mapMgr.delete(ROLE_HIERARCHY, parentRoleMap);
            mapMgr.delete(ROLE_HIERARCHY, childRoleMap);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
        roleRepository.delete(storeRole);
        roleRepository.flush();
    }

    @Override
    public void deleteRole(@NotNull Role role) throws RBACException {
        NaturalKeys.requireRoleName(role);
        deleteRole(role.getRoleName());
    }

    @Override
    public Role findRole(@NotNull String roleName) throws RBACException {
        NaturalKeys.require(roleName, RBACMessageKeys.VALIDATION_ROLE_NAME_REQUIRED, "role's roleName");
        return roleRepository.findByRoleName(roleName).orElse(null);
    }

    @Override
    public @NotNull Role readRole(@NotNull String roleName) throws RBACException {
        Role role = findRole(roleName);
        if (role == null) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_ROLE,
                    "readRole", roleName);
        }
        return role;
    }

    // -------------------------------------------------------------------------
    // Actor-Role assignment
    // -------------------------------------------------------------------------

    @Override
    public void assignActor(@NotNull String actorName, @NotNull String roleName) throws RBACException {
        NaturalKeys.require(actorName, RBACMessageKeys.VALIDATION_ACTOR_NAME_REQUIRED, "actor's actorName");
        NaturalKeys.require(roleName, RBACMessageKeys.VALIDATION_ROLE_NAME_REQUIRED, "role's roleName");
        Actor actor = actorRepository.findByActorName(actorName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ACTOR,
                        "assignActor", actorName));
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE,
                        "assignActor", roleName));
        AdminMgr self = (AdminMgr) AopContext.currentProxy();
        self.assignActor(actor, role);
    }

    @Override
    public void assignActor(@NotNull Actor actor, @NotNull Role role) throws RBACException {
        Long finalActorId;
        String resolvedActorName = actor.getActorName();
        if (actor.getId() == null) {
            if (!StringUtils.hasText(resolvedActorName)) {
                if (actor instanceof User user && StringUtils.hasText(user.getUsername())) {
                    resolvedActorName = user.getUsername();
                } else if (actor instanceof Department dept && StringUtils.hasText(dept.getDeptName())) {
                    resolvedActorName = dept.getDeptName();
                }
            }
            final String actorName = NaturalKeys.require(resolvedActorName, RBACMessageKeys.VALIDATION_ACTOR_NAME_REQUIRED, "actor's actorName");
            Actor storeActor = actorRepository.findByActorName(actorName)
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_ACTOR,
                            "assignActor", actorName));
            finalActorId = storeActor.getId();
            resolvedActorName = storeActor.getActorName();
        } else {
            finalActorId = actor.getId();
            if (actor.getActorName() == null) {
                Actor storeActor = actorRepository.findById(finalActorId)
                        .orElseThrow(() -> new RBACNotFoundException(
                                RBACMessageKeys.NOT_FOUND_ACTOR,
                                "assignActor", finalActorId));
                resolvedActorName = storeActor.getActorName();
            }
        }
        final String finalActorName = resolvedActorName;

        NaturalKeys.requireRoleName(role);
        Role storeRole;
        if (role.getId() != null) {
            storeRole = roleRepository.findById(role.getId())
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_ROLE,
                            "assignActor", role.getId()));
        } else {
            storeRole = roleRepository.findByRoleName(role.getRoleName())
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_ROLE,
                            "assignActor", role.getRoleName()));
        }
        Long roleId = storeRole.getId();

        // 校验 1: 最大主体基数限制 (maxCardinality)
        if (storeRole.getMaxCardinality() != null && storeRole.getMaxCardinality() > 0) {
            try {
                List<Long> assignedActorIds = mapMgr.find(ROLE_ACTOR, "actor_id", "role_id", List.of(roleId));
                if (assignedActorIds.size() >= storeRole.getMaxCardinality()) {
                    throw new RBACConflictException(
                            RBACMessageKeys.CONFLICT_ROLE_MAX_CARDINALITY,
                            "assignActor", storeRole.getRoleName(), storeRole.getMaxCardinality());
                }
            } catch (ServiceException e) {
                throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
            }
        }

        // 校验 2: 静态职责分离 (SSD) 约束检查
        // 形式化判定: ∀ (ssd_name, role_set, n) ∈ SSD, 若 storeRole ∈ role_set,
        // 则已分配角色与 role_set 的交集大小 + 1 < n
        List<com.minesunny.permission.core.entity.sod.SSDSet> ssdSets = ssdSetRepository.findAll();
        if (!ssdSets.isEmpty()) {
            try {
                List<Long> existingRoleIds = mapMgr.find(ROLE_ACTOR, "role_id", "actor_id", List.of(finalActorId));
                for (com.minesunny.permission.core.entity.sod.SSDSet ssdSet : ssdSets) {
                    Set<Long> ssdMemberRoleIds = ssdSet.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
                    if (ssdMemberRoleIds.contains(roleId)) {
                        long overlapCount = existingRoleIds.stream().filter(ssdMemberRoleIds::contains).distinct().count();
                        if (overlapCount + 1 >= ssdSet.getCardinality()) {
                            throw new RBACConflictException(
                                    RBACMessageKeys.CONFLICT_SSD_VIOLATION,
                                    "assignActor",
                                    storeRole.getRoleName(),
                                    finalActorName,
                                    ssdSet.getName(),
                                    ssdSet.getCardinality()
                            );
                        }
                    }
                }
            } catch (ServiceException e) {
                throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
            }
        }

        Map<String, List<Long>> map = new HashMap<>();
        map.put("actor_id", List.of(finalActorId));
        map.put("role_id", List.of(roleId));
        try {
            if (mapMgr.exist(ROLE_ACTOR, map)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_ROLE_ASSIGNED,
                        "assignActor", roleId, finalActorId);
            }
            mapMgr.save(ROLE_ACTOR, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    @Override
    public void deassignActor(@NotNull String actorName, @NotNull String roleName) throws RBACException {
        NaturalKeys.require(actorName, RBACMessageKeys.VALIDATION_ACTOR_NAME_REQUIRED, "actor's actorName");
        NaturalKeys.require(roleName, RBACMessageKeys.VALIDATION_ROLE_NAME_REQUIRED, "role's roleName");
        Actor actor = actorRepository.findByActorName(actorName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ACTOR,
                        "deassignActor", actorName));
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE,
                        "deassignActor", roleName));
        Long actorId = actor.getId();
        Long roleId = role.getId();
        Map<String, List<Long>> map = new HashMap<>();
        map.put("actor_id", List.of(actorId));
        map.put("role_id", List.of(roleId));
        try {
            if (!mapMgr.exist(ROLE_ACTOR, map)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_ROLE_NOT_ASSIGNED,
                        "deassignActor", roleId, actorId);
            }
            mapMgr.delete(ROLE_ACTOR, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    @Override
    public void deassignActor(@NotNull Actor actor, @NotNull Role role) throws RBACException {
        Long actorId = actor.getId();
        if (actorId == null) {
            String resolvedName = actor.getActorName();
            if (!StringUtils.hasText(resolvedName)) {
                if (actor instanceof User user && StringUtils.hasText(user.getUsername())) {
                    resolvedName = user.getUsername();
                } else if (actor instanceof Department dept && StringUtils.hasText(dept.getDeptName())) {
                    resolvedName = dept.getDeptName();
                }
            }
            final String actorName = NaturalKeys.require(resolvedName, RBACMessageKeys.VALIDATION_ACTOR_NAME_REQUIRED, "actor's actorName");
            actorId = actorRepository.findByActorName(actorName)
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_ACTOR,
                            "deassignActor", actorName))
                    .getId();
        } else if (actor.getActorName() == null && !actorRepository.existsById(actorId)) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_ACTOR,
                    "deassignActor", actorId);
        }

        NaturalKeys.requireRoleName(role);
        Long roleId = role.getId();
        if (roleId == null) {
            roleId = roleRepository.findByRoleName(role.getRoleName())
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_ROLE,
                            "deassignActor", role.getRoleName()))
                    .getId();
        } else if (role.getRoleName() == null && !roleRepository.existsById(roleId)) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_ROLE,
                    "deassignActor", role.getRoleName());
        }

        Map<String, List<Long>> map = new HashMap<>();
        map.put("actor_id", List.of(actorId));
        map.put("role_id", List.of(roleId));
        try {
            if (!mapMgr.exist(ROLE_ACTOR, map)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_ROLE_NOT_ASSIGNED,
                        "deassignActor", roleId, actorId);
            }
            mapMgr.delete(ROLE_ACTOR, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Permission CRUD + grant/revoke
    // -------------------------------------------------------------------------

    @Override
    public @NotNull Permission addPermission(@NotNull Permission permission) throws RBACException {
        NaturalKeys.requirePermName(permission);
        if (permissionRepository.findByPermName(permission.getPermName()).isPresent()) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_PERMISSION_EXISTS,
                    "addPermission", permission.getPermName());
        }
        try {
            return permissionRepository.saveAndFlush(permission);
        } catch (DataIntegrityViolationException e) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_PERMISSION_ARCHIVED, e,
                    "addPermission", permission.getPermName());
        }
    }

    @Override
    public @NotNull Permission updatePermission(@NotNull String permName, @NotNull Permission permission) throws RBACException {
        Permission storePermission = permissionRepository.findByPermName(permName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_PERMISSION,
                        "updatePermission", permName));
        String newPermName = permission.getPermName();
        if (Objects.nonNull(newPermName) && !newPermName.equals(permName)
                && permissionRepository.findByPermName(newPermName).isPresent()) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_PERMISSION_EXISTS,
                    "updatePermission", newPermName);
        }
        BeanUtils.copyProperties(permission, storePermission, "id");
        return permissionRepository.saveAndFlush(storePermission);
    }

    @Override
    public void deletePermission(@NotNull String permName) throws RBACException {
        NaturalKeys.require(permName, RBACMessageKeys.VALIDATION_PERM_NAME_REQUIRED, "permission's permName");
        Permission storePermission = permissionRepository.findByPermName(permName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_PERMISSION,
                        "deletePermission", permName));
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(storePermission.getId()));
        try {
            mapMgr.delete(ROLE_PERMISSION, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
        permissionRepository.delete(storePermission);
        permissionRepository.flush();
    }

    @Override
    public void deletePermission(@NotNull Permission permission) throws RBACException {
        NaturalKeys.requirePermName(permission);
        deletePermission(permission.getPermName());
    }

    @Override
    public Permission findPermission(@NotNull String permName) throws RBACException {
        NaturalKeys.require(permName, RBACMessageKeys.VALIDATION_PERM_NAME_REQUIRED, "permission's permName");
        return permissionRepository.findByPermName(permName).orElse(null);
    }

    @Override
    public @NotNull Permission readPermission(@NotNull String permName) throws RBACException {
        Permission permission = findPermission(permName);
        if (permission == null) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_PERMISSION,
                    "readPermission", permName);
        }
        return permission;
    }

    @Override
    public void grantPermission(@NotNull String permName, @NotNull String roleName) throws RBACException {
        NaturalKeys.require(permName, RBACMessageKeys.VALIDATION_PERM_NAME_REQUIRED, "permission's permName");
        NaturalKeys.require(roleName, RBACMessageKeys.VALIDATION_ROLE_NAME_REQUIRED, "role's roleName");
        Permission perm = permissionRepository.findByPermName(permName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_PERMISSION,
                        "grantPermission", permName));
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE,
                        "grantPermission", roleName));
        Long permId = perm.getId();
        Long roleId = role.getId();
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(permId));
        map.put("role_id", List.of(roleId));
        try {
            if (mapMgr.exist(ROLE_PERMISSION, map)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_PERMISSION_GRANTED,
                        "grantPermission", permId, roleId);
            }
            mapMgr.save(ROLE_PERMISSION, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    @Override
    public void grantPermission(@NotNull Permission permission, @NotNull Role role) throws RBACException {
        NaturalKeys.requirePermName(permission);
        Long permId = permission.getId();
        if (permId == null) {
            permId = permissionRepository.findByPermName(permission.getPermName())
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_PERMISSION,
                            "grantPermission", permission.getPermName()))
                    .getId();
        } else if (permission.getPermName() == null && !permissionRepository.existsById(permId)) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_PERMISSION,
                    "grantPermission", permission.getPermName());
        }

        NaturalKeys.requireRoleName(role);
        Long roleId = role.getId();
        if (roleId == null) {
            roleId = roleRepository.findByRoleName(role.getRoleName())
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_ROLE,
                            "grantPermission", role.getRoleName()))
                    .getId();
        } else if (role.getRoleName() == null && !roleRepository.existsById(roleId)) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_ROLE,
                    "grantPermission", role.getRoleName());
        }

        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(permId));
        map.put("role_id", List.of(roleId));
        try {
            if (mapMgr.exist(ROLE_PERMISSION, map)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_PERMISSION_GRANTED,
                        "grantPermission", permId, roleId);
            }
            mapMgr.save(ROLE_PERMISSION, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    @Override
    public void revokePermission(@NotNull String permName, @NotNull String roleName) throws RBACException {
        NaturalKeys.require(permName, RBACMessageKeys.VALIDATION_PERM_NAME_REQUIRED, "permission's permName");
        NaturalKeys.require(roleName, RBACMessageKeys.VALIDATION_ROLE_NAME_REQUIRED, "role's roleName");
        Permission perm = permissionRepository.findByPermName(permName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_PERMISSION,
                        "revokePermission", permName));
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE,
                        "revokePermission", roleName));
        Long permId = perm.getId();
        Long roleId = role.getId();
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(permId));
        map.put("role_id", List.of(roleId));
        try {
            if (!mapMgr.exist(ROLE_PERMISSION, map)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_PERMISSION_NOT_GRANTED,
                        "revokePermission", permId, roleId);
            }
            mapMgr.delete(ROLE_PERMISSION, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    @Override
    public void revokePermission(@NotNull Permission permission, @NotNull Role role) throws RBACException {
        NaturalKeys.requirePermName(permission);
        Long permId = permission.getId();
        if (permId == null) {
            permId = permissionRepository.findByPermName(permission.getPermName())
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_PERMISSION,
                            "revokePermission", permission.getPermName()))
                    .getId();
        } else if (permission.getPermName() == null && !permissionRepository.existsById(permId)) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_PERMISSION,
                    "revokePermission", permission.getPermName());
        }

        NaturalKeys.requireRoleName(role);
        Long roleId = role.getId();
        if (roleId == null) {
            roleId = roleRepository.findByRoleName(role.getRoleName())
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_ROLE,
                            "revokePermission", role.getRoleName()))
                    .getId();
        } else if (role.getRoleName() == null && !roleRepository.existsById(roleId)) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_ROLE,
                    "revokePermission", role.getRoleName());
        }

        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(permId));
        map.put("role_id", List.of(roleId));
        try {
            if (!mapMgr.exist(ROLE_PERMISSION, map)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_PERMISSION_NOT_GRANTED,
                        "revokePermission", permId, roleId);
            }
            mapMgr.delete(ROLE_PERMISSION, map);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Department Read & Delete (Symmetrical Facade in AdminMgr)
    // -------------------------------------------------------------------------

    @Override
    public Department findDepartment(@NotNull String deptName) throws RBACException {
        NaturalKeys.require(deptName, "department's deptName");
        return departmentRepository.findByDeptName(deptName).orElse(null);
    }

    @Override
    public @NotNull Department readDepartment(@NotNull String deptName) throws RBACException {
        Department department = findDepartment(deptName);
        if (department == null) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                    "readDepartment", deptName);
        }
        return department;
    }

    @Override
    public void deleteDepartment(@NotNull String deptName) throws RBACException {
        NaturalKeys.require(deptName, "department's deptName");
        Department storeDept = departmentRepository.findByDeptName(deptName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_DEPARTMENT,
                        "deleteDepartment", deptName));
        deleteActor(storeDept);
    }

    // -------------------------------------------------------------------------
    // NIST Hierarchical RBAC: Role Inheritance (RH)
    // -------------------------------------------------------------------------

    @Override
    public void addRoleInheritance(@NotNull String parentRoleName, @NotNull String childRoleName) throws RBACException {
        NaturalKeys.require(parentRoleName, "parentRoleName");
        NaturalKeys.require(childRoleName, "childRoleName");
        Role parentRole = roleRepository.findByRoleName(parentRoleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE, "addRoleInheritance", parentRoleName));
        Role childRole = roleRepository.findByRoleName(childRoleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE, "addRoleInheritance", childRoleName));
        AdminMgr self = (AdminMgr) AopContext.currentProxy();
        self.addRoleInheritance(parentRole, childRole);
    }

    @Override
    public void addRoleInheritance(@NotNull Role parentRole, @NotNull Role childRole) throws RBACException {
        NaturalKeys.requireRoleName(parentRole);
        NaturalKeys.requireRoleName(childRole);

        Role storeParent = roleRepository.findByRoleName(parentRole.getRoleName())
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE, "addRoleInheritance", parentRole.getRoleName()));
        Role storeChild = roleRepository.findByRoleName(childRole.getRoleName())
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE, "addRoleInheritance", childRole.getRoleName()));

        Long parentId = storeParent.getId();
        Long childId = storeChild.getId();

        Map<String, List<Long>> pairMap = new HashMap<>();
        pairMap.put("parent_role_id", List.of(parentId));
        pairMap.put("child_role_id", List.of(childId));

        try {
            if (mapMgr.exist(ROLE_HIERARCHY, pairMap)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_ROLE_INHERITANCE_EXISTS,
                        "addRoleInheritance", storeParent.getRoleName(), storeChild.getRoleName());
            }

            // 检查成环：递归查询 child 及其所有已有后继子角色
            Set<Long> descendantIdsOfChild = new HashSet<>();
            Queue<Long> queue = new ArrayDeque<>();
            queue.add(childId);
            descendantIdsOfChild.add(childId);

            while (!queue.isEmpty()) {
                Long current = queue.poll();
                List<Long> directChildren = mapMgr.find(ROLE_HIERARCHY, "child_role_id", "parent_role_id", List.of(current));
                for (Long nextChild : directChildren) {
                    if (descendantIdsOfChild.add(nextChild)) {
                        queue.add(nextChild);
                    }
                }
            }

            // 纯函数式 DAG 成环检测
            com.minesunny.permission.core.util.RoleHierarchyUtils.validateNoCycle(
                    parentId, childId, storeParent.getRoleName(), storeChild.getRoleName(), descendantIdsOfChild);

            mapMgr.save(ROLE_HIERARCHY, pairMap);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    @Override
    public void deleteRoleInheritance(@NotNull String parentRoleName, @NotNull String childRoleName) throws RBACException {
        NaturalKeys.require(parentRoleName, "parentRoleName");
        NaturalKeys.require(childRoleName, "childRoleName");
        Role parentRole = roleRepository.findByRoleName(parentRoleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE, "deleteRoleInheritance", parentRoleName));
        Role childRole = roleRepository.findByRoleName(childRoleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE, "deleteRoleInheritance", childRoleName));
        AdminMgr self = (AdminMgr) AopContext.currentProxy();
        self.deleteRoleInheritance(parentRole, childRole);
    }

    @Override
    public void deleteRoleInheritance(@NotNull Role parentRole, @NotNull Role childRole) throws RBACException {
        NaturalKeys.requireRoleName(parentRole);
        NaturalKeys.requireRoleName(childRole);

        Role storeParent = roleRepository.findByRoleName(parentRole.getRoleName())
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE, "deleteRoleInheritance", parentRole.getRoleName()));
        Role storeChild = roleRepository.findByRoleName(childRole.getRoleName())
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE, "deleteRoleInheritance", childRole.getRoleName()));

        Map<String, List<Long>> pairMap = new HashMap<>();
        pairMap.put("parent_role_id", List.of(storeParent.getId()));
        pairMap.put("child_role_id", List.of(storeChild.getId()));

        try {
            if (!mapMgr.exist(ROLE_HIERARCHY, pairMap)) {
                throw new RBACConflictException(
                        RBACMessageKeys.CONFLICT_ROLE_INHERITANCE_NOT_EXISTS,
                        "deleteRoleInheritance", storeParent.getRoleName(), storeChild.getRoleName());
            }
            mapMgr.delete(ROLE_HIERARCHY, pairMap);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }
}
