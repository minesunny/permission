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
package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.dto.DepartmentTreeNode;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.exception.*;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.OrgMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

/**
 * {@link OrgMgrImpl} 单元测试类。
 * <p>
 * 测试基于物化路径的部门生命周期管理、组织树构建以及直属与后继用户查询。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class OrgMgrImplTest {

    @Autowired
    private OrgMgr orgMgr;

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private EntityManager entityManager;

    private Department newDept(String name, String path) {
        return Department.builder()
                .actorName(name)
                .deptName(name)
                .path(path)
                .build();
    }

    private User newUser(String username) throws RBACException {
        User user = User.builder()
                .actorName(username)
                .username(username)
                .email(username + "@example.com")
                .build();
        return adminMgr.addActor(user);
    }

    // ------------------------------------------------------------------
    // Department CRUD & Path Validation Tests
    // ------------------------------------------------------------------

    @Test
    void testCreateRootDepartment() throws RBACException {
        Department root = newDept("dept-root-1", "/100");
        Department saved = orgMgr.createDepartment(root);

        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals(1, saved.getLevel());
        Assertions.assertEquals("/100", saved.getPath());
        Assertions.assertEquals("dept-root-1", saved.getDeptName());
    }

    @Test
    void testCreateChildDepartmentSuccess() throws RBACException {
        Department root = orgMgr.createDepartment(newDept("dept-root-child-test", "/200"));
        Assertions.assertEquals(1, root.getLevel());

        // Level 2 子部门，父路径为 /200
        Department child = orgMgr.createDepartment(newDept("dept-child-test", "/200/201"));
        Assertions.assertEquals(2, child.getLevel());
        Assertions.assertEquals("/200/201", child.getPath());

        // Level 3 子部门，父路径为 /200/201
        Department grandChild = orgMgr.createDepartment(newDept("dept-grandchild-test", "/200/201/202"));
        Assertions.assertEquals(3, grandChild.getLevel());
        Assertions.assertEquals("/200/201/202", grandChild.getPath());
    }

    @Test
    void testCreateChildDepartmentParentNotExists() {
        Department invalidChild = newDept("dept-invalid-child", "/999/1000");

        RBACNotFoundException ex = Assertions.assertThrows(RBACNotFoundException.class,
                () -> orgMgr.createDepartment(invalidChild));
        Assertions.assertTrue(ex.getMessage().contains("parent department path [/999] does not exist"));
    }

    @Test
    void testCreateDepartmentDuplicateNameOrPath() throws RBACException {
        orgMgr.createDepartment(newDept("dept-dup-name", "/300"));

        // 重复名称
        Assertions.assertThrows(RBACConflictException.class,
                () -> orgMgr.createDepartment(newDept("dept-dup-name", "/301")));

        // 重复路径
        Assertions.assertThrows(RBACConflictException.class,
                () -> orgMgr.createDepartment(newDept("dept-other-name", "/300")));
    }

    @Test
    void testUpdateDepartment() throws RBACException {
        orgMgr.createDepartment(newDept("dept-to-update", "/400"));

        Department updateParam = Department.builder()
                .description("updated description")
                .build();
        Department updated = orgMgr.updateDepartment("dept-to-update", updateParam);
        Assertions.assertEquals("updated description", updated.getDescription());

        // 重命名部门名称
        Department renameParam = Department.builder()
                .deptName("dept-updated-name")
                .build();
        Department renamed = orgMgr.updateDepartment("dept-to-update", renameParam);
        Assertions.assertEquals("dept-updated-name", renamed.getDeptName());
        Assertions.assertNotNull(orgMgr.readDepartment(Department.builder().deptName("dept-updated-name").build()));
        Assertions.assertNotNull(orgMgr.findDepartment(Department.builder().deptName("dept-updated-name").build()));
    }

    @Test
    void testDeleteDepartmentWithoutChildren() throws RBACException {
        Department saved = orgMgr.createDepartment(newDept("dept-to-delete", "/500"));
        Assertions.assertNotNull(orgMgr.readDepartment(saved.getDeptName()));
        Assertions.assertNotNull(orgMgr.findDepartment(saved.getDeptName()));

        // 按名称直接删除
        orgMgr.deleteDepartment("dept-to-delete");
        Assertions.assertNull(orgMgr.findDepartment("dept-to-delete"));
        Assertions.assertThrows(RBACNotFoundException.class, () -> orgMgr.readDepartment("dept-to-delete"));
    }

    @Test
    void testDeleteDepartmentWithChildrenThrowsException() throws RBACException {
        Department root = orgMgr.createDepartment(newDept("dept-del-parent", "/600"));
        orgMgr.createDepartment(newDept("dept-del-child", "/600/601"));

        // 尝试按名称删除具有子部门的父部门
        RBACTopologyException ex = Assertions.assertThrows(RBACTopologyException.class,
                () -> orgMgr.deleteDepartment("dept-del-parent"));
        Assertions.assertTrue(ex.getMessage().contains("has child departments"));
    }

    // ------------------------------------------------------------------
    // User Membership Management Tests (addUser & removeUser)
    // ------------------------------------------------------------------

    @Test
    void testAddAndRemoveUser() throws RBACException {
        Department dept = orgMgr.createDepartment(newDept("dept-user-test", "/700"));
        User user = newUser("user-dept-test");

        // 按名称添加用户
        Assertions.assertDoesNotThrow(() -> orgMgr.addUser("dept-user-test", "user-dept-test"));

        // 重复添加抛出异常
        Assertions.assertThrows(RBACConflictException.class, () -> orgMgr.addUser("dept-user-test", "user-dept-test"));

        // 按名称验证用户在直属用户列表中
        List<User> directUsers = orgMgr.readDepartmentUsers("dept-user-test");
        Assertions.assertEquals(1, directUsers.size());
        Assertions.assertEquals(user.getUsername(), directUsers.get(0).getUsername());

        // 按名称移除用户
        Assertions.assertDoesNotThrow(() -> orgMgr.removeUser("dept-user-test", "user-dept-test"));

        // 再次移除抛出异常
        Assertions.assertThrows(RBACConflictException.class, () -> orgMgr.removeUser("dept-user-test", "user-dept-test"));

        // 列表中已无该用户
        List<User> emptyUsers = orgMgr.readDepartmentUsers("dept-user-test");
        Assertions.assertTrue(emptyUsers.isEmpty());
    }

    // ------------------------------------------------------------------
    // Tree Query Tests (readDepartmentTree & readDepartmentSubTree)
    // ------------------------------------------------------------------

    @Test
    void testReadDepartmentTreeAndSubTree() throws RBACException {
        Department rootA = orgMgr.createDepartment(newDept("tree-rootA", "/800"));
        Department childA1 = orgMgr.createDepartment(newDept("tree-childA1", "/800/801"));
        Department childA2 = orgMgr.createDepartment(newDept("tree-childA2", "/800/802"));
        Department grandChildA11 = orgMgr.createDepartment(newDept("tree-grandChildA11", "/800/801/803"));

        // 查询全树
        List<DepartmentTreeNode> fullTree = orgMgr.readDepartmentTree();
        Assertions.assertFalse(fullTree.isEmpty());

        DepartmentTreeNode rootNode = fullTree.stream()
                .filter(node -> "/800".equals(node.getDepartment().getPath()))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(rootNode);
        Assertions.assertEquals(2, rootNode.getChildren().size()); // 801, 802

        DepartmentTreeNode childA1Node = rootNode.getChildren().stream()
                .filter(node -> "/800/801".equals(node.getDepartment().getPath()))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(childA1Node);
        Assertions.assertEquals(1, childA1Node.getChildren().size()); // 803

        // 查询以 childA1 为根的子树（分别测试实体入参与名称入参）
        DepartmentTreeNode subTree = orgMgr.readDepartmentSubTree(childA1);
        Assertions.assertNotNull(subTree);
        Assertions.assertEquals("/800/801", subTree.getDepartment().getPath());
        Assertions.assertEquals(1, subTree.getChildren().size());
        Assertions.assertEquals("/800/801/803", subTree.getChildren().get(0).getDepartment().getPath());

        DepartmentTreeNode subTreeByName = orgMgr.readDepartmentSubTree("tree-childA1");
        Assertions.assertNotNull(subTreeByName);
        Assertions.assertEquals("/800/801", subTreeByName.getDepartment().getPath());
        Assertions.assertEquals(1, subTreeByName.getChildren().size());
    }

    // ------------------------------------------------------------------
    // Direct and Successor Users Query Tests
    // ------------------------------------------------------------------

    @Test
    void testReadDepartmentUsersAndSuccessorUsers() throws RBACException {
        // 创建 3 层部门
        Department deptL1 = orgMgr.createDepartment(newDept("hier-dept-L1", "/900"));
        Department deptL2 = orgMgr.createDepartment(newDept("hier-dept-L2", "/900/901"));
        Department deptL3 = orgMgr.createDepartment(newDept("hier-dept-L3", "/900/901/902"));

        // 创建 3 个用户并分别加入各级部门
        User user1 = newUser("user-L1");
        User user2 = newUser("user-L2");
        User user3 = newUser("user-L3");

        orgMgr.addUser(deptL1, user1);
        orgMgr.addUser(deptL2, user2);
        orgMgr.addUser(deptL3, user3);

        // 1. 直属用户测试（支持 ID 和 部门名称 两种方式）
        List<User> l1Direct = orgMgr.readDepartmentUsers(deptL1.getId());
        Assertions.assertEquals(1, l1Direct.size());
        Assertions.assertEquals("user-L1", l1Direct.get(0).getUsername());

        List<User> l1DirectByName = orgMgr.readDepartmentUsers("hier-dept-L1");
        Assertions.assertEquals(1, l1DirectByName.size());
        Assertions.assertEquals("user-L1", l1DirectByName.get(0).getUsername());

        List<User> l2Direct = orgMgr.readDepartmentUsers(deptL2.getId());
        Assertions.assertEquals(1, l2Direct.size());
        Assertions.assertEquals("user-L2", l2Direct.get(0).getUsername());

        List<User> l3Direct = orgMgr.readDepartmentUsers(deptL3.getId());
        Assertions.assertEquals(1, l3Direct.size());
        Assertions.assertEquals("user-L3", l3Direct.get(0).getUsername());

        // 2. 后继用户测试（包含直接用户与所有下级子孙用户，支持 ID 和 部门名称）
        // L3 的后继用户：仅自己 (user-L3)
        List<User> l3Successors = orgMgr.readDepartmentSuccessorUsers(deptL3.getId());
        Assertions.assertEquals(1, l3Successors.size());
        Assertions.assertEquals("user-L3", l3Successors.get(0).getUsername());

        // L2 的后继用户：L2 和 L3 (user-L2, user-L3)
        List<User> l2Successors = orgMgr.readDepartmentSuccessorUsers(deptL2.getId());
        Assertions.assertEquals(2, l2Successors.size());
        List<String> l2Usernames = l2Successors.stream().map(User::getUsername).toList();
        Assertions.assertTrue(l2Usernames.contains("user-L2"));
        Assertions.assertTrue(l2Usernames.contains("user-L3"));

        // L1 的后继用户：L1, L2, L3 全部包含 (user-L1, user-L2, user-L3)
        List<User> l1Successors = orgMgr.readDepartmentSuccessorUsers(deptL1.getId());
        Assertions.assertEquals(3, l1Successors.size());
        List<String> l1Usernames = l1Successors.stream().map(User::getUsername).toList();
        Assertions.assertTrue(l1Usernames.contains("user-L1"));
        Assertions.assertTrue(l1Usernames.contains("user-L2"));
        Assertions.assertTrue(l1Usernames.contains("user-L3"));

        // 通过部门名称查询后继用户
        List<User> l1SuccessorsByName = orgMgr.readDepartmentSuccessorUsers("hier-dept-L1");
        Assertions.assertEquals(3, l1SuccessorsByName.size());

        // 3. 不存在部门测试
        Assertions.assertThrows(RBACNotFoundException.class, () -> orgMgr.readDepartmentUsers(99999L));
        Assertions.assertThrows(RBACNotFoundException.class, () -> orgMgr.readDepartmentSuccessorUsers(99999L));
        Assertions.assertThrows(RBACNotFoundException.class, () -> orgMgr.readDepartmentUsers("not-exist-dept"));
        Assertions.assertThrows(RBACNotFoundException.class, () -> orgMgr.readDepartmentSuccessorUsers("not-exist-dept"));
    }

    @Test
    void testUpdateDepartmentCascadeSubTreePathsAndPreventCycle() throws RBACException {
        // 构建初始树: /p -> /p/c -> /p/c/g
        Department p = orgMgr.createDepartment(newDept("cascade-p", "/p"));
        Department c = orgMgr.createDepartment(newDept("cascade-c", "/p/c"));
        Department g = orgMgr.createDepartment(newDept("cascade-g", "/p/c/g"));
        Assertions.assertEquals(1, p.getLevel());
        Assertions.assertEquals(2, c.getLevel());
        Assertions.assertEquals(3, g.getLevel());

        // 1. 防环校验：禁止将 /p 移动到自身子孙路径 /p/c/forbidden
        RBACTopologyException cycleEx = Assertions.assertThrows(RBACTopologyException.class, () ->
                orgMgr.updateDepartment("cascade-p", Department.builder().path("/p/c/forbidden").build()));
        Assertions.assertTrue(cycleEx.getMessage().contains("descendant path"));

        // 2. 级联路径与层级更新：创建新根 /new-root，将 /p 移动到 /new-root/p
        Department newRoot = orgMgr.createDepartment(newDept("cascade-new-root", "/new-root"));
        Department updatedP = orgMgr.updateDepartment("cascade-p", Department.builder().path("/new-root/p").build());
        Assertions.assertEquals("/new-root/p", updatedP.getPath());
        Assertions.assertEquals(2, updatedP.getLevel());

        // 验证子孙节点被自动级联更新
        Department updatedC = orgMgr.readDepartment("cascade-c");
        Assertions.assertEquals("/new-root/p/c", updatedC.getPath());
        Assertions.assertEquals(3, updatedC.getLevel());

        Department updatedG = orgMgr.readDepartment("cascade-g");
        Assertions.assertEquals("/new-root/p/c/g", updatedG.getPath());
        Assertions.assertEquals(4, updatedG.getLevel());

        // 验证旧路径均已不复存在
        Assertions.assertNull(orgMgr.findDepartment(Department.builder().path("/p").build()));
        Assertions.assertThrows(RBACNotFoundException.class, () -> orgMgr.readDepartment(Department.builder().path("/p").build()));
        Assertions.assertNull(orgMgr.findDepartment(Department.builder().path("/p/c").build()));
        Assertions.assertThrows(RBACNotFoundException.class, () -> orgMgr.readDepartment(Department.builder().path("/p/c").build()));
        Assertions.assertNull(orgMgr.findDepartment(Department.builder().path("/p/c/g").build()));
        Assertions.assertThrows(RBACNotFoundException.class, () -> orgMgr.readDepartment(Department.builder().path("/p/c/g").build()));
    }

    @Test
    void testDeleteDepartmentCascadeRoleActorCleanup() throws RBACException {
        Department dept = orgMgr.createDepartment(newDept("dept-role-cascade-test", "/role-dept"));
        Role role = adminMgr.addRole(Role.builder().roleName("role-for-dept-test").build());

        // 部门作为 Actor 分配角色
        adminMgr.assignActor(dept.getDeptName(), role.getRoleName());
        Assertions.assertTrue(reviewMgr.assignedRoles(dept.getDeptName()).stream().anyMatch(r -> r.getRoleName().equals("role-for-dept-test")));

        // 删除部门
        orgMgr.deleteDepartment("dept-role-cascade-test");
        Assertions.assertNull(orgMgr.findDepartment("dept-role-cascade-test"));
        Assertions.assertThrows(RBACNotFoundException.class, () -> orgMgr.readDepartment("dept-role-cascade-test"));

        // 验证角色对应的参与者列表已无该部门
        List<com.minesunny.permission.core.entity.actor.Actor> actors = reviewMgr.assignedActors("role-for-dept-test");
        Assertions.assertTrue(actors.stream().noneMatch(a -> a.getActorName().equals("dept-role-cascade-test")));
    }
}
