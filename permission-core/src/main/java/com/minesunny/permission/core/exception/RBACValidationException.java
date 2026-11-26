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
 * 参数校验与输入格式不合法异常（对应 HTTP 400 Bad Request 语义）。
 * <p>
 * 当必填参数缺失、自然键为空白字符、物化路径未以 '/' 开头等格式错误时抛出。
 */
public class RBACValidationException extends RBACException {

    public RBACValidationException(String msg) {
        super(msg);
    }

    public RBACValidationException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public RBACValidationException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    public RBACValidationException(String messageKey, Throwable cause, Object... args) {
        super(messageKey, cause, args);
    }

    public RBACValidationException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }

    public RBACValidationException(String messageKey, Object[] args, String defaultMessage, Throwable cause) {
        super(messageKey, args, defaultMessage, cause);
    }
}
