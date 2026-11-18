package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
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

    // ------------------------------------------------------------------
    // addActor
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testAddActor() throws RBACException {
        User user = User.builder().actorName("adm-addActor").username("adm-addActor").build();
        User addActor = adminMgr.addActor(user);
        Assertions.assertNotNull(addActor);
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.addActor(addActor));
    }

    // ------------------------------------------------------------------
    // updateActor
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testUpdateActor() throws RBACException {
        Assertions.assertThrows(ValidationException.class,
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
        Assertions.assertThrows(ValidationException.class,
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
        Assertions.assertTrue(reviewMgr.readActor(addActor).isEmpty());
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
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.addRole(addRole));
    }

    @Test
    @Commit
    void testUpdateRole() throws RBACException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updateRole("notExistRole", new Role()));
        Role addRole = adminMgr.addRole(Role.builder().roleName("adm-updateRole").build());
        addRole.setDescription("update role");
        Assertions.assertEquals("update role", adminMgr.updateRole("adm-updateRole", addRole).getDescription());
        entityManager.detach(addRole);
        addRole.setRoleName("adm-updateRole-renamed");
        adminMgr.updateRole("adm-updateRole", addRole);
        Assertions.assertTrue(reviewMgr.readRole(Role.builder().roleName("adm-updateRole-renamed").build()).isPresent());
        Assertions.assertTrue(reviewMgr.readRole(Role.builder().roleName("adm-updateRole").build()).isEmpty());

        adminMgr.addRole(Role.builder().roleName("adm-updateRole-dup1").build());
        adminMgr.addRole(Role.builder().roleName("adm-updateRole-dup2").build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updateRole("adm-updateRole-dup1",
                        Role.builder().roleName("adm-updateRole-dup2").build()));
        Assertions.assertTrue(reviewMgr.readRole(Role.builder().roleName("adm-updateRole-dup1").build()).isPresent());
    }

    @Test
    @Commit
    void testDeleteRole() throws RBACException {
        Role addRole = adminMgr.addRole(Role.builder().roleName("adm-deleteRole").build());
        adminMgr.deleteRole(addRole);
        Assertions.assertTrue(reviewMgr.readRole(addRole).isEmpty());
    }

    // ------------------------------------------------------------------
    // assignActor / deassignActor
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testAssignActor() throws RBACException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.assignActor(RBACEntityConstants.NOT_EXIST_USER, RBACEntityConstants.NOT_EXIST_ROLE));

        User user = adminMgr.addActor(
                User.builder().actorName("adm-assignActor").username("adm-assignActor").build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.assignActor(user, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-assignActorRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.assignActor(user, role));
        List<Role> roles = reviewMgr.assignedRoles(user);
        Assertions.assertTrue(roles.contains(role));

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.assignActor(user, role));
    }

    @Test
    @Commit
    void testDeassignActor() throws RBACException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.deassignActor(RBACEntityConstants.NOT_EXIST_USER, RBACEntityConstants.NOT_EXIST_ROLE));

        User user = adminMgr.addActor(
                User.builder().actorName("adm-deassignActor").username("adm-deassignActor").build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.deassignActor(user, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-deassignActorRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.assignActor(user, role));
        List<Role> roles = reviewMgr.assignedRoles(user);
        Assertions.assertTrue(roles.contains(role));

        Assertions.assertDoesNotThrow(() -> adminMgr.deassignActor(user, role));
        roles = reviewMgr.assignedRoles(user);
        Assertions.assertFalse(roles.contains(role));

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.deassignActor(user, role));
    }

    @Test
    @Commit
    void testAssignAndDeassignManagedRole() throws RBACException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.assignManagedRole(RBACEntityConstants.NOT_EXIST_USER, RBACEntityConstants.NOT_EXIST_ROLE));

        User user = adminMgr.addActor(
                User.builder().actorName("adm-managedRoleActor").username("adm-managedRoleActor").build());
        Role role = adminMgr.addRole(Role.builder().roleName("adm-managedRoleTarget").build());

        Assertions.assertDoesNotThrow(() -> adminMgr.assignManagedRole(user, role));
        Assertions.assertThrows(ValidationException.class, () -> adminMgr.assignManagedRole(user, role));

        Assertions.assertDoesNotThrow(() -> adminMgr.deassignManagedRole(user, role));
        Assertions.assertThrows(ValidationException.class, () -> adminMgr.deassignManagedRole(user, role));
    }

    // ------------------------------------------------------------------
    // Permission CRUD + grant/revoke
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testAddPermission() throws RBACException {
        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-addPerm1").resource("res1").operation("read").build());
        Assertions.assertTrue(reviewMgr.readPermission(permission1).isPresent());

        Permission permission2 = adminMgr.addPermission(Permission.builder().permName("adm-addPerm2").resource("res2").operation("write").build());
        Assertions.assertTrue(reviewMgr.readPermission(permission2).isPresent());

        Assertions.assertThrows(ValidationException.class, () -> adminMgr.addPermission(permission1));
        Assertions.assertThrows(ValidationException.class, () -> adminMgr.addPermission(permission2));
    }

    @Test
    @Commit
    void updatePermission() throws RBACException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updatePermission("notExistPerm", RBACEntityConstants.PERMISSION));

        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-updatePerm").resource("res1").operation("read").build());
        permission1.setDescription("updated description");
        Assertions.assertDoesNotThrow(() -> adminMgr.updatePermission("adm-updatePerm", permission1));
    }

    @Test
    @Commit
    void deletePermission() throws RBACException {
        Assertions.assertThrows(ValidationException.class, () -> adminMgr.deletePermission(RBACEntityConstants.PERMISSION));

        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-deletePerm").resource("res1").operation("read").build());
        Assertions.assertTrue(reviewMgr.readPermission(permission1).isPresent());

        adminMgr.deletePermission(permission1);
        Assertions.assertFalse(reviewMgr.readPermission(permission1).isPresent());
    }

    @Test
    @Commit
    void testGrantPermission() throws RBACException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.grantPermission(RBACEntityConstants.NOT_EXIST_PERMISSION, RBACEntityConstants.NOT_EXIST_ROLE));
        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-grantPerm").resource("res1").operation("read").build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.grantPermission(permission1, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-grantRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.grantPermission(permission1, role));
        List<Permission> permissions = reviewMgr.rolePermissions(role);
        Assertions.assertTrue(permissions.contains(permission1));
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.grantPermission(permission1, role));
    }

    @Test
    @Commit
    void testRevokePermission() throws RBACException {
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.revokePermission(RBACEntityConstants.NOT_EXIST_PERMISSION, RBACEntityConstants.NOT_EXIST_ROLE));
        Permission permission1 = adminMgr.addPermission(Permission.builder().permName("adm-revokePerm").resource("res1").operation("read").build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.revokePermission(permission1, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().roleName("adm-revokeRole").build());
        Assertions.assertDoesNotThrow(() -> adminMgr.grantPermission(permission1, role));
        Assertions.assertDoesNotThrow(() -> adminMgr.revokePermission(permission1, role));
        Assertions.assertFalse(reviewMgr.rolePermissions(role).contains(permission1));
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.revokePermission(permission1, role));
    }

    @Test
    @Commit
    void testAddPermissionWithPolicyTemplate() throws RBACException {
        com.minesunny.permission.core.entity.perm.Policy policy = com.minesunny.permission.core.entity.perm.Policy.builder()
                .policyName("template-policy-1")
                .description("template policy description")
                .build();

        Permission permission = Permission.builder()
                .permName("perm-with-template")
                .resource("res-tmpl")
                .operation("read")
                .policy(policy)
                .build();

        Permission savedPerm = adminMgr.addPermission(permission);
        Assertions.assertNotNull(savedPerm);
        Assertions.assertNotNull(savedPerm.getPolicy());
        Assertions.assertNotNull(savedPerm.getPolicy().getId());
    }

    @Test
    @Commit
    void testPermissionPolicyCRUD() throws RBACException {
        User user = User.builder().actorName("policy-user-1").username("policy-user-1").build();
        adminMgr.addActor(user);

        Permission permission = Permission.builder()
                .permName("perm-policy-crud")
                .resource("res-crud")
                .operation("write")
                .build();
        adminMgr.addPermission(permission);

        com.minesunny.permission.core.entity.perm.Policy userPolicy1 = com.minesunny.permission.core.entity.perm.Policy.builder()
                .policyName("user-policy-1")
                .description("user policy 1")
                .build();

        Assertions.assertDoesNotThrow(() -> adminMgr.addPermissionPolicy("policy-user-1", "perm-policy-crud", userPolicy1));

        Assertions.assertThrows(ValidationException.class, () ->
                adminMgr.addPermissionPolicy("policy-user-1", "perm-policy-crud", userPolicy1));

        com.minesunny.permission.core.entity.perm.Policy userPolicy2 = com.minesunny.permission.core.entity.perm.Policy.builder()
                .policyName("user-policy-2")
                .description("user policy 2")
                .build();

        Assertions.assertDoesNotThrow(() -> adminMgr.updatePermissionPolicy("policy-user-1", "perm-policy-crud", userPolicy2));

        Assertions.assertDoesNotThrow(() -> adminMgr.deletePermissionPolicy("policy-user-1", "perm-policy-crud"));
    }
}
