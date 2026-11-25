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
package com.minesunny.permission.core.dto;

import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织架构部门树节点数据传输对象（DTO）。
 * <p>
 * 用于在内存中表示树形层次结构，包含当前节点对应的 {@link Department} 实体，
 * 递归包含的子部门节点列表 {@code children}，以及可选的直属用户列表 {@code users}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentTreeNode {

    /**
     * 当前节点对应的部门实体信息。
     */
    private Department department;

    /**
     * 当前部门的直接子部门节点列表。
     */
    @Builder.Default
    private List<DepartmentTreeNode> children = new ArrayList<>();

    /**
     * 直属于当前部门的用户列表（可选加载）。
     */
    @Builder.Default
    private List<User> users = new ArrayList<>();

}
