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

import com.minesunny.jpa.ServiceException;
import com.minesunny.permission.core.entity.Session;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.perm.Operation;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.entity.perm.Resource;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

@Transactional
public interface ReviewMgr {
    Optional<User> readUser(@NotNull User user) throws ServiceException;


    List<User> readUsers(@NotNull User user) throws ServiceException;


    Optional<Role> readRole(@NotNull Role role) throws ServiceException;

    @NotNull
    List<Role> readRoles(@NotNull Role role) throws ServiceException;

    Optional<Permission> readPermission(@NotNull Permission permission) throws ServiceException;

    @NotNull
    List<Permission> readPermissions(@NotNull Permission permission) throws ServiceException;

    /**
     * This function returns the List of permissions (op, obj) granted to a given role. The function
     * is valid if and only if the role is a member of the ROLES data List
     *
     * @param role
     * @return
     * @throws ServiceException
     */
    @NotNull
    List<Permission> rolePermissions(@NotNull Role role) throws ServiceException;


    /**
     * This function returns the permissions a given user gets through his/her assigned roles.
     * The function is valid if and only if the user is a member of the USERS data List.
     *
     * @param user
     * @return
     * @throws ServiceException
     */
    @NotNull
    List<Permission> userPermissions(@NotNull User user) throws ServiceException;


    /**
     * This function returns the active roles associated with a session. The function is valid if
     * and only if the session identifier is a member of the SESSIONS data List.
     *
     * @param session
     * @return
     * @throws ServiceException
     */
    @NotNull List<Role> sessionRoles(@NotNull Session session) throws ServiceException;


    /**
     * This function returns the permissions of the session session, i.e., the permissions assigned
     * to its active roles. The function is valid if and only if the session identifier is a member of
     * the SESSIONS data List.
     *
     * @param session
     * @return
     * @throws ServiceException
     */
    @NotNull List<Permission> sessionPermissions(@NotNull Session session) throws ServiceException;


    /**
     * This function returns the List of operations a given role is permitted to perform on a given
     * object. The function is valid only if the role is a member of the ROLES data List, and the
     * object is a member of the OBJS data List.
     *
     * @param role
     * @param resource
     * @return
     * @throws ServiceException
     */
    @NotNull List<Operation> roleOperationsOnResource(@NotNull Role role, @NotNull Resource resource) throws ServiceException;


    /**
     * This function returns the List of operations a given user is permitted to perform on a given
     * object, obtained either directly or through his/her assigned roles. The function is valid if
     * and only if the user is a member of the USERS data List and the object is a member of the
     * OBJS data List. T
     *
     * @param user
     * @param resource
     * @return
     * @throws ServiceException
     */
    @NotNull List<Operation> userOperationsOnResource(@NotNull User user, @NotNull Resource resource) throws ServiceException;


    /**
     * This function returns the List of users assigned to a given role. The function is valid if and
     * only if the role is a member of the ROLES data List.
     *
     * @param role
     * @return
     * @throws ServiceException
     */

    @NotNull List<User> assignedUsers(@NotNull Role role) throws ServiceException;


    /**
     * This function returns the List of roles assigned to a given user. The function is valid if and
     * only if the user is a member of the USERS data List.
     *
     * @param user
     * @return
     * @throws ServiceException
     */
    @NotNull List<Role> assignedRoles(@NotNull User user) throws ServiceException;


    @NotNull List<Role> permissionRoles(@NotNull Permission permission) throws ServiceException;

    @NotNull List<User> permissionUsers(@NotNull Permission permission) throws ServiceException;
}
