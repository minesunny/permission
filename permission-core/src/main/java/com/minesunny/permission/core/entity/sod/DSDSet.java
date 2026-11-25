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
package com.minesunny.permission.core.entity.sod;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 动态职责分离（Dynamic Separation of Duty, DSD）约束实体（NIST / ANSI INCITS 359-2004 规范）。
 * <p>
 * <b>标准理论定义（NIST RBAC DSD Specification）</b>：
 * <blockquote>
 * "In a DSD relation $(DSD\_Set\_Name, role\_set, n)$, no user is permitted to activate $n$ or more roles from $role\_set$ in the same session.<br>
 * Formally: $\forall s \in SESSIONS, |SessionRoles(s) \cap role\_set| &lt; n$."
 * </blockquote>
 * 在会话创建（{@code CreateSession}）或动态激活角色（{@code AddActiveRole}）时强制拦截违规激活。
 */
@Entity
@SuperBuilder
@NoArgsConstructor
@Table(name = "t_dsd_set")
@Getter
@Setter
@DiscriminatorValue("DSD")
public class DSDSet extends SoDSet {
}
