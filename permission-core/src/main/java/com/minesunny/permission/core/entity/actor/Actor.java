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

import com.minesunny.jpa.entity.CachedIdEntity;
import com.minesunny.permission.core.entity.Role;
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

import java.util.Date;
import java.util.Set;

/**
 * RBAC 核心参与者主体实体基类（NIST / ANSI INCITS 359-2004 规范）。
 * <p>
 * <b>标准理论定义（NIST RBAC Core Specification）</b>：
 * <blockquote>
 * "A <b>User</b> is defined as a human being or an autonomous agent (machine, process, or organizational entity)
 * capable of asserting beliefs or desires, and possessing an identity within the security domain."
 * </blockquote>
 * 在本系统中，{@code Actor} 作为所有可被分配角色（Role Assignment, $UA \subseteq USERS \times ROLES$）的主体公共抽象，
 * 采用 JPA {@link InheritanceType#JOINED} 继承模型，统一纳管人类用户 {@link User} 与组织部门 {@link Department}。
 *
 * <h3>NIST RBAC 规范与实体属性/方法映射表</h3>
 * <table border="1">
 *   <caption>Actor 实体映射对照表</caption>
 *   <tr>
 *     <th>属性 / 方法</th>
 *     <th>NIST 规范术语 / 数学定义</th>
 *     <th>语义说明</th>
 *   </tr>
 *   <tr>
 *     <td>{@link #actorName}</td>
 *     <td>$u \in USERS$ (Natural Identity)</td>
 *     <td>主体的全局唯一自然标识主键，在所有参与者（用户/部门）之间唯一</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #lockDate}</td>
 *     <td>$Locked: USERS \rightarrow \{true, false\}$ (Temporal Lock)</td>
 *     <td>主体账户临时安全封禁截止时间戳；具有时间自愈性，到期后自动解除锁定</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #roles}</td>
 *     <td>$AssignedRoles(u) = \{r \in ROLES \mid (u, r) \in UA\}$</td>
 *     <td>主体直接分配拥有的角色集合，对应多对多中间表映射关系</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #isLocked(Date)}</td>
 *     <td>$isLocked(u, t) \equiv (u.lockDate \neq null \wedge u.lockDate > t)$</td>
 *     <td>判定主体在指定时间戳 $t$ 下是否处于安全封禁窗口期内</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #isLocked()}</td>
 *     <td>$isLocked(u, now)$</td>
 *     <td>判定主体在当前瞬间是否处于安全封禁锁定状态</td>
 *   </tr>
 * </table>
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "actor_type")
@Table(name = "t_actor")
@Comment("权限系统参与者(用户/部门)基类")
@SQLDelete(sql = "update t_actor set delete_at = current_timestamp where id = ? and version = ? and delete_at is null")
@SQLRestriction("delete_at is null")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Actor extends CachedIdEntity {

    /**
     * 参与者唯一标识名称（自然主键）。
     * <p>
     * <b>NIST 规范数学定义</b>：$u \in USERS$，代表安全域内主体的唯一自然标识。
     */
    @Column(name = "actor_name", unique = true, nullable = false)
    @Comment(value = "actor name, unique across all actor types (user/department)")
    private String actorName;

    /**
     * 主体账户临时安全锁定截止时间戳。
     * <p>
     * <b>NIST 规范数学定义</b>：$LockWindow(u) = [t_{trigger}, lockDate]$。
     * 为 {@code null} 表示无锁定惩罚；若当前时间早于 {@code lockDate} 则处于锁定惩罚窗口期。
     */
    @Column(name = "lock_date")
    @Comment(value = "actor lock date, null means not locked")
    private Date lockDate;

    /**
     * 当前参与者直接分配拥有的角色集合。
     * <p>
     * <b>NIST 规范数学定义</b>：$AssignedRoles(u) = \{r \in ROLES \mid (u, r) \in UA\}$，其中 $UA \subseteq USERS \times ROLES$。
     */
    @ManyToMany(mappedBy = "actors", targetEntity = Role.class)
    @Fetch(FetchMode.SELECT)
    private Set<Role> roles;

    /**
     * 判定当前主体在指定基准时间点是否处于安全锁定惩罚窗口内。
     * <p>
     * <b>NIST 形式化判定</b>：$isLocked(u, now) \iff (lockDate \neq null \wedge lockDate > now)$。
     *
     * @param now 基准时间点，若为 {@code null} 则默认取系统当前时间
     * @return 若账户处于锁定惩罚期返回 {@code true}，否则返回 {@code false}
     */
    public boolean isLocked(Date now) {
        if (lockDate == null) {
            return false;
        }
        Date currentTime = (now != null) ? now : new Date();
        return lockDate.after(currentTime);
    }

    /**
     * 判定当前主体在当前瞬间是否处于安全锁定惩罚状态。
     * <p>
     * <b>NIST 形式化判定</b>：$isLocked(u) \iff isLocked(u, System.currentTimeMillis())$。
     *
     * @return 若账户处于锁定惩罚期返回 {@code true}，否则返回 {@code false}
     */
    public boolean isLocked() {
        return isLocked(new Date());
    }
}
