package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.exception.ServiceException;
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
import com.minesunny.permission.core.util.NaturalKeys;
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
        NaturalKeys.requireUsername(user);
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new ValidationException("[addUser]:  The RBAC user entity [username: %s ] already exists".formatted(user.getUsername()));
        }
        User newUser = new User();
        BeanUtils.copyProperties(user, newUser, "roles", "post", "managerOrgUnits", "orgUnits");
        return userRepository.saveAndFlush(newUser);
    }

    @Override
    public @NotNull User updateUser(@NotNull String username, @NotNull User user) throws ServiceException {
        User storeUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ValidationException("[updateUser]:    The RBAC user entity [username: %s] does not exist".formatted(username)));
        BeanUtils.copyProperties(user, storeUser, "id", "roles", "post", "managerOrgUnits", "orgUnits");
        return userRepository.saveAndFlush(storeUser);
    }

    @Override
    public void deleteUser(@NotNull User user) throws ServiceException {
        NaturalKeys.requireUsername(user);
        User storeUser = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new ValidationException("[deleteUser]:    The RBAC user entity [username: %s] does not exist".formatted(user.getUsername())));
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(storeUser.getId()));
        mapMgr.delete(ORG_UNIT_USER, map);
        mapMgr.delete(ROLE_USER, map);
        mapMgr.delete(POST_USR, map);
        userRepository.delete(storeUser);
    }

    @Override
    public @NotNull Role addRole(@NotNull Role role) throws ServiceException {
        NaturalKeys.requireRoleName(role);
        if (roleRepository.findByRoleName(role.getRoleName()).isPresent()) {
            throw new ValidationException("[addRole]:    The RBAC role entity [roleName: %s ] already exists".formatted(role.getRoleName()));
        }
        Role newRole = new Role();
        BeanUtils.copyProperties(role, newRole, "permissions", "users");
        return roleRepository.saveAndFlush(newRole);
    }

    @Override
    public @NotNull Role updateRole(@NotNull String roleName, @NotNull Role role) throws ServiceException {
        Role storeRole = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new ValidationException("[updateRole]:    The RBAC role entity [roleName: %s] does not exist".formatted(roleName)));
        BeanUtils.copyProperties(role, storeRole, "id", "users", "permissions");
        return roleRepository.saveAndFlush(storeRole);
    }

    @Override
    public void deleteRole(@NotNull Role role) throws ServiceException {
        NaturalKeys.requireRoleName(role);
        Role storeRole = roleRepository.findByRoleName(role.getRoleName())
                .orElseThrow(() -> new ValidationException("[deleteRole]:    The RBAC role entity [roleName: %s] does not exist".formatted(role.getRoleName())));
        roleRepository.delete(storeRole);
    }

    @Override
    public void assignUser(@NotNull User user, @NotNull Role role) throws ServiceException {
        UserRoleIds ids = resolveUserRole(user, role, "assignUser");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(ids.userId()));
        map.put("role_id", List.of(ids.roleId()));
        if (mapMgr.exist(ROLE_USER, map)) {
            throw new ValidationException("[assignUser]:    The RBAC role[id: %s] entity has assigned to user[id: %s] entity".formatted(ids.roleId(), ids.userId()));
        }
        mapMgr.save(ROLE_USER, map);
    }

    @Override
    public void deassignUser(@NotNull User user, @NotNull Role role) throws ServiceException {
        UserRoleIds ids = resolveUserRole(user, role, "deassignUser");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(ids.userId()));
        map.put("role_id", List.of(ids.roleId()));
        if (!mapMgr.exist(ROLE_USER, map)) {
            throw new ValidationException("[deassignUser]:    The RBAC role[id: %s] entity has deassigned from user[id: %s] entity".formatted(ids.roleId(), ids.userId()));
        }
        mapMgr.delete(ROLE_USER, map);
    }

    @Override
    public @NotNull Permission addPermission(@NotNull Permission permission) throws ServiceException {
        NaturalKeys.requirePermName(permission);
        if (permissionRepository.findByPermName(permission.getPermName()).isPresent()) {
            throw new ValidationException("[addPermission]:    The RBAC permission entity [permName: %s ] already exists".formatted(permission.getPermName()));
        }
        validateSingleResourceType(permission.getResources(), "addPermission");
        return permissionRepository.saveAndFlush(permission);
    }

    @Override
    public @NotNull Permission updatePermission(@NotNull String permName, @NotNull Permission permission) throws ServiceException {
        Permission storePermission = permissionRepository.findByPermName(permName)
                .orElseThrow(() -> new ValidationException("[updatePermission]:    The RBAC permission entity [permName: %s] does not exist".formatted(permName)));
        validateSingleResourceType(permission.getResources(), "updatePermission");
        BeanUtils.copyProperties(permission, storePermission, "id");
        return permissionRepository.saveAndFlush(storePermission);
    }

    @Override
    public void deletePermission(@NotNull Permission permission) throws ServiceException {
        NaturalKeys.requirePermName(permission);
        Permission storePermission = permissionRepository.findByPermName(permission.getPermName())
                .orElseThrow(() -> new ValidationException("[deletePermission]:    The RBAC permission entity [permName: %s] does not exist".formatted(permission.getPermName())));
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(storePermission.getId()));
        mapMgr.delete(ROLE_PERMISSION, map);
        permissionRepository.delete(storePermission);
    }

    @Override
    public void grantPermission(@NotNull Permission permission, @NotNull Role role) throws ServiceException {
        PermissionRoleIds ids = resolvePermissionRole(permission, role, "grantPermission");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(ids.permissionId()));
        map.put("role_id", List.of(ids.roleId()));
        if (mapMgr.exist(ROLE_PERMISSION, map)) {
            throw new ValidationException("[grantPermission]:    The RBAC permission[id: %s] entity has grant to role[id: %s] entity".formatted(ids.permissionId(), ids.roleId()));
        }
        mapMgr.save(ROLE_PERMISSION, map);
    }

    @Override
    public void revokePermission(@NotNull Permission permission, @NotNull Role role) throws ServiceException {
        PermissionRoleIds ids = resolvePermissionRole(permission, role, "revokePermission");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(ids.permissionId()));
        map.put("role_id", List.of(ids.roleId()));
        if (!mapMgr.exist(ROLE_PERMISSION, map)) {
            throw new ValidationException("[revokePermission]:    The RBAC permission[id: %s] entity has revoke from role[id: %s] entity".formatted(ids.permissionId(), ids.roleId()));
        }
        mapMgr.delete(ROLE_PERMISSION, map);
    }

    private void validateSingleResourceType(Set<Resource> resources, String op) throws ValidationException {
        if (Objects.nonNull(resources) && !resources.isEmpty()) {
            Set<String> resourceNames = resources.stream().map(r -> r.getClass().getSimpleName()).collect(Collectors.toSet());
            if (resourceNames.size() > 1) {
                throw new ValidationException("[%s]: The RBAC permission entity must have a single resource [resource: %s] type set".formatted(op, Arrays.toString(StringUtils.toStringArray(resourceNames))));
            }
        }
    }

    private record UserRoleIds(Long userId, Long roleId) {}


    private record PermissionRoleIds(Long permissionId, Long roleId) {}

    private UserRoleIds resolveUserRole(User user, Role role, String op) throws ValidationException {
        NaturalKeys.requireUsername(user);
        NaturalKeys.requireRoleName(role);
        User storeUser = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new ValidationException("[%s]:    The RBAC user[username: %s] does not exist".formatted(op, user.getUsername())));
        Role storeRole = roleRepository.findByRoleName(role.getRoleName())
                .orElseThrow(() -> new ValidationException("[%s]:    The RBAC role[roleName: %s] does not exist".formatted(op, role.getRoleName())));
        return new UserRoleIds(storeUser.getId(), storeRole.getId());
    }

    private PermissionRoleIds resolvePermissionRole(Permission permission, Role role, String op) throws ValidationException {
        NaturalKeys.requirePermName(permission);
        NaturalKeys.requireRoleName(role);
        Permission storePermission = permissionRepository.findByPermName(permission.getPermName())
                .orElseThrow(() -> new ValidationException("[%s]:    The RBAC permission[permName: %s] does not exist".formatted(op, permission.getPermName())));
        Role storeRole = roleRepository.findByRoleName(role.getRoleName())
                .orElseThrow(() -> new ValidationException("[%s]:    The RBAC role[roleName: %s] does not exist".formatted(op, role.getRoleName())));
        return new PermissionRoleIds(storePermission.getId(), storeRole.getId());
    }
}
