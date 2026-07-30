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
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACException;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;


@Transactional
public interface AdminMgr {

    /**
     * This command creates a new RBAC actor (User / Post / OrgUnit). The command is valid
     * only if the new actor is not already a member of the ACTORS data set (identified by
     * actorName). The ACTORS data set is updated.
     *
     * @param actor actor to add (any subtype of Actor)
     * @param <T>   concrete Actor subtype
     * @return the persisted actor with ID assigned
     */
    @NotNull
    <T extends Actor> T addActor(@NotNull T actor) throws RBACException;

    /**
     * This command updates an existing actor identified by {@code actorName}. Only scalar
     * fields are updated; relationship collections (roles, post, orgUnits, etc.) are excluded.
     *
     * @param actorName current actorName of the actor to update
     * @param actor     object carrying the new field values
     * @param <T>       concrete Actor subtype
     * @return the updated, persisted actor
     */
    @NotNull
    <T extends Actor> T updateActor(@NotNull String actorName, @NotNull T actor) throws RBACException;

    /**
     * This command deletes an existing actor from the RBAC database. All role assignments
     * of the actor are removed before deletion.
     *
     * @param actor actor to delete (must have either id or actorName set)
     */
    void deleteActor(@NotNull Actor actor) throws RBACException;


    /**
     * This command creates a new role. The command is valid if and only if the new role is not
     * already a member of the ROLES data set.
     */
    @NotNull
    Role addRole(@NotNull Role role) throws RBACException;

    @NotNull
    Role updateRole(@NotNull String roleName, @NotNull Role role) throws RBACException;

    /**
     * This command deletes an existing role from the RBAC database.
     */
    void deleteRole(@NotNull Role role) throws RBACException;


    /**
     * This command assigns an actor to a role.
     */
    void assignActor(@NotNull Actor actor, @NotNull Role role) throws RBACException;

    /**
     * This command removes the assignment of an actor from a role.
     */
    void deassignActor(@NotNull Actor actor, @NotNull Role role) throws RBACException;

    @NotNull
    Permission addPermission(@NotNull Permission permission) throws RBACException;

    @NotNull
    Permission updatePermission(@NotNull String permName, @NotNull Permission permission) throws RBACException;

    void deletePermission(@NotNull Permission permission) throws RBACException;

    /**
     * This command grants a role the permission to perform an operation on an object.
     */
    void grantPermission(@NotNull Permission permission, @NotNull Role role) throws RBACException;

    /**
     * This command revokes a permission from a role.
     */
    void revokePermission(@NotNull Permission permission, @NotNull Role role) throws RBACException;

}
