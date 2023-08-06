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
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;


@Transactional
public interface AdminMgr {

    /**
     * This command creates a new RBAC user. The command is valid only if the new user is
     * not already a member of the USERS data set. The USER data set is updated. The new user
     * does not own any session at the time of its creation.
     *
     * @param user
     * @return
     */
    @NotNull
    User addUser(@NotNull User user) throws ServiceException;

    @NotNull
    User updateUser(@NotNull User user) throws ServiceException;


    /**
     * This command deletes an existing user from the RBAC database. The command is valid
     * if and only if the user to be deleted is a member of the USERS data set. The USERS and
     * UA data sets and the assigned_users function are updated. It is an implementation
     * decision how to proceed with the sessions owned by the user to be deleted. The RBAC
     * system could wait for such a session to terminate normally, or it could force its
     * termination.
     *
     * @param user
     */
    void deleteUser(@NotNull User user) throws ServiceException;


    /**
     * This command creates a new role. The command is valid if and only if the new role is not
     * already a member of the ROLES data set. The ROLES data set and the functions
     * assigned_users and assigned_permissions are updated. Initially, no user or permission is
     * assigned to the new role.
     *
     * @param role
     * @return
     */
    @NotNull
    Role addRole(@NotNull Role role) throws ServiceException;

    @NotNull
    Role updateRole(@NotNull Role role) throws ServiceException;


    /**
     * This command deletes an existing role from the RBAC database. The command is valid
     * if and only if the role to be deleted is a member of the ROLES data set. It is an
     * implementation decision how to proceed with the sessions in which the role to be deleted
     * is active. The RBAC system could wait for such a session to terminate normally, it could
     * force the termination of that session, or it could delete the role from that session while
     * allowing the session to continue.
     *
     * @param role
     */
    void deleteRole(@NotNull Role role) throws ServiceException;


    /**
     * This command assigns a user to a role. The command is valid if and only if the user is a
     * member of the USERS data set, the role is a member of the ROLES data set, and the user
     * is not already assigned to the role. The data set UA and the function assigned_users are
     * updated to reflect the assignment.
     *
     * @param user
     * @param role
     */
    void assignUser(@NotNull User user, @NotNull Role role) throws ServiceException;

    /**
     * This command deletes the assignment of the user user to the role role. The command is
     * valid if and only if the user is a member of the USERS data set, the role is a member of
     * the ROLES data set, and the user is assigned to the role.
     * It is an implementation decision how to proceed with the sessions in which the session
     * user is user and one of his/her active roles is role. The RBAC system could wait for such
     * a session to terminate normally, could force its termination, or could inactivate the role.
     *
     * @param user
     * @param role
     */
    void deassignUser(@NotNull User user, @NotNull Role role) throws ServiceException;

    @NotNull
    Permission addPermission(@NotNull Permission permission) throws ServiceException;

    @NotNull
    Permission updatePermission(@NotNull Permission permission) throws ServiceException;

    void deletePermission(@NotNull Permission permission) throws ServiceException;


    /**
     * This command grants a role the permission to perform an operation on an object to a role.
     * The command may be implemented as granting permissions to a group corresponding to
     * that role, i.e., setting the access control list of the object involved.
     * The command is valid if and only if the pair (operation, object) represents a permission,
     * and the role is a member of the ROLES data set.
     *
     * @param permission
     * @param role
     */
    void grantPermission(@NotNull Permission permission, @NotNull Role role) throws ServiceException;


    /**
     * @param permission
     * @param role
     */
    void revokePermission(@NotNull Permission permission, @NotNull Role role) throws ServiceException;

}
