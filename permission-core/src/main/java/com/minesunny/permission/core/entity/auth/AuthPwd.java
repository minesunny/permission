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
package com.minesunny.permission.core.entity.auth;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_auth_pwd")
@SQLDelete(sql = "update t_auth_pwd set delete_at = current_timestamp where delete_at is null")
@Where(clause = "delete_at is null")
@Getter
@Setter
@SuperBuilder
public class AuthPwd extends Auth {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(nullable = false)
    @Comment(value = "用户表uuid")
    private Long userId;

    @Column(nullable = false)
    @Comment(value = "历史密码")
    private String password;

    @Column(nullable = false)
    @Comment(value = "历史密码修改记录")
    private String passwordDate;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Comment(value = "是否第一次登录")
    private boolean firstLogin;


    @Column(nullable = false)
    @Comment(value = "密码错误次数")
    private short pwdErrorCount;

    @Column(nullable = false)
    @Comment(value = "锁定日期（lockDate为空，即未被锁）")
    private Date lockDate;

}
