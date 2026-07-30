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

    /**
     * Checks whether an actor is assigned to a specific role.
     */
    boolean hasRole(@NotNull Actor actor, @NotNull Role role) throws RBACException;

    /**
     * Checks whether an actor possesses a specific permission (via any assigned role).
     */
    boolean hasPermission(@NotNull Actor actor, @NotNull Permission permission) throws RBACException;

    /**
     * Returns the list of roles managed by a given actor.
     */
    @NotNull List<Role> managedRoles(@NotNull Actor actor) throws RBACException;

    /**
     * Returns the aggregated list of permissions associated with roles managed by a given actor.
     */
    @NotNull List<Permission> managedPermissions(@NotNull Actor actor) throws RBACException;

    /**
     * Checks whether an actor has management authority over a specific role.
     */
    boolean hasManagedRole(@NotNull Actor actor, @NotNull Role role) throws RBACException;

    /**
     * Checks whether an actor has management authority over any role carrying a given permission.
     */
    boolean hasManagedPermission(@NotNull Actor actor, @NotNull Permission permission) throws RBACException;

    /**
     * @deprecated Use {@link #hasManagedRole(Actor, Role)} instead.
     */
    @Deprecated
    default boolean hasManageRole(@NotNull Actor actor, @NotNull Role role) throws RBACException {
        return hasManagedRole(actor, role);
    }

    /**
     * @deprecated Use {@link #hasManagedPermission(Actor, Permission)} instead.
     */
    @Deprecated
    default boolean hasManagePermission(@NotNull Actor actor, @NotNull Permission permission) throws RBACException {
        return hasManagedPermission(actor, permission);
    }
}
