package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.jpa.manager.MapMgr;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.OrgUnit;
import com.minesunny.permission.core.entity.actor.Post;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.entity.perm.Policy;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.repository.ActorRepository;
import com.minesunny.permission.core.repository.PermissionRepository;
import com.minesunny.permission.core.repository.PolicyRepository;
import com.minesunny.permission.core.repository.RoleRepository;
import com.minesunny.permission.core.repository.UserRepository;
import com.minesunny.permission.core.util.NaturalKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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
    private final PolicyRepository policyRepository;
    private final MapMgr mapMgr;

    // -------------------------------------------------------------------------
    // Actor CRUD
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Actor> T addActor(@NotNull T actor) throws RBACException {
        NaturalKeys.requireActorName(actor);
        if (actorRepository.findByActorName(actor.getActorName()).isPresent()) {
            throw new ValidationException("[addActor]:  The RBAC actor entity [actorName: %s] already exists"
                    .formatted(actor.getActorName()));
        }
        return (T) actorRepository.saveAndFlush(actor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Actor> T updateActor(@NotNull String actorName, @NotNull T actor) throws RBACException {
        Actor storeActor = actorRepository.findByActorName(actorName)
                .orElseThrow(() -> new ValidationException(
                        "[updateActor]:  The RBAC actor entity [actorName: %s] does not exist".formatted(actorName)));
        String newActorName = actor.getActorName();
        if (newActorName != null && !newActorName.equals(actorName)
                && actorRepository.findByActorName(newActorName).isPresent()) {
            throw new ValidationException("[updateActor]:  The RBAC actor entity [actorName: %s] already exists"
                    .formatted(newActorName));
        }
        List<String> excludes = new ArrayList<>(List.of("id", "roles"));
        if (actor instanceof User) {
            excludes.addAll(List.of("post", "managerOrgUnits", "orgUnits"));
        } else if (actor instanceof OrgUnit) {
            excludes.addAll(List.of("users", "managerUsers"));
        } else if (actor instanceof Post) {
            excludes.add("users");
        }
        BeanUtils.copyProperties(actor, storeActor, excludes.toArray(new String[0]));
        return (T) actorRepository.saveAndFlush(storeActor);
    }

    @Override
    public void deleteActor(@NotNull Actor actor) throws RBACException {
        Actor storeActor = resolveActor(actor, "deleteActor");
        Map<String, List<Long>> actorMap = new HashMap<>();
        actorMap.put("actor_id", List.of(storeActor.getId()));
        try {
            mapMgr.delete(ROLE_ACTOR, actorMap);
            if (storeActor instanceof User) {
                Map<String, List<Long>> userMap = new HashMap<>();
                userMap.put("user_id", List.of(storeActor.getId()));
                mapMgr.delete(ORG_UNIT_USER, userMap);
                mapMgr.delete(POST_USER, userMap);
            }
        } catch (ServiceException e) {
            throw rethrow(e);
        }
        actorRepository.delete(storeActor);
        actorRepository.flush();
    }

    // -------------------------------------------------------------------------
    // Role CRUD
    // -------------------------------------------------------------------------

    @Override
    public @NotNull Role addRole(@NotNull Role role) throws RBACException {
        NaturalKeys.requireRoleName(role);
        if (roleRepository.findByRoleName(role.getRoleName()).isPresent()) {
            throw new ValidationException("[addRole]:    The RBAC role entity [roleName: %s ] already exists"
                    .formatted(role.getRoleName()));
        }
        Role newRole = new Role();
        BeanUtils.copyProperties(role, newRole, "permissions", "actors");
        return roleRepository.saveAndFlush(newRole);
    }

    @Override
    public @NotNull Role updateRole(@NotNull String roleName, @NotNull Role role) throws RBACException {
        Role storeRole = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new ValidationException(
                        "[updateRole]:    The RBAC role entity [roleName: %s] does not exist".formatted(roleName)));
        String newRoleName = role.getRoleName();
        if (newRoleName != null && !newRoleName.equals(roleName)
                && roleRepository.findByRoleName(newRoleName).isPresent()) {
            throw new ValidationException("[updateRole]:    The RBAC role entity [roleName: %s ] already exists"
                    .formatted(newRoleName));
        }
        BeanUtils.copyProperties(role, storeRole, "id", "actors", "permissions");
        return roleRepository.saveAndFlush(storeRole);
    }

    @Override
    public void deleteRole(@NotNull Role role) throws RBACException {
        NaturalKeys.requireRoleName(role);
        Role storeRole = roleRepository.findByRoleName(role.getRoleName())
                .orElseThrow(() -> new ValidationException(
                        "[deleteRole]:    The RBAC role entity [roleName: %s] does not exist"
                                .formatted(role.getRoleName())));
        roleRepository.delete(storeRole);
        roleRepository.flush();
    }

    // -------------------------------------------------------------------------
    // Actor-Role assignment
    // -------------------------------------------------------------------------

    @Override
    public void assignActor(@NotNull Actor actor, @NotNull Role role) throws RBACException {
        ActorRoleIds ids = resolveActorRole(actor, role, "assignActor");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("actor_id", List.of(ids.actorId()));
        map.put("role_id", List.of(ids.roleId()));
        try {
            if (mapMgr.exist(ROLE_ACTOR, map)) {
                throw new ValidationException("[assignActor]:    The RBAC role[id: %s] entity has assigned to actor[id: %s] entity"
                        .formatted(ids.roleId(), ids.actorId()));
            }
            mapMgr.save(ROLE_ACTOR, map);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    @Override
    public void deassignActor(@NotNull Actor actor, @NotNull Role role) throws RBACException {
        ActorRoleIds ids = resolveActorRole(actor, role, "deassignActor");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("actor_id", List.of(ids.actorId()));
        map.put("role_id", List.of(ids.roleId()));
        try {
            if (!mapMgr.exist(ROLE_ACTOR, map)) {
                throw new ValidationException("[deassignActor]:    The RBAC role[id: %s] entity has deassigned from actor[id: %s] entity"
                        .formatted(ids.roleId(), ids.actorId()));
            }
            mapMgr.delete(ROLE_ACTOR, map);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    // -------------------------------------------------------------------------
    // Permission CRUD + grant/revoke
    // -------------------------------------------------------------------------

    @Override
    public @NotNull Permission addPermission(@NotNull Permission permission) throws RBACException {
        NaturalKeys.requirePermName(permission);
        if (permissionRepository.findByPermName(permission.getPermName()).isPresent()) {
            throw new ValidationException("[addPermission]:    The RBAC permission entity [permName: %s ] already exists"
                    .formatted(permission.getPermName()));
        }
        Permission storePermission = permissionRepository.saveAndFlush(permission);
        if (permission.getPolicy() != null) {
            Policy policy = permission.getPolicy();
            policy.setPermName(storePermission.getPermName());
            policy.setOwnerName(null);
            if (policy.getId() == null) {
                policy = policyRepository.saveAndFlush(policy);
                permission.setPolicy(policy);
            }
        }
        return storePermission;
    }

    @Override
    public @NotNull Permission updatePermission(@NotNull String permName, @NotNull Permission permission) throws RBACException {
        Permission storePermission = permissionRepository.findByPermName(permName)
                .orElseThrow(() -> new ValidationException(
                        "[updatePermission]:    The RBAC permission entity [permName: %s] does not exist".formatted(permName)));
        String newPermName = permission.getPermName();
        if (newPermName != null && !newPermName.equals(permName)
                && permissionRepository.findByPermName(newPermName).isPresent()) {
            throw new ValidationException("[updatePermission]:    The RBAC permission entity [permName: %s ] already exists"
                    .formatted(newPermName));
        }
        BeanUtils.copyProperties(permission, storePermission, "id");
        return permissionRepository.saveAndFlush(storePermission);
    }

    @Override
    public void deletePermission(@NotNull Permission permission) throws RBACException {
        NaturalKeys.requirePermName(permission);
        Permission storePermission = permissionRepository.findByPermName(permission.getPermName())
                .orElseThrow(() -> new ValidationException(
                        "[deletePermission]:    The RBAC permission entity [permName: %s] does not exist"
                                .formatted(permission.getPermName())));
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(storePermission.getId()));
        try {
            if (mapMgr.exist(ROLE_PERMISSION, map)) {
                mapMgr.delete(ROLE_PERMISSION, map);
            }
        } catch (ServiceException e) {
            throw rethrow(e);
        }
        List<Policy> policies = policyRepository.findByPermNameAndOwnerName(storePermission.getPermName(), null);
        if (!policies.isEmpty()) {
            policyRepository.deleteAll(policies);
        }
        permissionRepository.delete(storePermission);
        permissionRepository.flush();
    }

    @Override
    public void grantPermission(@NotNull Permission permission, @NotNull Role role) throws RBACException {
        PermissionRoleIds ids = resolvePermissionRole(permission, role, "grantPermission");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(ids.permissionId()));
        map.put("role_id", List.of(ids.roleId()));
        try {
            if (mapMgr.exist(ROLE_PERMISSION, map)) {
                throw new ValidationException("[grantPermission]:    The RBAC permission[id: %s] entity has grant to role[id: %s] entity"
                        .formatted(ids.permissionId(), ids.roleId()));
            }
            mapMgr.save(ROLE_PERMISSION, map);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    @Override
    public void revokePermission(@NotNull Permission permission, @NotNull Role role) throws RBACException {
        PermissionRoleIds ids = resolvePermissionRole(permission, role, "revokePermission");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("permission_id", List.of(ids.permissionId()));
        map.put("role_id", List.of(ids.roleId()));
        try {
            if (!mapMgr.exist(ROLE_PERMISSION, map)) {
                throw new ValidationException("[revokePermission]:    The RBAC permission[id: %s] entity has revoke from role[id: %s] entity"
                        .formatted(ids.permissionId(), ids.roleId()));
            }
            mapMgr.delete(ROLE_PERMISSION, map);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    @Override
    public void addPermissionPolicy(@NotNull String username, @NotNull String permName, @NotNull Policy policy) throws RBACException {
        userRepository.findByUsername(username)
                .orElseThrow(() -> new ValidationException(
                        "[addPermissionPolicy]: The RBAC user entity [username: %s] does not exist".formatted(username)));
        permissionRepository.findByPermName(permName)
                .orElseThrow(() -> new ValidationException(
                        "[addPermissionPolicy]: The RBAC permission entity [permName: %s] does not exist".formatted(permName)));

        List<Policy> existing = policyRepository.findByPermNameAndOwnerName(permName, username);
        if (!existing.isEmpty()) {
            throw new ValidationException("[addPermissionPolicy]: Policy mapping for permission [permName: %s] and user [username: %s] already exists"
                    .formatted(permName, username));
        }

        policy.setPermName(permName);
        policy.setOwnerName(username);
        policyRepository.saveAndFlush(policy);
    }

    @Override
    public void updatePermissionPolicy(@NotNull String username, @NotNull String permName, @NotNull Policy policy) throws RBACException {
        userRepository.findByUsername(username)
                .orElseThrow(() -> new ValidationException(
                        "[updatePermissionPolicy]: The RBAC user entity [username: %s] does not exist".formatted(username)));
        permissionRepository.findByPermName(permName)
                .orElseThrow(() -> new ValidationException(
                        "[updatePermissionPolicy]: The RBAC permission entity [permName: %s] does not exist".formatted(permName)));

        List<Policy> existing = policyRepository.findByPermNameAndOwnerName(permName, username);
        if (existing.isEmpty()) {
            throw new ValidationException(
                    "[updatePermissionPolicy]: User policy for permission [permName: %s] and user [username: %s] does not exist"
                            .formatted(permName, username));
        }

        policy.setId(existing.get(0).getId());
        policy.setPermName(permName);
        policy.setOwnerName(username);
        policyRepository.saveAndFlush(policy);
    }

    @Override
    public void deletePermissionPolicy(@NotNull String username, @NotNull String permName) throws RBACException {
        userRepository.findByUsername(username)
                .orElseThrow(() -> new ValidationException(
                        "[deletePermissionPolicy]: The RBAC user entity [username: %s] does not exist".formatted(username)));
        permissionRepository.findByPermName(permName)
                .orElseThrow(() -> new ValidationException(
                        "[deletePermissionPolicy]: The RBAC permission entity [permName: %s] does not exist".formatted(permName)));

        List<Policy> policies = policyRepository.findByPermNameAndOwnerName(permName, username);
        if (!policies.isEmpty()) {
            policyRepository.deleteAll(policies);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private record ActorRoleIds(Long actorId, Long roleId) {}

    private record PermissionRoleIds(Long permissionId, Long roleId) {}

    private Actor resolveActor(Actor actor, String op) throws RBACException {
        if (actor.getId() != null) {
            return actorRepository.findById(actor.getId())
                    .orElseThrow(() -> new ValidationException(
                            "[%s]:  The RBAC actor[id: %s] does not exist".formatted(op, actor.getId())));
        }
        NaturalKeys.requireActorName(actor);
        return actorRepository.findByActorName(actor.getActorName())
                .orElseThrow(() -> new ValidationException(
                        "[%s]:  The RBAC actor[actorName: %s] does not exist".formatted(op, actor.getActorName())));
    }

    private ActorRoleIds resolveActorRole(Actor actor, Role role, String op) throws RBACException {
        Long actorId = resolveActor(actor, op).getId();
        NaturalKeys.requireRoleName(role);
        Role storeRole = roleRepository.findByRoleName(role.getRoleName())
                .orElseThrow(() -> new ValidationException(
                        "[%s]:    The RBAC role[roleName: %s] does not exist".formatted(op, role.getRoleName())));
        return new ActorRoleIds(actorId, storeRole.getId());
    }

    private PermissionRoleIds resolvePermissionRole(Permission permission, Role role, String op) throws RBACException {
        NaturalKeys.requirePermName(permission);
        NaturalKeys.requireRoleName(role);
        Permission storePermission = permissionRepository.findByPermName(permission.getPermName())
                .orElseThrow(() -> new ValidationException(
                        "[%s]:    The RBAC permission[permName: %s] does not exist".formatted(op, permission.getPermName())));
        Role storeRole = roleRepository.findByRoleName(role.getRoleName())
                .orElseThrow(() -> new ValidationException(
                        "[%s]:    The RBAC role[roleName: %s] does not exist".formatted(op, role.getRoleName())));
        return new PermissionRoleIds(storePermission.getId(), storeRole.getId());
    }

    private RBACException rethrow(ServiceException e) {
        if (e instanceof RBACException rbacException) {
            return rbacException;
        }
        return new RBACException(e.getMessage(), e);
    }
}
