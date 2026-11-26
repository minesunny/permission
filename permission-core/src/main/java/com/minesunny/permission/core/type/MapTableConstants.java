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

/**
 * 关联映射表表名常量定义接口。
 */
public interface MapTableConstants {
    /** 角色与参与者映射表 */
    String ROLE_ACTOR = "t_role_actor_map";

    /** 部门与用户映射表 */
    String DEPARTMENT_USER = "t_department_user_map";

    /** 角色与权限映射表 */
    String ROLE_PERMISSION = "t_role_permission_map";

    /** 角色继承层次偏序映射表 (Role Hierarchy) */
    String ROLE_HIERARCHY = "t_role_hierarchy_map";

    /** 职责分离约束与角色关联映射表 (SoD-Role Map) */
    String SOD_ROLE = "t_sod_role_map";
}
