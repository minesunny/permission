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
 * 参数校验异常（历史兼容类）。
 *
 * @deprecated 建议优先使用语义更明确的现代异常：
 * <ul>
 *   <li>{@link RBACValidationException} - 参数与格式非法</li>
 *   <li>{@link RBACNotFoundException} - 实体或资源不存在</li>
 *   <li>{@link RBACConflictException} - 业务标识重复或关系冲突</li>
 *   <li>{@link RBACTopologyException} - 组织树拓扑与自环异常</li>
 * </ul>
 */
@Deprecated
public class ValidationException extends RBACValidationException {

    public ValidationException(String msg) {
        super(msg);
    }

    public ValidationException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public ValidationException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    public ValidationException(String messageKey, Throwable cause, Object... args) {
        super(messageKey, cause, args);
    }

    public ValidationException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }

    public ValidationException(String messageKey, Object[] args, String defaultMessage, Throwable cause) {
        super(messageKey, args, defaultMessage, cause);
    }
}
