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
package com.minesunny.permission.core.entity;

import com.minesunny.permission.core.entity.actor.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * RBAC 会话实体（ANSI-INCITS 359-2004 / NIST RBAC 规范）。
 * <p>
 * 会话是一个活动主体的执行态上下文（Active Execution Context）。
 * 每个会话严格绑定唯一一个用户 {@link #user}，并在内存中维持当前会话内激活的角色子集 {@link #roles}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    /**
     * 会话唯一标识 ID。
     */
    private String sessionId;

    /**
     * 会话绑定的唯一用户（$session\_users(s) \rightarrow u$）。
     */
    private User user;

    /**
     * 当前会话已激活的角色集合（$session\_roles(s) \subseteq assigned\_roles(u)$）。
     */
    @Builder.Default
    private List<Role> roles = new ArrayList<>();
}
