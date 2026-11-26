package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.MineSpecification;
import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.jpa.manager.MapMgr;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.Session;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.*;
import com.minesunny.permission.core.i18n.RBACMessageKeys;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.repository.ActorRepository;
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
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewMgrImpl implements ReviewMgr {
    private final ActorRepository actorRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MapMgr mapMgr;

    // -------------------------------------------------------------------------
    // Actor read & find
    // -------------------------------------------------------------------------

    @Override
    public Actor findActor(@NotNull String actorName) throws RBACException {
        NaturalKeys.require(actorName, "actor's actorName");
        return actorRepository.findByActorName(actorName).orElse(null);
    }

    @Override
    public Actor findActor(@NotNull Actor actor) throws RBACException {
        NaturalKeys.requireActorName(actor);
        return findActor(actor.getActorName());
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

    @Override
    public @NotNull Actor readActor(@NotNull Actor actor) throws RBACException {
        NaturalKeys.requireActorName(actor);
        return readActor(actor.getActorName());
    }

    @Override
    public List<Actor> readActors(@NotNull Actor actor) throws RBACException {
        MineSpecification<Actor> search = actor.specification();
        if (Objects.isNull(search)) {
            return Collections.emptyList();
        }
        return actorRepository.findAll(search);
    }

    // -------------------------------------------------------------------------
    // User read & find
    // -------------------------------------------------------------------------

    @Override
    public User findUser(@NotNull String username) throws RBACException {
        NaturalKeys.require(username, "user's username");
        return userRepository.findByUsername(username).orElse(null);
    }

    @Override
    public User findUser(@NotNull User user) throws RBACException {
        NaturalKeys.requireUsername(user);
        return findUser(user.getUsername());
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

    @Override
    public @NotNull User readUser(@NotNull User user) throws RBACException {
        NaturalKeys.requireUsername(user);
        return readUser(user.getUsername());
    }

    @Override
    public @NotNull List<User> readUsers(@NotNull User user) throws RBACException {
        MineSpecification<User> search = user.specification();
        if (Objects.isNull(search)) {
            return Collections.emptyList();
        }
        return userRepository.findAll(search);
    }

    // -------------------------------------------------------------------------
    // Role read & find
    // -------------------------------------------------------------------------

    @Override
    public Role findRole(@NotNull String roleName) throws RBACException {
        NaturalKeys.require(roleName, "role's roleName");
        return roleRepository.findByRoleName(roleName).orElse(null);
    }

    @Override
    public Role findRole(@NotNull Role role) throws RBACException {
        NaturalKeys.requireRoleName(role);
        return findRole(role.getRoleName());
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

    @Override
    public @NotNull Role readRole(@NotNull Role role) throws RBACException {
        NaturalKeys.requireRoleName(role);
        return readRole(role.getRoleName());
    }

    @Override
    public @NotNull List<Role> readRoles(@NotNull Role role) throws RBACException {
        MineSpecification<Role> search = role.specification();
        if (Objects.isNull(search)) {
            return Collections.emptyList();
        }
        return roleRepository.findAll(search);
    }

    // -------------------------------------------------------------------------
    // Permission read & find
    // -------------------------------------------------------------------------

    @Override
    public Permission findPermission(@NotNull String permName) throws RBACException {
        NaturalKeys.require(permName, "permission's permName");
        return permissionRepository.findByPermName(permName).orElse(null);
    }

    @Override
    public Permission findPermission(@NotNull Permission permission) throws RBACException {
        NaturalKeys.requirePermName(permission);
        return findPermission(permission.getPermName());
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
    public @NotNull Permission readPermission(@NotNull Permission permission) throws RBACException {
        NaturalKeys.requirePermName(permission);
        return readPermission(permission.getPermName());
    }

    @Override
    public @NotNull List<Permission> readPermissions(@NotNull Permission permission) throws RBACException {
        MineSpecification<Permission> search = permission.specification();
        if (Objects.isNull(search)) {
            return Collections.emptyList();
        }
        return permissionRepository.findAll(search);
    }

    // -------------------------------------------------------------------------
    // Role permissions
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<Permission> rolePermissions(@NotNull String roleName) throws RBACException {
        NaturalKeys.require(roleName, "role's roleName");
        return rolePermissions(Role.builder().roleName(roleName).build());
    }

    @Override
    public @NotNull List<Permission> rolePermissions(@NotNull Role role) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        Role targetRole = reviewMgr.findRole(role);
        if (targetRole == null) {
            return Collections.emptyList();
        }
        Long roleId = targetRole.getId();
        try {
            List<Long> permissionIds = mapMgr.find(MapTableConstants.ROLE_PERMISSION, "permission_id", "role_id", List.of(roleId));
            if (permissionIds.isEmpty()) {
                return Collections.emptyList();
            }
            return permissionRepository.findAllById(permissionIds.stream().distinct().toList());
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Actor permissions
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<Permission> actorPermissions(@NotNull String actorName) throws RBACException {
        NaturalKeys.require(actorName, "actor's actorName");
        Actor actor = actorRepository.findByActorName(actorName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ACTOR, "actorPermissions", "actorName: " + actorName));
        return actorPermissions(actor);
    }

    @Override
    public @NotNull List<Permission> actorPermissions(@NotNull Actor actor) throws RBACException {
        Long actorId = actor.getId();
        if (actorId == null) {
            String actorName = actor.getActorName();
            if (StringUtils.hasText(actorName)) {
                Optional<Actor> optionalActor = actorRepository.findByActorName(actorName);
                if (optionalActor.isEmpty()) {
                    return Collections.emptyList();
                }
                actorId = optionalActor.get().getId();
            } else {
                return Collections.emptyList();
            }
        }
        return permissionRepository.findPermissionsByActorIdFast(actorId);
    }

    // -------------------------------------------------------------------------
    // Session
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<Role> sessionRoles(@NotNull Session session) throws RBACException {
        List<Role> roles = session.getRoles();
        if (Objects.isNull(roles) || roles.isEmpty()) {
            return Collections.emptyList();
        }
        // NIST RBAC 动态授权约束：session_roles(s) ⊆ assigned_roles(u)
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Role> assignedRoles = Objects.nonNull(session.getUser())
                ? reviewMgr.assignedRoles(session.getUser())
                : Collections.emptyList();

        if (assignedRoles.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> assignedRoleNames = assignedRoles.stream()
                .map(Role::getRoleName)
                .collect(Collectors.toSet());

        return roles.stream()
                .filter(r -> assignedRoleNames.contains(r.getRoleName()))
                .filter(Role::activated)
                .collect(Collectors.toList());
    }

    @Override
    public @NotNull List<Permission> sessionPermissions(@NotNull Session session) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Role> roles = reviewMgr.sessionRoles(session);
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = roles.stream().map(Role::getId).distinct().toList();
        try {
            List<Long> permissionIds = mapMgr.find(MapTableConstants.ROLE_PERMISSION, "permission_id", "role_id", roleIds);
            if (permissionIds.isEmpty()) {
                return Collections.emptyList();
            }
            return permissionRepository.findAllById(permissionIds.stream().distinct().toList());
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Assigned actors / roles
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<Actor> assignedActors(@NotNull String roleName) throws RBACException {
        NaturalKeys.require(roleName, "role's roleName");
        return assignedActors(Role.builder().roleName(roleName).build());
    }

    @Override
    public @NotNull List<Actor> assignedActors(@NotNull Role role) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        Role targetRole = reviewMgr.findRole(role);
        if (targetRole == null) {
            return Collections.emptyList();
        }
        Long roleId = targetRole.getId();
        try {
            List<Long> actorIds = mapMgr.find(MapTableConstants.ROLE_ACTOR, "actor_id", "role_id", List.of(roleId));
            if (actorIds.isEmpty()) {
                return Collections.emptyList();
            }
            return actorRepository.findAllById(actorIds.stream().distinct().toList());
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    @Override
    public @NotNull List<Role> assignedRoles(@NotNull String actorName) throws RBACException {
        NaturalKeys.require(actorName, "actor's actorName");
        Actor actor = actorRepository.findByActorName(actorName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ACTOR, "assignedRoles", "actorName: " + actorName));
        return assignedRoles(actor);
    }

    @Override
    public @NotNull List<Role> assignedRoles(@NotNull Actor actor) throws RBACException {
        Long actorId = actor.getId();
        if (actorId == null) {
            String actorName = actor.getActorName();
            if (StringUtils.hasText(actorName)) {
                Optional<Actor> optionalActor = actorRepository.findByActorName(actorName);
                if (optionalActor.isEmpty()) {
                    return Collections.emptyList();
                }
                actorId = optionalActor.get().getId();
            } else {
                return Collections.emptyList();
            }
        }
        try {
            List<Long> roleIds = mapMgr.find(MapTableConstants.ROLE_ACTOR, "role_id", "actor_id", List.of(actorId));
            if (roleIds.isEmpty()) {
                return Collections.emptyList();
            }
            return roleRepository.findAllById(roleIds.stream().distinct().toList());
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Permission roles / actors
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<Role> permissionRoles(@NotNull Permission permission) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        Permission targetPerm = reviewMgr.readPermission(permission);
        Long permId = targetPerm.getId();
        try {
            List<Long> roleIds = mapMgr.find(MapTableConstants.ROLE_PERMISSION, "role_id", "permission_id", List.of(permId));
            if (roleIds.isEmpty()) {
                return Collections.emptyList();
            }
            return roleRepository.findAllById(roleIds.stream().distinct().toList());
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
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
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Role and Permission Checks
    // -------------------------------------------------------------------------

    @Override
    public boolean hasRole(@NotNull String actorName, @NotNull String roleName) throws RBACException {
        NaturalKeys.require(actorName, "actor's actorName");
        NaturalKeys.require(roleName, "role's roleName");
        Actor actor = actorRepository.findByActorName(actorName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ACTOR, "hasRole", "actorName: " + actorName));
        return hasRole(actor, Role.builder().roleName(roleName).build());
    }

    @Override
    public boolean hasRole(@NotNull Actor actor, @NotNull Role role) throws RBACException {
        Long actorId = actor.getId();
        if (actorId == null) {
            String actorName = actor.getActorName();
            if (StringUtils.hasText(actorName)) {
                Optional<Actor> optionalActor = actorRepository.findByActorName(actorName);
                if (optionalActor.isEmpty()) {
                    return false;
                }
                actorId = optionalActor.get().getId();
            } else {
                return false;
            }
        }
        NaturalKeys.requireRoleName(role);
        return roleRepository.hasRoleFast(actorId, role.getRoleName());
    }

    @Override
    public boolean hasPermission(@NotNull String actorName, @NotNull String permName) throws RBACException {
        NaturalKeys.require(actorName, "actor's actorName");
        NaturalKeys.require(permName, "permission's permName");
        Actor actor = actorRepository.findByActorName(actorName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ACTOR, "hasPermission", "actorName: " + actorName));
        return hasPermission(actor, Permission.builder().permName(permName).build());
    }

    @Override
    public boolean hasPermission(@NotNull Actor actor, @NotNull Permission permission) throws RBACException {
        Long actorId = actor.getId();
        if (actorId == null) {
            String actorName = actor.getActorName();
            if (StringUtils.hasText(actorName)) {
                Optional<Actor> optionalActor = actorRepository.findByActorName(actorName);
                if (optionalActor.isEmpty()) {
                    return false;
                }
                actorId = optionalActor.get().getId();
            } else {
                return false;
            }
        }
        NaturalKeys.requirePermName(permission);
        return permissionRepository.hasPermissionFast(actorId, permission.getPermName());
    }

    // -------------------------------------------------------------------------
    // NIST CheckAccess & OperationsOnResource Functions
    // -------------------------------------------------------------------------

    @Override
    public boolean checkAccess(@NotNull Session session, @NotNull String operation, @NotNull String resource) throws RBACException {
        if (Objects.isNull(session)) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_SESSION_REQUIRED);
        }
        NaturalKeys.require(operation, "operation");
        NaturalKeys.require(resource, "resource");

        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Permission> sessionPerms = reviewMgr.sessionPermissions(session);
        if (sessionPerms.isEmpty()) {
            return false;
        }

        // 拒绝优先原则（Deny-Overrides）：如果存在明确的 DENY 规则匹配该 (op, resource)，则直接拒绝
        boolean hasDeny = sessionPerms.stream()
                .filter(p -> "DENY".equalsIgnoreCase(p.getEffect()))
                .anyMatch(p -> resource.equals(p.getResource()) && operation.equals(p.getOperation()));
        if (hasDeny) {
            return false;
        }

        // 正向允许匹配（ALLOW 或未指定 effect 默认为 ALLOW）
        return sessionPerms.stream()
                .filter(p -> !"DENY".equalsIgnoreCase(p.getEffect()))
                .anyMatch(p -> resource.equals(p.getResource()) && operation.equals(p.getOperation()));
    }

    @Override
    public boolean checkAccess(@NotNull String actorName, @NotNull String operation, @NotNull String resource) throws RBACException {
        NaturalKeys.require(actorName, "actor's actorName");
        NaturalKeys.require(operation, "operation");
        NaturalKeys.require(resource, "resource");

        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Permission> actorPerms = reviewMgr.actorPermissions(actorName);
        if (actorPerms.isEmpty()) {
            return false;
        }

        boolean hasDeny = actorPerms.stream()
                .filter(p -> "DENY".equalsIgnoreCase(p.getEffect()))
                .anyMatch(p -> resource.equals(p.getResource()) && operation.equals(p.getOperation()));
        if (hasDeny) {
            return false;
        }

        return actorPerms.stream()
                .filter(p -> !"DENY".equalsIgnoreCase(p.getEffect()))
                .anyMatch(p -> resource.equals(p.getResource()) && operation.equals(p.getOperation()));
    }

    @Override
    public @NotNull List<String> roleOperationsOnResource(@NotNull String roleName, @NotNull String resource) throws RBACException {
        NaturalKeys.require(roleName, "roleName");
        NaturalKeys.require(resource, "resource");
        Role role = Role.builder().roleName(roleName).build();
        ReviewMgr self = (ReviewMgr) AopContext.currentProxy();
        return self.roleOperationsOnResource(role, resource);
    }

    @Override
    public @NotNull List<String> roleOperationsOnResource(@NotNull Role role, @NotNull String resource) throws RBACException {
        NaturalKeys.require(resource, "resource");
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Permission> permissions = reviewMgr.rolePermissions(role);
        if (permissions.isEmpty()) {
            return Collections.emptyList();
        }
        return permissions.stream()
                .filter(p -> resource.equals(p.getResource()))
                .filter(p -> !"DENY".equalsIgnoreCase(p.getEffect()))
                .map(Permission::getOperation)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public @NotNull List<String> userOperationsOnResource(@NotNull String actorName, @NotNull String resource) throws RBACException {
        NaturalKeys.require(actorName, "actor's actorName");
        NaturalKeys.require(resource, "resource");
        Actor actor = Actor.builder().actorName(actorName).build();
        ReviewMgr self = (ReviewMgr) AopContext.currentProxy();
        return self.userOperationsOnResource(actor, resource);
    }

    @Override
    public @NotNull List<String> userOperationsOnResource(@NotNull Actor actor, @NotNull String resource) throws RBACException {
        NaturalKeys.require(resource, "resource");
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Permission> permissions = reviewMgr.actorPermissions(actor);
        if (permissions.isEmpty()) {
            return Collections.emptyList();
        }
        return permissions.stream()
                .filter(p -> resource.equals(p.getResource()))
                .filter(p -> !"DENY".equalsIgnoreCase(p.getEffect()))
                .map(Permission::getOperation)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // NIST Hierarchical RBAC Review Functions
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<Role> authorizedRoles(@NotNull String actorName) throws RBACException {
        NaturalKeys.require(actorName, "actor's actorName");
        Actor actor = actorRepository.findByActorName(actorName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ACTOR, "authorizedRoles", actorName));
        ReviewMgr self = (ReviewMgr) AopContext.currentProxy();
        return self.authorizedRoles(actor);
    }

    @Override
    public @NotNull List<Role> authorizedRoles(@NotNull Actor actor) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Role> directRoles = reviewMgr.assignedRoles(actor);
        if (directRoles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> directRoleIds = directRoles.stream().map(Role::getId).distinct().toList();
        Set<Long> allRoleIds = new HashSet<>(directRoleIds);
        Queue<Long> queue = new ArrayDeque<>(directRoleIds);

        try {
            while (!queue.isEmpty()) {
                Long current = queue.poll();
                List<Long> childRoleIds = mapMgr.find(MapTableConstants.ROLE_HIERARCHY, "child_role_id", "parent_role_id", List.of(current));
                for (Long childId : childRoleIds) {
                    if (allRoleIds.add(childId)) {
                        queue.add(childId);
                    }
                }
            }
            return roleRepository.findAllById(allRoleIds);
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    @Override
    public @NotNull List<Actor> authorizedActors(@NotNull String roleName) throws RBACException {
        NaturalKeys.require(roleName, "roleName");
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE, "authorizedActors", roleName));
        ReviewMgr self = (ReviewMgr) AopContext.currentProxy();
        return self.authorizedActors(role);
    }

    @Override
    public @NotNull List<Actor> authorizedActors(@NotNull Role role) throws RBACException {
        Long roleId = role.getId();
        if (roleId == null) {
            NaturalKeys.requireRoleName(role);
            roleId = roleRepository.findByRoleName(role.getRoleName())
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_ROLE, "authorizedActors", role.getRoleName()))
                    .getId();
        }

        // 向上寻找所有父角色 (祖先上位角色)，其分配的主体均可继承该角色
        Set<Long> ascendantRoleIds = new HashSet<>();
        ascendantRoleIds.add(roleId);
        Queue<Long> queue = new ArrayDeque<>();
        queue.add(roleId);

        try {
            while (!queue.isEmpty()) {
                Long current = queue.poll();
                List<Long> parentRoleIds = mapMgr.find(MapTableConstants.ROLE_HIERARCHY, "parent_role_id", "child_role_id", List.of(current));
                for (Long parentId : parentRoleIds) {
                    if (ascendantRoleIds.add(parentId)) {
                        queue.add(parentId);
                    }
                }
            }
            List<Long> actorIds = mapMgr.find(MapTableConstants.ROLE_ACTOR, "actor_id", "role_id", new ArrayList<>(ascendantRoleIds));
            if (actorIds.isEmpty()) {
                return Collections.emptyList();
            }
            return actorRepository.findAllById(actorIds.stream().distinct().toList());
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    @Override
    public @NotNull List<Permission> authorizedPermissions(@NotNull String roleName) throws RBACException {
        NaturalKeys.require(roleName, "roleName");
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ROLE, "authorizedPermissions", roleName));
        ReviewMgr self = (ReviewMgr) AopContext.currentProxy();
        return self.authorizedPermissions(role);
    }

    @Override
    public @NotNull List<Permission> authorizedPermissions(@NotNull Role role) throws RBACException {
        Long roleId = role.getId();
        if (roleId == null) {
            NaturalKeys.requireRoleName(role);
            roleId = roleRepository.findByRoleName(role.getRoleName())
                    .orElseThrow(() -> new RBACNotFoundException(
                            RBACMessageKeys.NOT_FOUND_ROLE, "authorizedPermissions", role.getRoleName()))
                    .getId();
        }

        // 向下寻找该角色自身及其所有子角色的 ID 集合
        Set<Long> descendantRoleIds = new HashSet<>();
        descendantRoleIds.add(roleId);
        Queue<Long> queue = new ArrayDeque<>();
        queue.add(roleId);

        try {
            while (!queue.isEmpty()) {
                Long current = queue.poll();
                List<Long> childRoleIds = mapMgr.find(MapTableConstants.ROLE_HIERARCHY, "child_role_id", "parent_role_id", List.of(current));
                for (Long childId : childRoleIds) {
                    if (descendantRoleIds.add(childId)) {
                        queue.add(childId);
                    }
                }
            }
            List<Long> permissionIds = mapMgr.find(MapTableConstants.ROLE_PERMISSION, "permission_id", "role_id", new ArrayList<>(descendantRoleIds));
            if (permissionIds.isEmpty()) {
                return Collections.emptyList();
            }
            return permissionRepository.findAllById(permissionIds.stream().distinct().toList());
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }

    @Override
    public @NotNull List<Permission> authorizedActorPermissions(@NotNull String actorName) throws RBACException {
        NaturalKeys.require(actorName, "actor's actorName");
        Actor actor = actorRepository.findByActorName(actorName)
                .orElseThrow(() -> new RBACNotFoundException(
                        RBACMessageKeys.NOT_FOUND_ACTOR, "authorizedActorPermissions", actorName));
        ReviewMgr self = (ReviewMgr) AopContext.currentProxy();
        return self.authorizedActorPermissions(actor);
    }

    @Override
    public @NotNull List<Permission> authorizedActorPermissions(@NotNull Actor actor) throws RBACException {
        ReviewMgr reviewMgr = (ReviewMgr) AopContext.currentProxy();
        List<Role> allRoles = reviewMgr.authorizedRoles(actor);
        if (allRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = allRoles.stream().map(Role::getId).distinct().toList();
        try {
            List<Long> permissionIds = mapMgr.find(MapTableConstants.ROLE_PERMISSION, "permission_id", "role_id", roleIds);
            if (permissionIds.isEmpty()) {
                return Collections.emptyList();
            }
            return permissionRepository.findAllById(permissionIds.stream().distinct().toList());
        } catch (ServiceException e) {
            throw (e.getCause() instanceof RBACException rbac) ? rbac : new RBACException(e.getMessage(), e);
        }
    }
}
