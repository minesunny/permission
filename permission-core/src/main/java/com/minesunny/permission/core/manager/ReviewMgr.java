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
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACException;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

@Transactional
public interface ReviewMgr {

    /**
     * Returns a single actor matched by actorName (natural key).
     *
     * @param actor actor carrying the actorName to look up
     * @return Optional containing the actor if found
     */
    Optional<Actor> readActor(@NotNull Actor actor) throws RBACException;

    /**
     * Returns a list of actors matching the search criteria carried in {@code actor}.
     *
     * @param actor actor carrying search conditions (must have at least one condition set)
     * @return list of matching actors
     */
    List<Actor> readActors(@NotNull Actor actor) throws RBACException;

    Optional<Role> readRole(@NotNull Role role) throws RBACException;

    @NotNull
    List<Role> readRoles(@NotNull Role role) throws RBACException;

    Optional<Permission> readPermission(@NotNull Permission permission) throws RBACException;

    @NotNull
    List<Permission> readPermissions(@NotNull Permission permission) throws RBACException;

    /**
     * Returns the list of permissions granted to a given role.
     */
    @NotNull
    List<Permission> rolePermissions(@NotNull Role role) throws RBACException;

    /**
     * Returns the permissions an actor obtains through its assigned roles.
     */
    @NotNull
    List<Permission> actorPermissions(@NotNull Actor actor) throws RBACException;

    /**
     * Returns the active roles associated with a session.
     */
    @NotNull List<Role> sessionRoles(@NotNull Session session) throws RBACException;

    /**
     * Returns the permissions of a session (union of all active role permissions).
     */
    @NotNull List<Permission> sessionPermissions(@NotNull Session session) throws RBACException;

    /**
     * Returns the list of actors assigned to a given role.
     */
    @NotNull List<Actor> assignedActors(@NotNull Role role) throws RBACException;

    /**
     * Returns the list of roles assigned to a given actor.
     */
    @NotNull List<Role> assignedRoles(@NotNull Actor actor) throws RBACException;

    @NotNull List<Role> permissionRoles(@NotNull Permission permission) throws RBACException;

    @NotNull List<Actor> permissionActors(@NotNull Permission permission) throws RBACException;
}
