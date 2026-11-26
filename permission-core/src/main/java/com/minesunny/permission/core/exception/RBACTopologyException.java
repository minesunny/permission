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
 * 组织架构拓扑约束与自环冲突异常（对应 HTTP 422 Unprocessable Entity 语义）。
 * <p>
 * 当试图删除包含子部门的父节点导致孤儿悬空、或试图将祖先部门移动到自身子孙路径之下引发自环时抛出。
 */
public class RBACTopologyException extends RBACException {

    public RBACTopologyException(String msg) {
        super(msg);
    }

    public RBACTopologyException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public RBACTopologyException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    public RBACTopologyException(String messageKey, Throwable cause, Object... args) {
        super(messageKey, cause, args);
    }

    public RBACTopologyException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }

    public RBACTopologyException(String messageKey, Object[] args, String defaultMessage, Throwable cause) {
        super(messageKey, args, defaultMessage, cause);
    }
}
