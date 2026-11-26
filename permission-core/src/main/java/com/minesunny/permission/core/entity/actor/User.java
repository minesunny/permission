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
package com.minesunny.permission.core.entity.actor;

import com.minesunny.permission.core.type.GenderType;
import com.minesunny.permission.core.type.RegisterType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

import java.util.Date;

/**
 * RBAC 具体人类用户实体（NIST / ANSI INCITS 359-2004 规范）。
 * <p>
 * <b>标准理论定义（NIST RBAC Core Specification）</b>：
 * <blockquote>
 * "A <b>User</b> represents an individual human being with authentication credentials,
 * participating in role assignments and executing active sessions."
 * </blockquote>
 * 继承自主体基类 {@link Actor}，共享账户安全锁定状态及判定逻辑 {@link Actor#isLocked()}，
 * 并支持行政生命周期开关 {@link #isEnabled()}。
 *
 * <h3>NIST RBAC 规范与实体属性/方法映射表</h3>
 * <table border="1">
 *   <caption>User 实体映射对照表</caption>
 *   <tr>
 *     <th>属性 / 方法</th>
 *     <th>NIST 规范术语 / 数学定义</th>
 *     <th>语义说明</th>
 *   </tr>
 *   <tr>
 *     <td>{@link #username}</td>
 *     <td>$u_{human} \in USERS$ (Login Credential Key)</td>
 *     <td>具体人类用户的唯一登录名（自然主键），作为身份认证主体凭据唯一索引</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #enabled}</td>
 *     <td>$Status: USERS \rightarrow \{Active, Disabled\}$</td>
 *     <td>主体账户行政启停生命周期状态，由管理员主动管控，无自动过期机制</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #isEnabled()}</td>
 *     <td>$isEnabled(u) \equiv (u.enabled == true)$</td>
 *     <td>判定当前账户是否处于行政启用状态</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #isValid(Date)}</td>
 *     <td>$Valid(u, t) \iff isEnabled(u) \wedge \neg isLocked(u, t)$</td>
 *     <td>综合判定用户账户在指定基准时间点是否完全合法可用（既启用且未锁定）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #isValid()}</td>
 *     <td>$Valid(u, now)$</td>
 *     <td>综合判定用户账户在当前瞬间是否完全合法可用</td>
 *   </tr>
 * </table>
 */
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_user")
@Getter
@Setter
@DiscriminatorValue("user")
public class User extends Actor {

    /**
     * 用户唯一登录名（自然主键）。
     * <p>
     * <b>NIST 规范数学定义</b>：$u \in USERS$ (Human Identity Key)。
     */
    @Column(name = "username", unique = true, nullable = false)
    @Comment(value = "user login name, unique")
    private String username;

    /**
     * 用户账户启用/停用生命周期状态（行政控制状态）。
     * <p>
     * <b>NIST 规范数学定义</b>：$LifecycleStatus(u) \in \{Active, Disabled\}$。
     * {@code true} 表示正常启用；{@code false} 表示已被管理员显式禁用。默认为 {@code true}。
     */
    @Builder.Default
    @Column(name = "enabled", nullable = false)
    @Comment(value = "whether the user account is enabled (administrative switch)")
    private Boolean enabled = true;

    /**
     * 用户详细描述信息。
     */
    @Column(name = "description")
    @Comment(value = "description")
    private String description;

    /**
     * 绑定的电子邮箱地址。
     */
    @Column(name = "email")
    @Comment(value = "user email")
    private String email;

    /**
     * 用户性别类型。
     */
    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    @Comment(value = "user gender")
    private GenderType gender;

    /**
     * 用户生日信息。
     */
    @Column(name = "birthday")
    @Comment(value = "user birthday")
    private String birthday;

    /**
     * 用户注册来源 IP 地址。
     */
    @Column(name = "register_ip")
    @Comment(value = "user register ip")
    private String registerIp;

    /**
     * 用户注册途径类型。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "register_type")
    private RegisterType registerType;

    /**
     * 判定用户账户当前是否处于行政启用状态。
     * <p>
     * <b>NIST 形式化判定</b>：$isEnabled(u) \iff (u.enabled == true)$。
     *
     * @return 若账户启用返回 {@code true}，若为 {@code null} 或 {@code false} 则返回 {@code false}
     */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.enabled);
    }

    /**
     * 综合判定用户账户在指定时间点是否完全有效可用（既被启用、又未处于锁定惩罚窗口）。
     * <p>
     * <b>NIST 形式化判定</b>：$Valid(u, now) \iff isEnabled(u) \wedge \neg isLocked(u, now)$。
     *
     * @param now 基准时间点
     * @return 若账户完全有效可用返回 {@code true}，否则返回 {@code false}
     */
    public boolean isValid(Date now) {
        return isEnabled() && !isLocked(now);
    }

    /**
     * 综合判定用户账户在当前瞬间是否完全有效可用（既被启用、又未处于锁定惩罚窗口）。
     * <p>
     * <b>NIST 形式化判定</b>：$Valid(u) \iff isEnabled(u) \wedge \neg isLocked(u)$。
     *
     * @return 若账户完全有效可用返回 {@code true}，否则返回 {@code false}
     */
    public boolean isValid() {
        return isValid(new Date());
    }
}
