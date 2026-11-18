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

import com.minesunny.jpa.exception.ServiceException;

/**
 * Root exception for the RBAC permission module.
 *
 * <p>All permission-related exceptions ({@link SecurityException},
 * {@link ValidationException}, {@link AuthorizationException}, …) extend this class,
 * allowing callers to catch a single type for any RBAC error while still being
 * compatible with the underlying {@link ServiceException} hierarchy.
 *
 * <p>Exception hierarchy:
 * <pre>
 * ServiceException (jpa library)
 *   └── RBACException
 *         ├── SecurityException
 *         │     └── ValidationException
 *         └── AuthorizationException  (TODO: migrate from fortress hierarchy)
 * </pre>
 */
public class RBACException extends ServiceException {

    public RBACException(String msg) {
        super(msg);
    }

    public RBACException(String msg, Exception cause) {
        super(msg, cause);
    }
}
