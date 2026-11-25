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

import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;

public interface RBACEntityConstants {

    Role NOT_EXIST_ROLE = Role.builder().roleName("notExistRole").build();

    Role ROLE = new Role();
    User NOT_EXIST_USER = User.builder().actorName("notExistUser").username("notExistUser").build();
    User USER = new User();

    /** Empty Actor (no actorName) used to verify that readActor() throws on missing natural key. */
    Actor ACTOR = new User();

    Department NOT_EXIST_DEPARTMENT = Department.builder().actorName("notExistDept").deptName("notExistDept").build();

    Department DEPARTMENT = new Department();

    Permission NOT_EXIST_PERMISSION = Permission.builder().permName("notExistPerm").build();

    Permission PERMISSION = new Permission();
}
