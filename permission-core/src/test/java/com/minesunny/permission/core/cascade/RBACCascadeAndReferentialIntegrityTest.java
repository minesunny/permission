package com.minesunny.permission.core.cascade;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.RBACNotFoundException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.OrgMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 级联删除与外键/中间表孤儿映射引用完整性综合测试套件。
 * 验证核心实体（User, Department, Role, Permission）在被删除时，
 * 中间表（t_role_actor_map, t_role_permission_map, t_department_user_map）
 * 是否 100% 干净联动清理，防止产生悬空引用与孤儿映射。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class RBACCascadeAndReferentialIntegrityTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private OrgMgr orgMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    /**
     * Test 1: 删除用户时，级联清理该用户的所有角色分配与部门归属映射
     */
    @Test
    @DisplayName("级联删除测试: 删除用户时，其在 t_role_actor_map 与 t_department_user_map 中的映射彻底清理")
    void testDeleteUserCascadeMappingsCleaned() throws RBACException {
        String username = "cas-del-user";
        String roleName = "cas-del-role";
        String deptName = "cas-del-dept";

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 1. 准备实体并建立关联
        tx.execute(status -> {
            try {
                User user = adminMgr.addUser(User.builder().actorName(username).username(username).build());
                Role role = adminMgr.addRole(Role.builder().roleName(roleName).build());
                Department dept = orgMgr.createDepartment(Department.builder().actorName(deptName).deptName(deptName).path("/cas-del-dept").build());

                adminMgr.assignActor(user, role);
                orgMgr.addUser(deptName, username);
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        // 验证关系已建立
        User user = reviewMgr.readUser(username);
        Role role = reviewMgr.readRole(roleName);
        assertTrue(reviewMgr.hasRole(user, role));
        assertEquals(1, orgMgr.readDepartmentUsers(deptName).size());

        // 2. 执行删除用户
        tx.execute(status -> {
            try {
                adminMgr.deleteUser(username);
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        // 3. 验证用户已不存在
        assertNull(reviewMgr.findUser(username));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readUser(username));

        // 4. 验证角色的分配用户列表中已不包含该用户
        List<com.minesunny.permission.core.entity.actor.Actor> actors = reviewMgr.assignedActors(roleName);
        assertTrue(actors.isEmpty(), "删除用户后，角色的 assignedActors 应为空，不可残留孤儿映射");

        // 5. 验证部门下的用户列表已不包含该用户
        List<User> deptUsers = orgMgr.readDepartmentUsers(deptName);
        assertTrue(deptUsers.isEmpty(), "删除用户后，部门下的用户列表应为空，不可残留孤儿映射");

        // 6. 验证 Role 和 Department 实体本身不受影响
        assertNotNull(reviewMgr.findRole(roleName));
        assertNotNull(orgMgr.findDepartment(deptName));
    }

    /**
     * Test 2: 删除角色时，级联清理所有 Actor 分配与 Permission 授权映射
     */
    @Test
    @DisplayName("级联删除测试: 删除角色时，其在 t_role_actor_map 与 t_role_permission_map 中的映射彻底清理")
    void testDeleteRoleCascadeMappingsCleaned() throws RBACException {
        String username = "cas-role-user";
        String roleName = "cas-del-role-target";
        String permName = "cas-del-role-perm";

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 1. 初始化
        tx.execute(status -> {
            try {
                User user = adminMgr.addUser(User.builder().actorName(username).username(username).build());
                Role role = adminMgr.addRole(Role.builder().roleName(roleName).build());
                Permission perm = adminMgr.addPermission(Permission.builder().permName(permName).build());

                adminMgr.assignActor(user, role);
                adminMgr.grantPermission(perm, role);
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        // 验证关系
        User user = reviewMgr.readUser(username);
        Role role = reviewMgr.readRole(roleName);
        Permission perm = reviewMgr.readPermission(permName);
        assertTrue(reviewMgr.hasRole(user, role));
        assertTrue(reviewMgr.hasPermission(user, perm));

        // 2. 执行删除角色
        tx.execute(status -> {
            try {
                adminMgr.deleteRole(roleName);
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        // 3. 验证角色已不存在
        assertNull(reviewMgr.findRole(roleName));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readRole(roleName));

        // 4. 验证用户的角色列表已不包含该角色，且失去了相应权限
        List<Role> userRoles = reviewMgr.assignedRoles(username);
        assertTrue(userRoles.isEmpty(), "删除角色后，用户 assignedRoles 必须为空");
        assertFalse(reviewMgr.hasPermission(user, perm), "角色被删除后，用户不再享有通过该角色获得的权限");

        // 5. 验证权限实体依然存在，但没有角色关联
        assertNotNull(reviewMgr.findPermission(permName));
        List<Role> permRoles = reviewMgr.permissionRoles(perm);
        assertTrue(permRoles.isEmpty(), "权限关联的角色列表应为空");
    }

    /**
     * Test 3: 删除权限时，级联清理所有角色的权限映射
     */
    @Test
    @DisplayName("级联删除测试: 删除权限时，所有拥有该权限的角色的权限映射彻底清理")
    void testDeletePermissionCascadeMappingsCleaned() throws RBACException {
        String role1Name = "cas-perm-role-1";
        String role2Name = "cas-perm-role-2";
        String permName = "cas-del-perm-target";

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 1. 初始化两角色一权限并关联
        tx.execute(status -> {
            try {
                Role r1 = adminMgr.addRole(Role.builder().roleName(role1Name).build());
                Role r2 = adminMgr.addRole(Role.builder().roleName(role2Name).build());
                Permission p = adminMgr.addPermission(Permission.builder().permName(permName).build());

                adminMgr.grantPermission(p, r1);
                adminMgr.grantPermission(p, r2);
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        // 验证两角色均具有该权限
        Role r1 = reviewMgr.readRole(role1Name);
        Role r2 = reviewMgr.readRole(role2Name);
        Permission p = reviewMgr.readPermission(permName);
        assertEquals(1, reviewMgr.rolePermissions(r1).size());
        assertEquals(1, reviewMgr.rolePermissions(r2).size());

        // 2. 执行删除权限
        tx.execute(status -> {
            try {
                adminMgr.deletePermission(permName);
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        // 3. 验证权限已删除
        assertNull(reviewMgr.findPermission(permName));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readPermission(permName));

        // 4. 验证两个角色的权限列表自动变为空，无孤儿映射
        assertTrue(reviewMgr.rolePermissions(r1).isEmpty(), "r1 的权限列表应为空");
        assertTrue(reviewMgr.rolePermissions(r2).isEmpty(), "r2 的权限列表应为空");
    }

    /**
     * Test 4: 删除部门时，部门与用户的映射清理，但用户本身不应受损
     */
    @Test
    @DisplayName("级联删除测试: 删除部门清理与该部门的用户关联映射，直属用户完好保留")
    void testDeleteDepartmentCascadeUserMappingsCleaned() throws RBACException {
        String deptName = "cas-dept-alone";
        String user1 = "cas-dept-u1";
        String user2 = "cas-dept-u2";

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.execute(status -> {
            try {
                orgMgr.createDepartment(Department.builder().actorName(deptName).deptName(deptName).path("/cas-dept-alone").build());
                adminMgr.addUser(User.builder().actorName(user1).username(user1).build());
                adminMgr.addUser(User.builder().actorName(user2).username(user2).build());

                orgMgr.addUser(deptName, user1);
                orgMgr.addUser(deptName, user2);
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        assertEquals(2, orgMgr.readDepartmentUsers(deptName).size());

        // 删除部门
        tx.execute(status -> {
            try {
                orgMgr.deleteDepartment(deptName);
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        // 部门已被删除
        assertNull(orgMgr.findDepartment(deptName));

        // 两个用户完好无损，且没有脏映射
        assertNotNull(reviewMgr.findUser(user1));
        assertNotNull(reviewMgr.findUser(user2));
    }
}
