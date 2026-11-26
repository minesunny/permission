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

import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.Session;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.exception.RBACConflictException;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.RBACNotFoundException;
import com.minesunny.permission.core.exception.RBACValidationException;
import com.minesunny.permission.core.i18n.RBACMessageKeys;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.manager.SessionMgr;
import com.minesunny.permission.core.util.NaturalKeys;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * RBAC 会话管理业务接口实现（SessionMgrImpl）。
 * <p>
 * 遵循架构规范：
 * <ul>
 *     <li>完全由声明的 {@code @Override public} 接口方法构成，杜绝私有业务逻辑方法。</li>
 *     <li>通过 Spring AOP 代理保持事务一致性与内部调用统一拦截。</li>
 *     <li>严格遵守 NIST RBAC 约束：$session\_roles(s) \subseteq assigned\_roles(u)$。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SessionMgrImpl implements SessionMgr {

    private final ReviewMgr reviewMgr;
    private final com.minesunny.permission.core.repository.sod.DSDSetRepository dsdSetRepository;

    @Override
    public @NotNull Session createSession(@NotNull User user, @NotNull List<Role> activeRoles) throws RBACException {
        if (Objects.isNull(user)) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_REQUIRED, "user");
        }
        User persistentUser = reviewMgr.readUser(user);

        Session session = Session.builder()
                .sessionId(UUID.randomUUID().toString())
                .user(persistentUser)
                .roles(new ArrayList<>())
                .build();

        if (Objects.nonNull(activeRoles)) {
            SessionMgr self = (SessionMgr) AopContext.currentProxy();
            for (Role role : activeRoles) {
                self.addActiveRole(session, role);
            }
        }
        return session;
    }

    @Override
    public @NotNull Session createSession(@NotNull String username, @NotNull List<String> activeRoleNames) throws RBACException {
        NaturalKeys.require(username, "username");
        User persistentUser = reviewMgr.readUser(username);

        List<Role> roles = new ArrayList<>();
        if (Objects.nonNull(activeRoleNames)) {
            for (String roleName : activeRoleNames) {
                roles.add(reviewMgr.readRole(roleName));
            }
        }
        SessionMgr self = (SessionMgr) AopContext.currentProxy();
        return self.createSession(persistentUser, roles);
    }

    @Override
    public void addActiveRole(@NotNull Session session, @NotNull Role role) throws RBACException {
        if (Objects.isNull(session)) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_SESSION_REQUIRED);
        }
        if (Objects.isNull(session.getUser())) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_REQUIRED, "session.user");
        }
        Role persistentRole = reviewMgr.readRole(role);

        // 1. 验证该角色当前是否已被分配给该用户 (session_roles(s) ⊆ assigned_roles(u))
        boolean hasRole = reviewMgr.hasRole(session.getUser(), persistentRole);
        if (!hasRole) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_ROLE_NOT_ASSIGNED_TO_USER,
                    "addActiveRole",
                    persistentRole.getRoleName(),
                    session.getUser().getUsername()
            );
        }

        // 2. 验证该角色是否已经在当前会话中激活
        List<Role> currentRoles = session.getRoles();
        if (Objects.isNull(currentRoles)) {
            currentRoles = new ArrayList<>();
            session.setRoles(currentRoles);
        }
        boolean alreadyActive = currentRoles.stream()
                .anyMatch(r -> persistentRole.getRoleName().equals(r.getRoleName()));
        if (alreadyActive) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_SESSION_ROLE_ALREADY_ACTIVE,
                    "addActiveRole",
                    persistentRole.getRoleName(),
                    session.getSessionId()
            );
        }

        // 3. 验证角色时空约束 (Temporal TRBAC: 是否处于生效时间窗口内且未被锁定)
        if (!persistentRole.activated()) {
            throw new RBACConflictException(
                    RBACMessageKeys.CONFLICT_ROLE_TEMPORAL_INACTIVE,
                    "addActiveRole",
                    persistentRole.getRoleName()
            );
        }

        // 4. 验证动态职责分离 (DSD) 约束
        // 形式化判定: ∀ (dsd_name, role_set, n) ∈ DSD, 若 persistentRole ∈ role_set,
        // 则已激活角色与 role_set 的交集大小 + 1 < n
        List<com.minesunny.permission.core.entity.sod.DSDSet> dsdSets = dsdSetRepository.findAll();
        if (!dsdSets.isEmpty()) {
            for (com.minesunny.permission.core.entity.sod.DSDSet dsdSet : dsdSets) {
                java.util.Set<String> dsdMemberRoleNames = dsdSet.getRoles().stream()
                        .map(Role::getRoleName)
                        .collect(java.util.stream.Collectors.toSet());
                if (dsdMemberRoleNames.contains(persistentRole.getRoleName())) {
                    long overlapCount = currentRoles.stream()
                            .filter(r -> dsdMemberRoleNames.contains(r.getRoleName()))
                            .distinct()
                            .count();
                    if (overlapCount + 1 >= dsdSet.getCardinality()) {
                        throw new RBACConflictException(
                                RBACMessageKeys.CONFLICT_DSD_VIOLATION,
                                "addActiveRole",
                                persistentRole.getRoleName(),
                                session.getSessionId(),
                                dsdSet.getName(),
                                dsdSet.getCardinality()
                        );
                    }
                }
            }
        }

        currentRoles.add(persistentRole);
    }

    @Override
    public void addActiveRole(@NotNull Session session, @NotNull String roleName) throws RBACException {
        NaturalKeys.require(roleName, "roleName");
        Role persistentRole = reviewMgr.readRole(roleName);
        SessionMgr self = (SessionMgr) AopContext.currentProxy();
        self.addActiveRole(session, persistentRole);
    }

    @Override
    public void dropActiveRole(@NotNull Session session, @NotNull Role role) throws RBACException {
        if (Objects.isNull(session)) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_SESSION_REQUIRED);
        }
        NaturalKeys.requireRoleName(role);
        List<Role> currentRoles = session.getRoles();
        if (Objects.isNull(currentRoles) || currentRoles.isEmpty()) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_SESSION_ROLE,
                    "dropActiveRole",
                    role.getRoleName(),
                    session.getSessionId()
            );
        }

        boolean removed = currentRoles.removeIf(r -> role.getRoleName().equals(r.getRoleName()));
        if (!removed) {
            throw new RBACNotFoundException(
                    RBACMessageKeys.NOT_FOUND_SESSION_ROLE,
                    "dropActiveRole",
                    role.getRoleName(),
                    session.getSessionId()
            );
        }
    }

    @Override
    public void dropActiveRole(@NotNull Session session, @NotNull String roleName) throws RBACException {
        NaturalKeys.require(roleName, "roleName");
        Role role = Role.builder().roleName(roleName).build();
        SessionMgr self = (SessionMgr) AopContext.currentProxy();
        self.dropActiveRole(session, role);
    }

    @Override
    public void deleteSession(@NotNull Session session) throws RBACException {
        if (Objects.isNull(session)) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_SESSION_REQUIRED);
        }
        if (Objects.nonNull(session.getRoles())) {
            session.getRoles().clear();
        }
        session.setUser(null);
        session.setSessionId(null);
    }
}
