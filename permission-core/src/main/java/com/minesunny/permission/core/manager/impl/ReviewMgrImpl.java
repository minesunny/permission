package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.MineSpecification;
import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.jpa.manager.MapMgr;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.Session;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.repository.ActorRepository;
import com.minesunny.permission.core.repository.PermissionRepository;
import com.minesunny.permission.core.repository.RoleRepository;
import com.minesunny.permission.core.type.MapTableConstants;
import com.minesunny.permission.core.util.NaturalKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewMgrImpl implements ReviewMgr {
    private final ActorRepository actorRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MapMgr mapMgr;

    // -------------------------------------------------------------------------
    // Actor read
    // -------------------------------------------------------------------------

    @Override
    public Optional<Actor> readActor(@NotNull Actor actor) throws RBACException {
        NaturalKeys.requireActorName(actor);
        return actorRepository.findByActorName(actor.getActorName());
    }

    @Override
    public List<Actor> readActors(@NotNull Actor actor) throws RBACException {
        MineSpecification<Actor> search = actor.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readActors]:    The RBAC actor searchCondition is required and cannot be null");
        }
        return actorRepository.findAll(search);
    }

    // -------------------------------------------------------------------------
    // Role read
    // -------------------------------------------------------------------------

    @Override
    public Optional<Role> readRole(@NotNull Role role) throws RBACException {
        NaturalKeys.requireRoleName(role);
        return roleRepository.findByRoleName(role.getRoleName());
    }

    @Override
    public @NotNull List<Role> readRoles(@NotNull Role role) throws RBACException {
        MineSpecification<Role> search = role.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readRoles]:    The RBAC role searchCondition is required and cannot be null");
        }
        return roleRepository.findAll(search);
    }

    // -------------------------------------------------------------------------
    // Permission read
    // -------------------------------------------------------------------------

    @Override
    public Optional<Permission> readPermission(@NotNull Permission permission) throws RBACException {
        NaturalKeys.requirePermName(permission);
        return permissionRepository.findByPermName(permission.getPermName());
    }

    @Override
    public @NotNull List<Permission> readPermissions(@NotNull Permission permission) throws RBACException {
        MineSpecification<Permission> search = permission.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readPermissions]:    The RBAC permission searchCondition is required and cannot be null");
        }
        return permissionRepository.findAll(search);
    }

    // -------------------------------------------------------------------------
    // Role permissions
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<Permission> rolePermissions(@NotNull Role role) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        Optional<Role> optionalRole = reviewMgr.readRole(role);
        if (optionalRole.isPresent()) {
            Set<Permission> permissions = optionalRole.get().getPermissions();
            if (permissions == null || permissions.isEmpty()) {
                return Collections.emptyList();
            }
            return List.copyOf(permissions);
        }
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Actor permissions
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<Permission> actorPermissions(@NotNull Actor actor) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Role> roles = reviewMgr.assignedRoles(actor);
        try {
            List<Long> permissionIds = mapMgr.find(MapTableConstants.ROLE_PERMISSION, "permission_id", "role_id",
                            roles.stream().map(Role::getId).toList())
                    .stream().toList();
            if (permissionIds.isEmpty()) {
                return Collections.emptyList();
            }
            Permission permission = new Permission();
            for (Long permissionId : permissionIds) {
                permission.setId(permissionId);
                permission.orEq("id");
            }
            return reviewMgr.readPermissions(permission);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    // -------------------------------------------------------------------------
    // Session
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<Role> sessionRoles(@NotNull Session session) throws RBACException {
        List<Role> roles = session.getRoles();
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream().filter(Role::activated).collect(Collectors.toList());
    }

    @Override
    public @NotNull List<Permission> sessionPermissions(@NotNull Session session) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Role> roles = reviewMgr.sessionRoles(session);
        return roles.stream().flatMap(role -> role.getPermissions().stream()).collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Assigned actors / roles
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<Actor> assignedActors(@NotNull Role role) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        Optional<Role> optionalRole = reviewMgr.readRole(role);
        if (optionalRole.isPresent()) {
            Set<Actor> actors = optionalRole.get().getActors();
            if (actors == null || actors.isEmpty()) {
                return Collections.emptyList();
            }
            return List.copyOf(actors);
        }
        return Collections.emptyList();
    }

    @Override
    public @NotNull List<Role> assignedRoles(@NotNull Actor actor) throws RBACException {
        Optional<Actor> optionalActor;
        if (actor.getId() != null) {
            optionalActor = actorRepository.findById(actor.getId());
        } else if (StringUtils.hasText(actor.getActorName())) {
            optionalActor = actorRepository.findByActorName(actor.getActorName());
        } else {
            return Collections.emptyList();
        }
        if (optionalActor.isPresent()) {
            Set<Role> roles = optionalActor.get().getRoles();
            if (roles == null || roles.isEmpty()) {
                return Collections.emptyList();
            }
            return List.copyOf(roles);
        }
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Permission roles / actors
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<Role> permissionRoles(@NotNull Permission permission) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        Optional<Permission> optionalPermission = reviewMgr.readPermission(permission);
        if (optionalPermission.isEmpty()) {
            throw new ValidationException("[permissionRoles]:    The RBAC permission[id: %s] entity is not exist"
                    .formatted(permission.getId()));
        }
        Set<Role> roles = permission.getRoles();
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(permission.getRoles());
    }

    @Override
    public @NotNull List<Actor> permissionActors(@NotNull Permission permission) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Long> roleIds = reviewMgr.permissionRoles(permission)
                .stream().map(Role::getId).distinct().toList();
        try {
            List<Long> actorIds = mapMgr.find(MapTableConstants.ROLE_ACTOR, "actor_id", "role_id", roleIds);
            return actorRepository.findAllById(actorIds);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    private RBACException rethrow(ServiceException e) {
        if (e instanceof RBACException rbacException) {
            return rbacException;
        }
        return new RBACException(e.getMessage(), e);
    }
}
