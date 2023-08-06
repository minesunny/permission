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
package com.minesunny.permission.core.type;

import com.minesunny.jpa.entity.BaseEntity;
import com.minesunny.permission.core.entity.actor.OrgUnit;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Post;
import com.minesunny.permission.core.entity.perm.Operation;
import com.minesunny.permission.core.entity.perm.Permission;

public interface RBACEntityConstants {

    Role NOT_EXIST_ROLE = Role.builder().id(-1L).build();

    Role ROLE = new Role();
    User NOT_EXIST_USER = User.builder().id(-1L).build();
    User USER = new User();

    OrgUnit NOT_EXIST_ORG_UNIT = OrgUnit.builder().id(-1L).build();

    OrgUnit ORG_UNIT = new OrgUnit();


    Permission NOT_EXIST_PERMISSION = Permission.builder().id(-1L).build();


    Permission PERMISSION = new Permission();


    Post NOT_EXIST_POST = Post.builder().id(-1L).build();

    Post POST = new Post();

    BaseEntity OPERATION = Operation.builder().build();
}
