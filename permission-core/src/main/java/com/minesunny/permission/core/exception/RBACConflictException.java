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
package com.minesunny.permission.core.exception;

/**
 * 业务唯一性标识冲突或重复授权状态异常（对应 HTTP 409 Conflict 语义）。
 * <p>
 * 当实体名称已存在、部门路径已被占用、角色已赋予参与者、权限已授予角色、
 * 用户已在部门中，或试图撤销不存在的关系时抛出。
 */
public class RBACConflictException extends RBACException {

    public RBACConflictException(String msg) {
        super(msg);
    }

    public RBACConflictException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public RBACConflictException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    public RBACConflictException(String messageKey, Throwable cause, Object... args) {
        super(messageKey, cause, args);
    }

    public RBACConflictException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }

    public RBACConflictException(String messageKey, Object[] args, String defaultMessage, Throwable cause) {
        super(messageKey, args, defaultMessage, cause);
    }
}
