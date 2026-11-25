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
package com.minesunny.permission.core.manager;

import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.Session;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.exception.RBACException;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * RBAC 会话管理业务接口（ANSI/NIST RBAC Core System Functions）。
 * <p>
 * 负责会话的完整生命周期管理与动态角色激活控制：
 * <ul>
 *     <li>{@code CreateSession(user, session, ars)}：创建会话并激活指定初始角色子集。</li>
 *     <li>{@code AddActiveRole(user, session, role)}：动态在当前会话中激活新角色。</li>
 *     <li>{@code DropActiveRole(user, session, role)}：动态从当前会话中去激活角色（最小特权原则）。</li>
 *     <li>{@code DeleteSession(user, session)}：销毁会话。</li>
 * </ul>
 */
@Transactional
public interface SessionMgr {

    /**
     * 为指定用户创建会话并激活指定的初始角色列表。
     *
     * @param user        绑定的用户实体（需包含 username 或 id）
     * @param activeRoles 初始激活的角色列表（可为空列表）
     * @return 已创建并初始化的会话对象
     * @throws RBACException 当用户不存在、角色未授予该用户或角色当前不可用时抛出
     */
    @NotNull
    Session createSession(@NotNull User user, @NotNull List<Role> activeRoles) throws RBACException;

    /**
     * 根据用户名创建会话并激活指定角色名称列表。
     *
     * @param username        用户登录名（自然主键）
     * @param activeRoleNames 初始激活的角色名称列表
     * @return 已创建并初始化的会话对象
     * @throws RBACException 当用户不存在、角色未授予该用户或角色当前不可用时抛出
     */
    @NotNull
    Session createSession(@NotNull String username, @NotNull List<String> activeRoleNames) throws RBACException;

    /**
     * 在指定会话中动态激活一个新角色。
     *
     * @param session 当前活动会话
     * @param role    待激活的角色实体（需包含 roleName 或 id）
     * @throws RBACException 当角色未授予该会话用户、角色已在会话中激活、或角色当前处于锁定/过期状态时抛出
     */
    void addActiveRole(@NotNull Session session, @NotNull Role role) throws RBACException;

    /**
     * 在指定会话中根据角色名称动态激活一个新角色。
     *
     * @param session  当前活动会话
     * @param roleName 待激活的角色名称（自然主键）
     * @throws RBACException 当角色未授予该会话用户、角色已在会话中激活、或角色当前处于锁定/过期状态时抛出
     */
    void addActiveRole(@NotNull Session session, @NotNull String roleName) throws RBACException;

    /**
     * 在指定会话中注销/停用已激活的角色（落实最小特权原则）。
     *
     * @param session 当前活动会话
     * @param role    待去激活的角色实体
     * @throws RBACException 当角色未在当前会话中激活时抛出
     */
    void dropActiveRole(@NotNull Session session, @NotNull Role role) throws RBACException;

    /**
     * 在指定会话中根据角色名称注销/停用已激活的角色。
     *
     * @param session  当前活动会话
     * @param roleName 待去激活的角色名称
     * @throws RBACException 当角色未在当前会话中激活时抛出
     */
    void dropActiveRole(@NotNull Session session, @NotNull String roleName) throws RBACException;

    /**
     * 显式销毁并清理指定会话。
     *
     * @param session 待销毁的会话
     * @throws RBACException 当会话参数非法时抛出
     */
    void deleteSession(@NotNull Session session) throws RBACException;
}
