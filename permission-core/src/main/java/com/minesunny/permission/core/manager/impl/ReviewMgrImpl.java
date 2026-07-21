package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.MineSpecification;
import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.jpa.manager.MapMgr;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.Session;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Operation;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.entity.perm.Resource;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.repository.OperationRepository;
import com.minesunny.permission.core.repository.PermissionRepository;
import com.minesunny.permission.core.repository.RoleRepository;
import com.minesunny.permission.core.repository.UserRepository;
import com.minesunny.permission.core.type.MapTableConstants;
import com.minesunny.permission.core.util.NaturalKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewMgrImpl implements ReviewMgr {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MapMgr mapMgr;
    private final OperationRepository operationRepository;

    @Override
    public Optional<User> readUser(@NotNull User user) throws ServiceException {
        NaturalKeys.requireUsername(user);
        return userRepository.findByUsername(user.getUsername());
    }

    @Override
    public List<User> readUsers(@NotNull User user) throws ServiceException {

        MineSpecification<User> search = user.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readUsers]:    The RBAC user searchCondition is required and cannot be null");
        }
        return userRepository.findAll(search);
    }

    @Override
    public Optional<Role> readRole(@NotNull Role role) throws ServiceException {
        NaturalKeys.requireRoleName(role);
        return roleRepository.findByRoleName(role.getRoleName());
    }

    @Override
    public @NotNull List<Role> readRoles(@NotNull Role role) throws ServiceException {
        MineSpecification<Role> search = role.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readRoles]:    The RBAC role searchCondition is required and cannot be null");
        }
        return roleRepository.findAll(search);
    }

    @Override
    public Optional<Permission> readPermission(@NotNull Permission permission) throws ServiceException {
        NaturalKeys.requirePermName(permission);
        return permissionRepository.findByPermName(permission.getPermName());
    }

    @Override
    public @NotNull List<Permission> readPermissions(@NotNull Permission permission) throws ServiceException {

        MineSpecification<Permission> search = permission.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readPermissions]:    The RBAC permission searchCondition is required and cannot be null");
        }
        return permissionRepository.findAll(search);
    }

    @Override
    public @NotNull List<Permission> rolePermissions(@NotNull Role role) throws ServiceException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        Optional<Role> optionalRole = reviewMgr.readRole(role);
        if (optionalRole.isPresent()) {
            Role readRole = optionalRole.get();
            Set<Permission> permissions = readRole.getPermissions();
            if (permissions == null || permissions.isEmpty()) {
                return Collections.emptyList();
            }
            return List.copyOf(permissions);
        }

        return Collections.emptyList();

    }

    @Override
    public @NotNull List<Permission> userPermissions(@NotNull User user) throws ServiceException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Role> roles = reviewMgr.assignedRoles(user);
        List<Long> permissionIds = mapMgr.find(MapTableConstants.ROLE_PERMISSION, "permission_id", "role_id",
                        roles.stream().map(Role::getId).toList())
                .stream()
                .toList();
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        Permission permission = new Permission();
        for (Long permissionId : permissionIds) {
            permission.setId(permissionId);
            permission.orEq("id");
        }
        return reviewMgr.readPermissions(permission);
    }

    @Override
    public @NotNull List<Role> sessionRoles(@NotNull Session session) throws ServiceException {

        List<Role> roles = session.getRoles();
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream().filter(Role::activated)
                .collect(Collectors.toList());
    }

    @Override
    public @NotNull List<Permission> sessionPermissions(@NotNull Session session) throws ServiceException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Role> roles = reviewMgr.sessionRoles(session);
        return roles.stream().flatMap(role -> role.getPermissions().stream()).collect(Collectors.toList());
    }

    @Override
    public @NotNull List<Operation> roleOperationsOnResource(@NotNull Role role, @NotNull Resource resource) throws ServiceException {
        if (Objects.isNull(resource.getId())) {
            throw new ValidationException("[roleOperationsOnResource]:    The RBAC resource entity is required and cannot be null");
        }
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        Optional<Role> optionalRole = reviewMgr.readRole(role);
        if (optionalRole.isEmpty()) {
            return Collections.emptyList();
        }
        // read permissions on role
        Set<Permission> permissions = optionalRole.get().getPermissions();

        // find which permission has resource
        List<Long> permissionIds = mapMgr
                .find(MapTableConstants.PERMISSION_RESOURCE, "permission_id", "resource_id", List.of(resource.getId()))
                .stream().distinct().toList();


        List<Long> resourcePermissions =
                permissions.stream().map(Permission::getId)
                        .filter(permissionIds::contains).toList();

        // find operations
        List<Long> operationIds =
                mapMgr.find(MapTableConstants.PERMISSION_OPERATION, "operation_id", "permission_id", resourcePermissions).stream().toList();
        return operationRepository.findAllById(operationIds);
    }

    @Override
    public @NotNull List<Operation> userOperationsOnResource(@NotNull User user, @NotNull Resource resource) throws ServiceException {
        if (Objects.isNull(resource.getId())) {
            throw new ValidationException("[roleOperationsOnResource]:    The RBAC resource entity is required and cannot be null");
        }
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Long> roleIds = reviewMgr.assignedRoles(user)
                .stream().map(Role::getId).toList();

        List<Long> permissionsOnRoles = mapMgr.find(MapTableConstants.ROLE_PERMISSION, "permission_id", "role_id",
                roleIds).stream().distinct().toList();

        // find which permission has resource
        List<Long> permissionIds = mapMgr.find(MapTableConstants.PERMISSION_RESOURCE, "permission_id", "resource_id",
                        List.of(resource.getId())).stream().distinct()
                .filter(permissionsOnRoles::contains).toList();

        // find operations
        List<Long> operationIds =
                mapMgr.find(MapTableConstants.PERMISSION_OPERATION, "operation_id", "permission_id", permissionIds).stream().map(Long::valueOf).toList();
        return operationRepository.findAllById(operationIds);
    }

    @Override
    public @NotNull List<User> assignedUsers(@NotNull Role role) throws ServiceException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        Optional<Role> optionalRole = reviewMgr.readRole(role);
        if (optionalRole.isPresent()) {
            Role readRole = optionalRole.get();
            Set<User> users = readRole.getUsers();
            if (users == null || users.isEmpty()) {
                return Collections.emptyList();
            }
            return List.copyOf(readRole.getUsers());
        }
        return Collections.emptyList();
    }

    @Override
    public @NotNull List<Role> assignedRoles(@NotNull User user) throws ServiceException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        Optional<User> optionalUser = reviewMgr.readUser(user);
        if (optionalUser.isPresent()) {
            User readUser = optionalUser.get();
            Set<Role> roles = readUser.getRoles();
            if (roles == null || roles.isEmpty()) {
                return Collections.emptyList();
            }
            return List.copyOf(readUser.getRoles());
        }
        return Collections.emptyList();
    }

    @Override
    public @NotNull List<Role> permissionRoles(@NotNull Permission permission) throws ServiceException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        Optional<Permission> optionalPermission = reviewMgr.readPermission(permission);
        if (optionalPermission.isEmpty()) {
            throw new ValidationException("[permissionRoles]:    The RBAC permission[id: %s] entity is not exist".formatted(permission.getId()));
        }
        Set<Role> roles = permission.getRoles();
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(permission.getRoles());
    }

    @Override
    public @NotNull List<User> permissionUsers(@NotNull Permission permission) throws ServiceException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Long> roleIds = reviewMgr.permissionRoles(permission)
                .stream().map(Role::getId).distinct().toList();
        List<Long> userIds =
                mapMgr.find(MapTableConstants.ROLE_USER, "user_id", "role_id", roleIds);
        User user = new User();
        for (Long userId : userIds) {
            user.setId(userId);
            user.orEq("id");
        }
        return reviewMgr.readUsers(user);
    }
}
