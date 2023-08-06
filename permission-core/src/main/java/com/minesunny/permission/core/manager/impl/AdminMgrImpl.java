package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.ServiceException;
import com.minesunny.jpa.manager.MapMgr;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.entity.perm.Resource;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.repository.PermissionRepository;
import com.minesunny.permission.core.repository.RoleRepository;
import com.minesunny.permission.core.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.minesunny.permission.core.type.MapTableConstants.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMgrImpl implements AdminMgr {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MapMgr mapMgr;

    @Override
    public @NotNull User addUser(@NotNull User user) throws ServiceException {

        if (Objects.nonNull(user.getId())) {
            if (userRepository.findById(user.getId()).isPresent()) {
                throw new ValidationException(("[addUser]:  The RBAC user entity [id: %s ] is already  exist").formatted(user.getId()));
            }
        }
        User newUser = new User();
        BeanUtils.copyProperties(user, newUser, "roles", "post", "managerOrgUnits", "orgUnits");
        return userRepository.saveAndFlush(newUser);
    }

    @Override
    public @NotNull User updateUser(@NotNull User user) throws ServiceException {

        if (Objects.isNull(user.getId())) {
            throw new ValidationException("[updateUser]:    The RBAC user  entity's id is required and cannot be null");
        }
        Optional<User> findUser = userRepository.findById(user.getId());
        if (findUser.isEmpty()) {
            throw new ValidationException("[updateUser]:    The RBAC user entity [id: %s] is not exist".formatted(user.getId()));
        }

        User storeUser = findUser.get();
        BeanUtils.copyProperties(user, storeUser, "id", "roles", "depts", "post");
        return userRepository.saveAndFlush(storeUser);
    }

    @Override
    @Transactional
    public void deleteUser(@NotNull User user) throws ServiceException {
        if (Objects.isNull(user.getId())) {
            throw new ValidationException("[deleteUser]:    The RBAC user entity's id is required and cannot be null");
        }
        Long userId = user.getId();
        Optional<User> findUser = userRepository.findById(userId);
        if (findUser.isEmpty()) {
            return;
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(user.getId()));
        mapMgr.delete(ORG_UNIT_USER, map);
        mapMgr.delete(ROLE_USER, map);
        mapMgr.delete(POST_USR, map);
        userRepository.delete(user);
    }

    @Override
    public @NotNull Role addRole(@NotNull Role role) throws ServiceException {

        if (Objects.nonNull(role.getId())) {
            if (roleRepository.findById(role.getId()).isPresent()) {
                throw new ValidationException(("[addRole]:    The RBAC role entity [id: %s ] is already exist").formatted(role.getId()));
            }
        }
        Role newRole = new Role();
        BeanUtils.copyProperties(role, newRole, "permissions", "users");
        return roleRepository.saveAndFlush(role);
    }

    @Override
    public @NotNull Role updateRole(@NotNull Role role) throws ServiceException {

        if (Objects.isNull(role.getId())) {
            throw new ValidationException("[updateRole]:    The RBAC role  entity's id is required and cannot be null");
        }
        Optional<Role> findRole = roleRepository.findById(role.getId());
        if (findRole.isEmpty()) {
            throw new ValidationException("[updateRole]:    The RBAC role entity [id: %s] is not exist".formatted(role.getId()));
        }
        Role storeRole = findRole.get();
        BeanUtils.copyProperties(role, storeRole, "id", "users", "permissions");
        return roleRepository.saveAndFlush(storeRole);
    }

    @Override
    @Transactional
    public void deleteRole(@NotNull Role role) throws ServiceException {
        if (Objects.isNull(role.getId())) {
            throw new ValidationException("[deleteRole]:    The RBAC role entity's id is required and cannot be null");
        }
        Long roleId = role.getId();
        roleRepository.deleteById(roleId);
    }

    @Override
    public void assignUser(@NotNull User user, @NotNull Role role) throws ServiceException {
        if (Objects.isNull(user.getId())) {
            throw new ValidationException("[assignUser]:    The RBAC user entity's id is required and cannot null");
        }
        if (Objects.isNull(role.getId())) {
            throw new ValidationException("[assignUser]:    The RBAC role entity's id is required and cannot null");
        }
        Long userId = user.getId();
        Long roleId = role.getId();
        if (!userRepository.existsById(userId)) {
            throw new ValidationException("[assignUser]:    The RBAC user[id: %s] entity is not exist".formatted(userId));
        }
        if (!roleRepository.existsById(roleId)) {
            throw new ValidationException("[assignUser]:    The RBAC role[id: %s] entity is required and cannot null".formatted(roleId));
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(user.getId()));
        map.put("role_id", List.of(role.getId()));
        if (mapMgr.exist(ROLE_USER, map)) {
            throw new ValidationException(String.format("[assignUser]:    The RBAC role[id: %s] entity has assigned " +
                    "to user[id: %s]entity", roleId, userId));
        }
        mapMgr.save(ROLE_USER, map);
    }

    @Override
    public void deassignUser(@NotNull User user, @NotNull Role role) throws ServiceException {
        if (Objects.isNull(user.getId())) {
            throw new ValidationException("[deassignUser]:    The RBAC user entity's id is required and cannot null");
        }
        if (Objects.isNull(role.getId())) {
            throw new ValidationException("[deassignUser]:    The RBAC role entity's id is required and cannot null");
        }
        Long userId = user.getId();
        Long roleId = role.getId();
        if (!userRepository.existsById(userId)) {
            throw new ValidationException(String.format("[deassignUser]:    The RBAC user[id: %s] entity is not exist", userId));
        }
        if (!roleRepository.existsById(roleId)) {
            throw new ValidationException(String.format("[deassignUser]:    The RBAC role[id: %s] entity is not exist", roleId));
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(user.getId()));
        map.put("role_id", List.of(role.getId()));
        if (!mapMgr.exist(ROLE_USER, map)) {
            throw new ValidationException("[deassignUser]:    The RBAC role[id: %s] entity has designed from user[id: %s] entity".formatted(roleId, userId));
        }
        mapMgr.delete(ROLE_USER, map);
    }

    @Override
    public @NotNull Permission addPermission(@NotNull Permission permission) throws ServiceException {
        if (Objects.nonNull(permission.getId())) {
            if (permissionRepository.findById(permission.getId()).isPresent()) {
                throw new ValidationException(("[addPermission]:    The RBAC permission entity [id: %s ] is already exist").formatted(permission.getId()));
            }
        }
        Set<Resource> resources = permission.getResources();
        if (Objects.nonNull(resources) && !resources.isEmpty()) {
            Set<String> resourceName = resources.stream().map(resource -> resource.getClass().getSimpleName()).collect(Collectors.toSet());
            if (resourceName.size() > 1) {
                throw new ValidationException(String.format("[addPermission]: The RBAC permission entity must has " +
                        "same resource [resource: %s] type set", Arrays.toString(StringUtils.toStringArray(resourceName))));
            }
        }
        return permissionRepository.saveAndFlush(permission);
    }

    @Override
    public @NotNull Permission updatePermission(@NotNull Permission permission) throws ServiceException {
        if (Objects.isNull(permission.getId())) {
            throw new ValidationException("[updatePermission]:    The RBAC permission  entity is required and cannot be null");
        }
        if (permissionRepository.findById(permission.getId()).isEmpty()) {
            throw new ValidationException(("[updatePermission]:    The RBAC permission entity [id: %s ] is not exist").formatted(permission.getId()));
        }
        Set<Resource> resources = permission.getResources();
        if (Objects.nonNull(resources) && !resources.isEmpty()) {
            Set<String> resourceName = resources.stream().map(resource -> resource.getClass().getSimpleName()).collect(Collectors.toSet());
            if (resourceName.size() > 1) {
                throw new ValidationException(String.format("[updatePermission]: The RBAC permission entity must has " +
                        "same resource [resource: %s] type set", Arrays.toString(StringUtils.toStringArray(resourceName))));
            }
        }
        return permissionRepository.saveAndFlush(permission);
    }

    @Override
    @Transactional
    public void deletePermission(@NotNull Permission permission) throws ServiceException {
        if (Objects.isNull(permission.getId())) {
            throw new ValidationException("[deletePermission]:    The RBAC permission entity's id is required and cannot be null");
        }
        Long permissionId = permission.getId();
        // delete permission map with role;

        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(permission.getId()));
        mapMgr.delete(ROLE_PERMISSION, map);
        permissionRepository.deleteById(permissionId);
    }


    @Override
    public void grantPermission(@NotNull Permission permission, @NotNull Role role) throws ServiceException {
        if (Objects.isNull(permission.getId())) {
            throw new ValidationException("[grantPermission]:    The RBAC permission entity's id is required and cannot null");
        }
        if (Objects.isNull(role.getId())) {
            throw new ValidationException("[grantPermission]:    The RBAC role entity's id is required and cannot null");
        }
        Long permissionId = permission.getId();
        Long roleId = role.getId();
        if (!permissionRepository.existsById(permissionId)) {
            throw new ValidationException(String.format("[grantPermission]:    The RBAC permission[id: %s] entity is not exist", permissionId));
        }
        if (!roleRepository.existsById(roleId)) {
            throw new ValidationException(String.format("[grantPermission]:    The RBAC role[id: %s] entity is not exist", roleId));
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(permission.getId()));
        map.put("role_id", List.of(role.getId()));
        if (mapMgr.exist(ROLE_PERMISSION, map)) {
            throw new ValidationException("[grantPermission]:    The RBAC permission[id: %s] entity has grant to role[id: %s] entity".formatted(permissionId, roleId));
        }
        mapMgr.save(ROLE_PERMISSION, map);
    }

    @Override
    public void revokePermission(@NotNull Permission permission, @NotNull Role role) throws ServiceException {
        if (Objects.isNull(permission.getId())) {
            throw new ValidationException("[revokePermission]:    The RBAC permission entity's id is required and cannot null");
        }
        if (Objects.isNull(role.getId())) {
            throw new ValidationException("[revokePermission]:    The RBAC role entity's id is required and cannot null");
        }
        Long permissionId = permission.getId();
        Long roleId = role.getId();
        if (!permissionRepository.existsById(permissionId)) {
            throw new ValidationException(String.format("[revokePermission]:    The RBAC permission[id: %s] entity is not exist", permissionId));
        }
        if (!roleRepository.existsById(roleId)) {
            throw new ValidationException(String.format("[revokePermission]:    The RBAC role[id: %s] entity is not exist", roleId));
        }

        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(permission.getId()));
        map.put("role_id", List.of(role.getId()));
        if (!mapMgr.exist(ROLE_PERMISSION, map)) {
            throw new ValidationException(("[revokePermission]:    The RBAC permission[id: %s] entity has revoke from role[id: %s] entity").formatted(permissionId, roleId));
        }
        mapMgr.delete(ROLE_PERMISSION, map);
    }
}
