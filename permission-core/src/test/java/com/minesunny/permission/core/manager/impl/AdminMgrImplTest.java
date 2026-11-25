package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.*;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.OrgMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.type.RBACEntityConstants;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Commit;

import java.util.List;


@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection =
        EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class AdminMgrImplTest {
    @Autowired
    EntityManager entityManager;
    @Autowired
    private AdminMgr adminMgr;
    @Autowired
    private ReviewMgr reviewMgr;
    @Autowired
    private OrgMgr orgMgr;

    // ------------------------------------------------------------------
    // addActor
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testAddActor() throws RBACException {
        User user = User.builder().actorName("adm-addActor").username("adm-addActor").build();
        User addActor = adminMgr.addActor(user);
        Assertions.assertNotNull(addActor);
        Assertions.assertThrows(RBACConflictException.class,
                () -> adminMgr.addActor(addActor));
    }

    // ------------------------------------------------------------------
    // updateActor
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testUpdateActor() throws RBACException {
        Assertions.assertThrows(RBACNotFoundException.class,
                () -> adminMgr.updateActor("notExistActor", new User()));

        User addActor = adminMgr.addActor(
                User.builder().actorName("adm-updateActor").username("adm-updateActor").build());
        addActor.setDescription("update actor");
        Assertions.assertEquals("update actor",
                adminMgr.updateActor("adm-updateActor", addActor).getDescription());

        entityManager.detach(addActor);
        addActor.setActorName("adm-updateActor-renamed");
        adminMgr.updateActor("adm-updateActor", addActor);

        // old actorName is now free → can create again
        Assertions.assertDoesNotThrow(() -> adminMgr.addActor(
                User.builder().actorName("adm-updateActor").username("adm-updateActor-reuse").build()));

        // renaming to an already-taken actorName must fail
        adminMgr.addActor(User.builder().actorName("adm-updateActor-dup1").username("adm-dup1").build());
        adminMgr.addActor(User.builder().actorName("adm-updateActor-dup2").username("adm-dup2").build());
        Assertions.assertThrows(RBACConflictException.class,
                () -> adminMgr.updateActor("adm-updateActor-dup1",
                        User.builder().actorName("adm-updateActor-dup2").build()));
    }

    // ------------------------------------------------------------------
    // deleteActor
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testDeleteActor() throws RBACException {
        User addActor = adminMgr.addActor(
                User.builder().actorName("adm-deleteActor").username("adm-deleteActor").build());
        adminMgr.deleteActor(addActor);
        Assertions.assertNull(reviewMgr.findActor(addActor));
        Assertions.assertThrows(RBACNotFoundException.class, () -> reviewMgr.readActor(addActor));
    }

    // ------------------------------------------------------------------
    // Role CRUD
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testAddRole() throws RBACException {
        Role role = Role.builder().roleName("adm-addRole").build();
        Role addRole = adminMgr.addRole(role);
        Assertions.assertNotNull(addRole);
        Assertions.assertThrows(RBACConflictException.class,
                () -> adminMgr.addRole(addRole));
    }

    @Test
    @Commit
    void testUpdateRole() throws RBACException {
        Assertions.assertThrows(RBACNotFoundException.class,
                () -> adminMgr.updateRole("notExistRole", new Role()));
        Role addRole = adminMgr.addRole(Role.builder().roleName("adm-updateRole").build());
        addRole.setDescription("update role");
        Assertions.assertEquals("update role", adminMgr.updateRole("adm-updateRole", addRole).getDescription());
        entityManager.detach(addRole);
        addRole.setRoleName("adm-updateRole-renamed");
        adminMgr.updateRole("adm-updateRole", addRole);
        Assertions.assertNotNull(reviewMgr.readRole(Role.builder().roleName("adm-updateRole-renamed").build()));
        Assertions.assertNull(reviewMgr.findRole(Role.builder().roleName("adm-updateRole").build()));
        Assertions.assertThrows(RBACNotFoundException.class, () -> reviewMgr.readRole(Role.builder().roleName("adm-updateRole").build()));

        adminMgr.addRole(Role.builder().roleName("adm-updateRole-dup1").build());
        adminMgr.addRole(Role.builder().roleName("adm-updateRole-dup2").build());
        Assertions.assertThrows(RBACConflictException.class,
                () -> adminMgr.updateRole("adm-updateRole-dup1",
                        Role.builder().roleName("adm-updateRole-dup2").build()));
        Assertions.assertNotNull(reviewMgr.readRole(Role.builder().roleName("adm-updateRole-dup1").build()));
    }

    @Test
    @Commit
    void testDeleteRole() throws RBACException {
        Role addRole = adminMgr.addRole(Role.builder().roleName("adm-deleteRole").build());
        adminMgr.deleteRole(addRole);
        Assertions.assertNull(reviewMgr.findRole(addRole));
        Assertions.assertThrows(RBACNotFoundException.class, () -> reviewMgr.readRole(addRole));
    }

    // ------------------------------------------------------------------
    // assignActor / deassignActor
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testAssignActor() throws RBACException {
        Assertions.assertThrows(RBACNotFoundException.class,
                () -> adminMgr.assignActor(RBACEntityConstants.NOT_EXIST_USER, RBACEntityConstants.NOT_EXIST_ROLE));

        User user = adminMgr.addActor(
                User.builder().actorName("adm-assignActor").username("adm-assignActor").build());
        Assertions.assertThrows(RBACNotFoundException.class,
                () -> adminMgr.assignActor(user, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-assignActorRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.assignActor(user, role));
        List<Role> roles = reviewMgr.assignedRoles(user);
        Assertions.assertTrue(roles.contains(role));

        Assertions.assertThrows(RBACConflictException.class,
                () -> adminMgr.assignActor(user, role));
    }

    @Test
    @Commit
    void testDeassignActor() throws RBACException {
        Assertions.assertThrows(RBACNotFoundException.class,
                () -> adminMgr.deassignActor(RBACEntityConstants.NOT_EXIST_USER, RBACEntityConstants.NOT_EXIST_ROLE));

        User user = adminMgr.addActor(
                User.builder().actorName("adm-deassignActor").username("adm-deassignActor").build());
        Assertions.assertThrows(RBACNotFoundException.class,
                () -> adminMgr.deassignActor(user, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-deassignActorRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.assignActor(user, role));
        List<Role> roles = reviewMgr.assignedRoles(user);
        Assertions.assertTrue(roles.contains(role));

        Assertions.assertDoesNotThrow(() -> adminMgr.deassignActor(user, role));
        roles = reviewMgr.assignedRoles(user);
        Assertions.assertFalse(roles.contains(role));

        Assertions.assertThrows(RBACConflictException.class,
                () -> adminMgr.deassignActor(user, role));
    }

    // ------------------------------------------------------------------
    // Permission CRUD + grant/revoke
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testAddPermission() throws RBACException {
        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-addPerm1").resource("res1").operation("read").build());
        Assertions.assertNotNull(reviewMgr.readPermission(permission1));

        Permission permission2 = adminMgr.addPermission(Permission.builder().permName("adm-addPerm2").resource("res2").operation("write").build());
        Assertions.assertNotNull(reviewMgr.readPermission(permission2));

        Assertions.assertThrows(RBACConflictException.class, () -> adminMgr.addPermission(permission1));
        Assertions.assertThrows(RBACConflictException.class, () -> adminMgr.addPermission(permission2));
    }

    @Test
    @Commit
    void updatePermission() throws RBACException {
        Assertions.assertThrows(RBACNotFoundException.class,
                () -> adminMgr.updatePermission("notExistPerm", RBACEntityConstants.PERMISSION));

        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-updatePerm").resource("res1").operation("read").build());
        permission1.setDescription("updated description");
        Assertions.assertDoesNotThrow(() -> adminMgr.updatePermission("adm-updatePerm", permission1));
    }

    @Test
    @Commit
    void deletePermission() throws RBACException {
        Assertions.assertThrows(RBACValidationException.class, () -> adminMgr.deletePermission(RBACEntityConstants.PERMISSION));

        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-deletePerm").resource("res1").operation("read").build());
        Assertions.assertNotNull(reviewMgr.readPermission(permission1));

        adminMgr.deletePermission(permission1);
        Assertions.assertNull(reviewMgr.findPermission(permission1));
        Assertions.assertThrows(RBACNotFoundException.class, () -> reviewMgr.readPermission(permission1));
    }

    @Test
    @Commit
    void testGrantPermission() throws RBACException {
        Assertions.assertThrows(RBACNotFoundException.class,
                () -> adminMgr.grantPermission(RBACEntityConstants.NOT_EXIST_PERMISSION, RBACEntityConstants.NOT_EXIST_ROLE));
        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-grantPerm").resource("res1").operation("read").build());
        Assertions.assertThrows(RBACNotFoundException.class,
                () -> adminMgr.grantPermission(permission1, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-grantRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.grantPermission(permission1, role));
        List<Permission> permissions = reviewMgr.rolePermissions(role);
        Assertions.assertTrue(permissions.contains(permission1));
        Assertions.assertThrows(RBACConflictException.class,
                () -> adminMgr.grantPermission(permission1, role));
    }

    @Test
    @Commit
    void testRevokePermission() throws RBACException {
        Assertions.assertThrows(RBACNotFoundException.class,
                () -> adminMgr.revokePermission(RBACEntityConstants.NOT_EXIST_PERMISSION, RBACEntityConstants.NOT_EXIST_ROLE));
        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-revokePerm").resource("res1").operation("read").build());
        Assertions.assertThrows(RBACNotFoundException.class,
                () -> adminMgr.revokePermission(permission1, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-revokeRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.grantPermission(permission1, role));
        Assertions.assertDoesNotThrow(() -> adminMgr.revokePermission(permission1, role));
        Assertions.assertFalse(reviewMgr.rolePermissions(role).contains(permission1));
        Assertions.assertThrows(RBACConflictException.class,
                () -> adminMgr.revokePermission(permission1, role));
    }

    @Test
    @Commit
    void testDeleteRoleCascadeRoleActorAndRolePermissionCleanup() throws RBACException {
        Role role = adminMgr.addRole(Role.builder().roleName("role-cascade-del").build());
        User user = adminMgr.addActor(User.builder().actorName("user-cascade-del").username("user-cascade-del").build());
        Permission perm = adminMgr.addPermission(Permission.builder().permName("perm-cascade-del").build());

        adminMgr.assignActor("user-cascade-del", "role-cascade-del");
        adminMgr.grantPermission("perm-cascade-del", "role-cascade-del");

        Assertions.assertTrue(reviewMgr.hasRole("user-cascade-del", "role-cascade-del"));
        Assertions.assertTrue(reviewMgr.hasPermission("user-cascade-del", "perm-cascade-del"));

        // 删除角色
        adminMgr.deleteRole("role-cascade-del");
        Assertions.assertNull(reviewMgr.findRole("role-cascade-del"));
        Assertions.assertThrows(RBACNotFoundException.class, () -> reviewMgr.readRole("role-cascade-del"));

        // 验证用户角色已清空，权限也不再拥有
        Assertions.assertFalse(reviewMgr.hasRole("user-cascade-del", "role-cascade-del"));
        Assertions.assertFalse(reviewMgr.hasPermission("user-cascade-del", "perm-cascade-del"));
        Assertions.assertTrue(reviewMgr.assignedRoles("user-cascade-del").isEmpty());
    }

    @Test
    @Commit
    void testDeleteActorWithDepartmentChildrenProtection() throws RBACException {
        orgMgr.createDepartment(Department.builder().deptName("adm-del-parent").path("/adm-p").build());
        orgMgr.createDepartment(Department.builder().deptName("adm-del-child").path("/adm-p/c").build());

        // 试图通过 adminMgr.deleteActor 删除带有子部门的部门，应被子部门保护拦截
        RBACTopologyException ex = Assertions.assertThrows(RBACTopologyException.class,
                () -> adminMgr.deleteActor("adm-del-parent"));
        Assertions.assertTrue(ex.getMessage().contains("has child departments"));
    }

    // ------------------------------------------------------------------
    // User CRUD & Read by Name in AdminMgr
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testUserCrudAndReadByName() throws RBACException {
        // 1. addUser
        User user = User.builder().username("adm-user-crud").email("crud@test.com").description("user desc").build();
        User created = adminMgr.addUser(user);
        Assertions.assertNotNull(created.getId());
        Assertions.assertEquals("adm-user-crud", created.getUsername());
        Assertions.assertEquals("adm-user-crud", created.getActorName());

        // 重复添加抛出 RBACConflictException
        Assertions.assertThrows(RBACConflictException.class, () -> adminMgr.addUser(user));

        // 2. readUser by username
        Assertions.assertNotNull(adminMgr.readUser("adm-user-crud"));
        Assertions.assertEquals("crud@test.com", adminMgr.readUser("adm-user-crud").getEmail());
        Assertions.assertNull(adminMgr.findUser("not-exist-user-xyz"));
        Assertions.assertThrows(RBACNotFoundException.class, () -> adminMgr.readUser("not-exist-user-xyz"));

        // 3. updateUser
        created.setDescription("updated desc");
        User updated = adminMgr.updateUser("adm-user-crud", created);
        Assertions.assertEquals("updated desc", updated.getDescription());

        // 4. deleteUser by username
        adminMgr.deleteUser("adm-user-crud");
        Assertions.assertNull(adminMgr.findUser("adm-user-crud"));
        Assertions.assertThrows(RBACNotFoundException.class, () -> adminMgr.readUser("adm-user-crud"));

        // 再次删除不存在的用户抛出 RBACNotFoundException
        Assertions.assertThrows(RBACNotFoundException.class, () -> adminMgr.deleteUser("adm-user-crud"));
    }

    // ------------------------------------------------------------------
    // Symmetrical Read-by-Name & Department delete in AdminMgr
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testAdminMgrSymmetricalReadAndDeptDelete() throws RBACException {
        // Role read
        adminMgr.addRole(Role.builder().roleName("adm-sym-role").build());
        Assertions.assertNotNull(adminMgr.readRole("adm-sym-role"));
        Assertions.assertNull(adminMgr.findRole("not-exist-role-xyz"));
        Assertions.assertThrows(RBACNotFoundException.class, () -> adminMgr.readRole("not-exist-role-xyz"));

        // Permission read
        adminMgr.addPermission(Permission.builder().permName("adm-sym-perm").build());
        Assertions.assertNotNull(adminMgr.readPermission("adm-sym-perm"));
        Assertions.assertNull(adminMgr.findPermission("not-exist-perm-xyz"));
        Assertions.assertThrows(RBACNotFoundException.class, () -> adminMgr.readPermission("not-exist-perm-xyz"));

        // Actor read
        adminMgr.addActor(User.builder().actorName("adm-sym-actor").username("adm-sym-actor").build());
        Assertions.assertNotNull(adminMgr.readActor("adm-sym-actor"));
        Assertions.assertNull(adminMgr.findActor("not-exist-actor-xyz"));
        Assertions.assertThrows(RBACNotFoundException.class, () -> adminMgr.readActor("not-exist-actor-xyz"));

        // Department read & delete
        orgMgr.createDepartment(Department.builder().deptName("adm-sym-dept").path("/adm-sym-dept").build());
        Assertions.assertNotNull(adminMgr.readDepartment("adm-sym-dept"));

        adminMgr.deleteDepartment("adm-sym-dept");
        Assertions.assertNull(adminMgr.findDepartment("adm-sym-dept"));
        Assertions.assertThrows(RBACNotFoundException.class, () -> adminMgr.readDepartment("adm-sym-dept"));
        Assertions.assertThrows(RBACNotFoundException.class, () -> adminMgr.deleteDepartment("adm-sym-dept"));
    }
}
