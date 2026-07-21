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

import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.permission.core.entity.actor.OrgUnit;
import com.minesunny.permission.core.entity.actor.Post;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.sdt.OrgSdtNode;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

@Transactional
public interface OrgUnitMgr {
    @NotNull
    Post addPost(@NotNull Post post) throws ServiceException;

    @NotNull
    Post updatePost(@NotNull String postName, @NotNull Post post) throws ServiceException;

    void deletePost(@NotNull Post post) throws ServiceException;

    Optional<Post> readPost(@NotNull Post post) throws ServiceException;

    @NotNull
    List<Post> readPosts(@NotNull Post post) throws ServiceException;


    void bindPost(@NotNull Post post, @NotNull User user) throws ServiceException;

    void unbindPost(@NotNull Post post, @NotNull User user) throws ServiceException;

    @NotNull
    OrgUnit addOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException;

    @NotNull
    OrgUnit updateOrgUnit(@NotNull String orgName, @NotNull OrgUnit orgUnit) throws ServiceException;

    void deleteOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException;

    Optional<OrgUnit> readOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException;

    @NotNull
    List<OrgUnit> readOrgUnits(@NotNull OrgUnit orgUnit) throws ServiceException;

    void bindOrgUnit(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException;

    void unbindOrgUnit(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException;

    void bindOrgUnitNode(@NotNull OrgSdtNode childOrgNode, @NotNull OrgSdtNode parentOrgNode) throws ServiceException;

    void unbindOrgUnitNode(@NotNull OrgSdtNode childOrgNode) throws ServiceException;

    void bindManagerUser(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException;

    void unbindManagerUser(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException;

}
