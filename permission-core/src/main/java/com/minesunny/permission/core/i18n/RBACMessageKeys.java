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
package com.minesunny.permission.core.i18n;

/**
 * RBAC 国际化消息键常量定义接口。
 */
public interface RBACMessageKeys {

    // -------------------------------------------------------------------------
    // Validation (400)
    // -------------------------------------------------------------------------
    String VALIDATION_REQUIRED = "rbac.validation.required";
    String VALIDATION_ACTOR_NAME_REQUIRED = "rbac.validation.actor-name-required";
    String VALIDATION_USERNAME_REQUIRED = "rbac.validation.username-required";
    String VALIDATION_ROLE_NAME_REQUIRED = "rbac.validation.role-name-required";
    String VALIDATION_PERM_NAME_REQUIRED = "rbac.validation.perm-name-required";
    String VALIDATION_DEPT_NAME_REQUIRED = "rbac.validation.dept-name-required";
    String VALIDATION_PATH_REQUIRED = "rbac.validation.path-required";
    String VALIDATION_PATH_EMPTY = "rbac.validation.path-empty";
    String VALIDATION_PATH_START_SLASH = "rbac.validation.path-start-slash";
    String VALIDATION_DEPT_IDENTIFIER_REQUIRED = "rbac.validation.dept-identifier-required";
    String VALIDATION_USER_IDENTIFIER_REQUIRED = "rbac.validation.user-identifier-required";
    String VALIDATION_SESSION_REQUIRED = "rbac.validation.session-required";
    String VALIDATION_SEARCH_CONDITION_REQUIRED = "rbac.validation.search-condition-required";

    // -------------------------------------------------------------------------
    // Not Found (404)
    // -------------------------------------------------------------------------
    String NOT_FOUND_ACTOR = "rbac.not-found.actor";
    String NOT_FOUND_USER = "rbac.not-found.user";
    String NOT_FOUND_ROLE = "rbac.not-found.role";
    String NOT_FOUND_PERMISSION = "rbac.not-found.permission";
    String NOT_FOUND_DEPARTMENT = "rbac.not-found.department";
    String NOT_FOUND_PARENT_DEPARTMENT = "rbac.not-found.parent-department";
    String NOT_FOUND_SESSION_ROLE = "rbac.not-found.session-role";
    String NOT_FOUND_SSD_SET = "rbac.not-found.ssd-set";
    String NOT_FOUND_DSD_SET = "rbac.not-found.dsd-set";
    String NOT_FOUND_SOD_ROLE = "rbac.not-found.sod-role";

    // -------------------------------------------------------------------------
    // Conflict (409)
    // -------------------------------------------------------------------------
    String CONFLICT_ACTOR_EXISTS = "rbac.conflict.actor-exists";
    String CONFLICT_ACTOR_ARCHIVED = "rbac.conflict.actor-archived";
    String CONFLICT_USER_EXISTS = "rbac.conflict.user-exists";
    String CONFLICT_USER_ARCHIVED = "rbac.conflict.user-archived";
    String CONFLICT_ROLE_EXISTS = "rbac.conflict.role-exists";
    String CONFLICT_ROLE_ARCHIVED = "rbac.conflict.role-archived";
    String CONFLICT_PERMISSION_EXISTS = "rbac.conflict.permission-exists";
    String CONFLICT_PERMISSION_ARCHIVED = "rbac.conflict.permission-archived";
    String CONFLICT_DEPARTMENT_EXISTS = "rbac.conflict.department-exists";
    String CONFLICT_DEPARTMENT_PATH_EXISTS = "rbac.conflict.department-path-exists";
    String CONFLICT_DEPARTMENT_ARCHIVED = "rbac.conflict.department-archived";
    String CONFLICT_ROLE_ASSIGNED = "rbac.conflict.role-assigned";
    String CONFLICT_ROLE_NOT_ASSIGNED = "rbac.conflict.role-not-assigned";
    String CONFLICT_ROLE_NOT_ASSIGNED_TO_USER = "rbac.conflict.role-not-assigned-to-user";
    String CONFLICT_ROLE_TEMPORAL_INACTIVE = "rbac.conflict.role-temporal-inactive";
    String CONFLICT_ROLE_MAX_CARDINALITY = "rbac.conflict.role-max-cardinality";
    String CONFLICT_ROLE_INHERITANCE_EXISTS = "rbac.conflict.role-inheritance-exists";
    String CONFLICT_ROLE_INHERITANCE_NOT_EXISTS = "rbac.conflict.role-inheritance-not-exists";
    String CONFLICT_SESSION_ROLE_ALREADY_ACTIVE = "rbac.conflict.session-role-already-active";
    String CONFLICT_PERMISSION_GRANTED = "rbac.conflict.permission-granted";
    String CONFLICT_PERMISSION_NOT_GRANTED = "rbac.conflict.permission-not-granted";
    String CONFLICT_USER_IN_DEPARTMENT = "rbac.conflict.user-in-department";
    String CONFLICT_USER_NOT_IN_DEPARTMENT = "rbac.conflict.user-not-in-department";
    String CONFLICT_DESCENDANT_PATH_EXISTS = "rbac.conflict.descendant-path-exists";
    String CONFLICT_SSD_SET_EXISTS = "rbac.conflict.ssd-set-exists";
    String CONFLICT_DSD_SET_EXISTS = "rbac.conflict.dsd-set-exists";
    String CONFLICT_SSD_VIOLATION = "rbac.conflict.ssd-violation";
    String CONFLICT_DSD_VIOLATION = "rbac.conflict.dsd-violation";
    String CONFLICT_SOD_ROLE_ALREADY_MEMBER = "rbac.conflict.sod-role-already-member";

    // -------------------------------------------------------------------------
    // Topology (422)
    // -------------------------------------------------------------------------
    String TOPOLOGY_CHILD_DEPARTMENTS = "rbac.topology.child-departments";
    String TOPOLOGY_DESCENDANT_MOVEMENT = "rbac.topology.descendant-movement";
    String TOPOLOGY_ROLE_INHERITANCE_CYCLE = "rbac.topology.role-inheritance-cycle";
}
