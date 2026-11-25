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

import com.minesunny.jpa.entity.CachedIdEntity;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.perm.Permission;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalTime;
import java.util.Calendar;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * RBAC 角色实体（NIST / ANSI INCITS 359-2004 与 Temporal TRBAC 规范）。
 * <p>
 * <b>标准理论定义（NIST RBAC Core / Hierarchical / Temporal Specification）</b>：
 * <blockquote>
 * "A <b>Role</b> is a job function within the context of an organization with some associated semantics
 * regarding the authority and responsibility conferred on the user assigned to the role.<br>
 * Formally, roles form a partially ordered hierarchy $(ROLES, \succeq)$, where $r_1 \succeq r_2$ means
 * that $r_1$ inherits all permissions of $r_2$, and all users of $r_1$ are authorized to act in $r_2$."
 * </blockquote>
 *
 * <h3>NIST RBAC 规范与实体属性/方法映射表</h3>
 * <table border="1">
 *   <caption>Role 实体映射对照表</caption>
 *   <tr>
 *     <th>属性 / 方法</th>
 *     <th>NIST 规范术语 / 数学定义</th>
 *     <th>语义说明</th>
 *   </tr>
 *   <tr>
 *     <td>{@link #roleName}</td>
 *     <td>$r \in ROLES$ (Natural Identity Key)</td>
 *     <td>角色全局唯一命名标识主键，对应组织体系内的职能岗位</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #maxCardinality}</td>
 *     <td>$MaxCardinality(r) = n \in \mathbb{N}^+$ (Cardinality Constraint)</td>
 *     <td>角色基数约束：限制被分配该角色的主体总数不得超过 $n$（如 CEO 最多 1 人）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #parentRoles}</td>
 *     <td>$\{r_{parent} \in ROLES \mid r_{parent} \succeq r_{this}\}$</td>
 *     <td>角色继承层级（Role Hierarchy）：上位角色集合，自动继承本角色的所有权限</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #childRoles}</td>
 *     <td>$\{r_{child} \in ROLES \mid r_{this} \succeq r_{child}\}$</td>
 *     <td>角色继承层级（Role Hierarchy）：下位角色集合，本角色自动继承这些角色的全部权限</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #beginDateTime} / {@link #endDateTime}</td>
 *     <td>$ValidDuration(r) = [t_{begin}, t_{end}]$ (Temporal RBAC)</td>
 *     <td>角色宏观启用与过期时间窗口，超出该区间角色不可激活</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #beginLockDateTime} / {@link #endLockDateTime}</td>
 *     <td>$LockDuration(r) = [t_{lock\_start}, t_{lock\_end}]$</td>
 *     <td>角色临时熔断/封禁锁定时间窗口，处于该窗口期内角色被强制冻结</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #dayMask}</td>
 *     <td>$DayMask(r) \subseteq \{Sun, Mon, ..., Sat\}$</td>
 *     <td>星期掩码（bit0~bit6）：限制角色仅在特定星期（如仅工作日）有效</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #beginTimeOfDay} / {@link #endTimeOfDay}</td>
 *     <td>$DailyTimeWindow(r) = [time_{start}, time_{end}]$</td>
 *     <td>单日具体时间段限制（如 09:00:00 ~ 18:00:00），非业务时段自动失效</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #locked(Calendar)}</td>
 *     <td>$Locked(r, t) \iff t \in LockDuration(r)$</td>
 *     <td>判定角色在指定时间点是否处于封禁/熔断锁定状态</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #activated(Calendar)}</td>
 *     <td>$Active(r, t) \iff \neg Locked(r, t) \wedge t \in ValidDuration(r) \wedge t \in DailyTime(r)$</td>
 *     <td>综合判定角色在指定时间点是否完全处于可用激活状态</td>
 *   </tr>
 * </table>
 */
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_role")
@Getter
@Setter
@SQLDelete(sql = "update t_role set delete_at = current_timestamp where id = ? and version = ? and delete_at is null")
@SQLRestriction("delete_at is null")
public class Role extends CachedIdEntity {

    /**
     * 角色全局唯一标识名称（自然主键）。
     * <p>
     * <b>NIST 规范数学定义</b>：$r \in ROLES$。
     */
    @Column(name = "role_name", unique = true, nullable = false)
    @Comment(value = "role name, unique natural key")
    private String roleName;

    /**
     * 角色详细描述信息。
     */
    @Column(name = "description")
    @Comment(value = "detail description of this role")
    private String description;

    /**
     * 角色业务类型标识。
     */
    @Column(name = "role_type")
    @Comment(value = "role type")
    private String roleType;

    /**
     * 角色最大主体分配基数约束（NIST 第 5 章 Cardinality Constraint）。
     * <p>
     * <b>NIST 规范数学定义</b>：$|\{u \in USERS \mid (u, r) \in UA\}| \le maxCardinality$。
     * 为 {@code null} 或 0 表示无基数上限约束；若大于 0 则严格限制分配上限。
     */
    @Column(name = "max_cardinality")
    @Comment(value = "maximum number of actors that can be assigned to this role, null or 0 means unbounded")
    private Integer maxCardinality;

    /**
     * 拥有该角色的参与者集合。
     * <p>
     * <b>NIST 规范数学定义</b>：$AssignedUsers(r) = \{u \in USERS \mid (u, r) \in UA\}$。
     */
    @ManyToMany
    @Comment("actors has the role")
    @JoinTable(name = "t_role_actor_map",
            joinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "actor_id", referencedColumnName = "id"))
    @Fetch(FetchMode.SELECT)
    private Set<Actor> actors;

    /**
     * 该角色直接关联拥有的权限集合。
     * <p>
     * <b>NIST 规范数学定义</b>：$DirectPermissions(r) = \{p \in PRMS \mid (r, p) \in PA\}$。
     */
    @Comment(value = "permissions directly granted to this role")
    @ManyToMany
    @JoinTable(name = "t_role_permission_map",
            joinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id", referencedColumnName = "id"))
    @Fetch(FetchMode.SELECT)
    private Set<Permission> permissions;

    /**
     * 当前角色的直接上位/父角色集合（Role Hierarchy $r_{parent} \succeq r_{this}$）。
     * <p>
     * <b>NIST 规范数学定义</b>：$RH \subseteq ROLES \times ROLES$。上位角色自动继承当前角色的所有权限。
     */
    @ManyToMany
    @JoinTable(name = "t_role_hierarchy_map",
            joinColumns = @JoinColumn(name = "child_role_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "parent_role_id", referencedColumnName = "id"))
    @Fetch(FetchMode.SELECT)
    private Set<Role> parentRoles;

    /**
     * 当前角色的直接下位/子角色集合（当前角色继承这些子角色的所有权限）。
     */
    @ManyToMany(mappedBy = "parentRoles")
    @Fetch(FetchMode.SELECT)
    private Set<Role> childRoles;

    /**
     * 角色启用生效起始时间（包含）。为 null 表示不限制起始时间。
     * <p>
     * <b>NIST 规范数学定义</b>：$t_{begin} \in ValidDuration(r)$。
     */
    @Column(name = "begin_date_time")
    private Calendar beginDateTime;

    /**
     * 角色启用失效截止时间（包含）。为 null 表示不限制截止时间（永久有效）。
     * <p>
     * <b>NIST 规范数学定义</b>：$t_{end} \in ValidDuration(r)$。
     */
    @Column(name = "end_date_time")
    private Calendar endDateTime;

    /**
     * 角色临时锁定/封禁起始时间。
     */
    @Column(name = "begin_lock_date_time")
    private Calendar beginLockDateTime;

    /**
     * 角色临时锁定/封禁截止时间。
     */
    @Column(name = "end_lock_date_time")
    private Calendar endLockDateTime;

    /**
     * 角色每周生效星期二进制掩码（bit0~bit6 对应星期日到星期六）。
     * 为 0 表示不限制星期，全天候有效。
     */
    @Column(name = "day_mask")
    private int dayMask;

    /**
     * 角色在单日内的具体生效起始时间（包含，格式 HH:mm:ss，如 09:00:00）。
     * <p>
     * <b>NIST 规范数学定义</b>：$DailyTimeWindow(r) = [beginTimeOfDay, endTimeOfDay]$。
     */
    @Column(name = "begin_time_of_day")
    private LocalTime beginTimeOfDay;

    /**
     * 角色在单日内的具体生效截止时间（包含，格式 HH:mm:ss，如 18:00:00）。
     */
    @Column(name = "end_time_of_day")
    private LocalTime endTimeOfDay;

    /**
     * 判定角色在给定时间是否处于封禁/锁定窗口内。
     * <p>
     * <b>NIST 形式化判定</b>：$Locked(r, now) \iff now \in [beginLockDateTime, endLockDateTime]$。
     *
     * @param now 基准时间点
     * @return 若处于锁定窗口内返回 {@code true}，否则返回 {@code false}
     */
    public boolean locked(Calendar now) {
        now = Optional.ofNullable(now).orElse(Calendar.getInstance());
        if (Objects.isNull(beginLockDateTime)) {
            return false;
        }
        if (Objects.isNull(endLockDateTime)) {
            return !now.before(beginLockDateTime);
        }
        return !now.before(beginLockDateTime) && !now.after(endLockDateTime);
    }

    /**
     * 判定角色在当前瞬间是否处于封禁/锁定窗口内。
     *
     * @return 若处于锁定窗口内返回 {@code true}，否则返回 {@code false}
     */
    public boolean locked() {
        return locked(Calendar.getInstance());
    }

    /**
     * 判定角色在指定时间点是否处于激活可用状态。
     * <p>
     * <b>NIST 形式化判定</b>：
     * $Active(r, now) \iff \neg Locked(r, now) \wedge now \in ValidDuration(r) \wedge now \in DailyTime(r)$。
     *
     * @param now 基准时间点
     * @return 若角色可用激活返回 {@code true}，否则返回 {@code false}
     */
    public boolean activated(Calendar now) {
        now = Optional.ofNullable(now).orElse(Calendar.getInstance());
        if (locked(now)) {
            return false;
        }
        if (Objects.nonNull(beginDateTime) && now.before(beginDateTime)) {
            return false;
        }
        if (Objects.nonNull(endDateTime) && now.after(endDateTime)) {
            return false;
        }
        if (dayMask > 0) {
            int dayOfWeek = now.get(Calendar.DAY_OF_WEEK); // 1 (Sunday) ~ 7 (Saturday)
            int maskBit = 1 << (dayOfWeek - 1);
            if ((dayMask & maskBit) == 0) {
                return false;
            }
        }
        // 每日具体时间窗口判定 (HH:mm:ss)
        if (Objects.nonNull(beginTimeOfDay) || Objects.nonNull(endTimeOfDay)) {
            LocalTime currentLocalTime = LocalTime.of(
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    now.get(Calendar.SECOND)
            );
            if (Objects.nonNull(beginTimeOfDay) && currentLocalTime.isBefore(beginTimeOfDay)) {
                return false;
            }
            if (Objects.nonNull(endTimeOfDay) && currentLocalTime.isAfter(endTimeOfDay)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判定角色在当前瞬间是否处于激活可用状态。
     *
     * @return 若角色可用激活返回 {@code true}，否则返回 {@code false}
     */
    public boolean activated() {
        return activated(Calendar.getInstance());
    }

    /**
     * 兼容老版本属性拼写的 Getter 方法。
     *
     * @return 角色截止时间
     */
    public Calendar getEndDateTIme() {
        return this.endDateTime;
    }

    /**
     * 兼容老版本属性拼写的 Setter 方法。
     *
     * @param endDateTIme 角色截止时间
     */
    public void setEndDateTIme(Calendar endDateTIme) {
        this.endDateTime = endDateTIme;
    }
}
