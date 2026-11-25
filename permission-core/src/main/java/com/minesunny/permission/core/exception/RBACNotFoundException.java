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
 * 目标实体或资源不存在异常（对应 HTTP 404 Not Found 语义）。
 * <p>
 * 当查询目标参与者、角色、权限、部门或父部门路径在系统中不存在时抛出。
 */
public class RBACNotFoundException extends RBACException {

    public RBACNotFoundException(String msg) {
        super(msg);
    }

    public RBACNotFoundException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public RBACNotFoundException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    public RBACNotFoundException(String messageKey, Throwable cause, Object... args) {
        super(messageKey, cause, args);
    }

    public RBACNotFoundException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }

    public RBACNotFoundException(String messageKey, Object[] args, String defaultMessage, Throwable cause) {
        super(messageKey, args, defaultMessage, cause);
    }
}
