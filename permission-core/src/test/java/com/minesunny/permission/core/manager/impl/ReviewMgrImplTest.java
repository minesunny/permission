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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Commit;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection =
        EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class ReviewMgrImplTest {
    @Autowired
    private AdminMgr adminMgr;
    @Autowired
    private ReviewMgr reviewMgr;

    /** Helper: create and persist a User Actor (actorName = username). */
    private User newUser(String name) throws RBACException {
        return adminMgr.addActor(User.builder().actorName(name).username(name).build());
    }

    // ------------------------------------------------------------------
    // readActor / readActors
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testReadActor() throws RBACException {
        // Empty actor (no actorName) → requireActorName throws ValidationException
        assertThrows(ValidationException.class, () -> reviewMgr.readActor(RBACEntityConstants.ACTOR));

        User user = User.builder()
                .actorName("rev-readActor")
                .username("rev-readActor")
                .description("test readActor")
                .build();
        Actor saved = adminMgr.addActor(user);

        Optional<Actor> readActor = reviewMgr.readActor(saved);
        assertTrue(readActor.isPresent());
        // JPA JOINED inheritance returns the concrete subtype; cast is safe
        assertEquals("test readActor", ((User) readActor.get()).getDescription());
    }

    @Test
    @Commit
    void testReadActors() throws RBACException {
        // No search condition → ValidationException
        assertThrows(ValidationException.class, () -> reviewMgr.readActors(RBACEntityConstants.ACTOR));

        adminMgr.addActor(User.builder().actorName("rev-readActors-1").username("rev-readActors-1").build());
        adminMgr.addActor(User.builder().actorName("rev-readActors-2").username("rev-readActors-2").build());

        List<Actor> actors = reviewMgr.readActors(
                User.builder().actorName("rev-readActors").build().like("actorName", true));
        assertEquals(2, actors.size());
    }

    // ------------------------------------------------------------------
    // readRole / readRoles
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testReadRole() throws RBACException {
        assertThrows(ValidationException.class, () -> reviewMgr.readRole(RBACEntityConstants.ROLE));
        Role role = Role.builder().roleName("rev-readRole").description("test testReadRole").build();

        Optional<Role> readRole = reviewMgr.readRole(adminMgr.addRole(role));
        assertTrue(readRole.isPresent());
        assertEquals("test testReadRole", readRole.get().getDescription());

        role.andEq("description");
        List<Role> roles = reviewMgr.readRoles(role);
        assertFalse(roles.isEmpty());
        assertEquals("test testReadRole", roles.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadRoles() throws RBACException {
        assertThrows(ValidationException.class, () -> reviewMgr.readRoles(RBACEntityConstants.ROLE));
        adminMgr.addRole(Role.builder().roleName("rev-readRoles-1").description("abcdefg").build());
        adminMgr.addRole(Role.builder().roleName("rev-readRoles-2").description("abc").build());

        List<Role> roles = reviewMgr.readRoles(Role.builder().description("abc").build().like("description", true));
        assertEquals(2, roles.size());
    }

    // ------------------------------------------------------------------
    // readPermission / readPermissions
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testReadPermission() throws RBACException {
        assertThrows(ValidationException.class, () -> reviewMgr.readPermission(RBACEntityConstants.PERMISSION));
        Permission permission = Permission.builder().permName("rev-readPerm").description("test testReadPermission").build();

        Optional<Permission> readPermission = reviewMgr.readPermission(adminMgr.addPermission(permission));
        assertTrue(readPermission.isPresent());
        assertEquals("test testReadPermission", readPermission.get().getDescription());

        permission.andEq("description");
        List<Permission> permissions = reviewMgr.readPermissions(permission);
        assertFalse(permissions.isEmpty());
        assertEquals("test testReadPermission", permissions.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadPermissions() throws RBACException {
        assertThrows(ValidationException.class, () -> reviewMgr.readPermission(RBACEntityConstants.PERMISSION));
        adminMgr.addPermission(Permission.builder().permName("rev-readPerms-1").description("abcdefg").build());
        adminMgr.addPermission(Permission.builder().permName("rev-readPerms-2").description("abc").build());

        List<Permission> permissions = reviewMgr.readPermissions(
                Permission.builder().description("abc").build().like("description", true));
        assertEquals(2, permissions.size());
    }

    // ------------------------------------------------------------------
    // rolePermissions
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testRolePermissions() throws RBACException {
        assertEquals(0, reviewMgr.rolePermissions(RBACEntityConstants.NOT_EXIST_ROLE).size());

        Role role = adminMgr.addRole(Role.builder().roleName("rev-rolePerms").build());
        Permission p1 = adminMgr.addPermission(Permission.builder().permName("rev-rolePerms-p1").build());
        Permission p2 = adminMgr.addPermission(Permission.builder().permName("rev-rolePerms-p2").build());
        adminMgr.grantPermission(p1, role);
        adminMgr.grantPermission(p2, role);

        List<Permission> permissions = reviewMgr.rolePermissions(role);
        assertTrue(permissions.contains(p1));
        assertTrue(permissions.contains(p2));
    }

    // ------------------------------------------------------------------
    // actorPermissions
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testActorPermissions() throws RBACException {
        assertEquals(0, reviewMgr.actorPermissions(RBACEntityConstants.NOT_EXIST_USER).size());

        Role role = adminMgr.addRole(Role.builder().roleName("rev-actorPerms-role").build());
        User user = newUser("rev-actorPerms-user");
        Permission p1 = adminMgr.addPermission(Permission.builder().permName("rev-actorPerms-p1").build());
        Permission p2 = adminMgr.addPermission(Permission.builder().permName("rev-actorPerms-p2").build());
        adminMgr.grantPermission(p1, role);
        adminMgr.grantPermission(p2, role);
        adminMgr.assignActor(user, role);

        List<Permission> permissions = reviewMgr.actorPermissions(user);
        assertTrue(permissions.contains(p1));
        assertTrue(permissions.contains(p2));
    }

    // ------------------------------------------------------------------
    // assignedActors
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testAssignedActors() throws RBACException {
        assertEquals(0, reviewMgr.assignedActors(RBACEntityConstants.NOT_EXIST_ROLE).size());

        Role role = adminMgr.addRole(Role.builder().roleName("rev-assignedActors-role").build());
        User user1 = newUser("rev-assignedActors-u1");
        User user2 = newUser("rev-assignedActors-u2");
        adminMgr.assignActor(user1, role);
        adminMgr.assignActor(user2, role);

        List<Actor> actors = reviewMgr.assignedActors(role);
        assertTrue(actors.contains(user1));
        assertTrue(actors.contains(user2));
    }

    // ------------------------------------------------------------------
    // assignedRoles
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testAssignedRoles() throws RBACException {
        assertEquals(0, reviewMgr.assignedRoles(RBACEntityConstants.NOT_EXIST_USER).size());

        Role role1 = adminMgr.addRole(Role.builder().roleName("rev-assignedRoles-r1").build());
        Role role2 = adminMgr.addRole(Role.builder().roleName("rev-assignedRoles-r2").build());
        User user = newUser("rev-assignedRoles-user");
        adminMgr.assignActor(user, role1);
        adminMgr.assignActor(user, role2);

        List<Role> roles = reviewMgr.assignedRoles(user);
        assertTrue(roles.contains(role1));
        assertTrue(roles.contains(role2));
    }

    // ------------------------------------------------------------------
    // hasRole / hasPermission
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testHasRoleAndHasPermission() throws RBACException {
        User user = newUser("rev-hasRoleUser");
        Role role = adminMgr.addRole(Role.builder().roleName("rev-hasRoleTarget").build());
        Permission perm = adminMgr.addPermission(Permission.builder().permName("rev-hasPermTarget").build());
        adminMgr.grantPermission(perm, role);

        assertFalse(reviewMgr.hasRole(user, role));
        assertFalse(reviewMgr.hasPermission(user, perm));

        adminMgr.assignActor(user, role);

        assertTrue(reviewMgr.hasRole(user, role));
        assertTrue(reviewMgr.hasPermission(user, perm));

        adminMgr.deassignActor(user, role);

        assertFalse(reviewMgr.hasRole(user, role));
        assertFalse(reviewMgr.hasPermission(user, perm));
    }

    // ------------------------------------------------------------------
    // managedRoles / managedPermissions / hasManagedRole / hasManagedPermission
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testManagedRolesAndHasManagedRoleAndPermission() throws RBACException {
        User user = newUser("rev-managedRoles-user");
        Role role = adminMgr.addRole(Role.builder().roleName("rev-managedRole-target").build());
        Permission perm = adminMgr.addPermission(Permission.builder().permName("rev-managedPerm-target").build());
        adminMgr.grantPermission(perm, role);

        assertTrue(reviewMgr.managedRoles(user).isEmpty());
        assertTrue(reviewMgr.managedPermissions(user).isEmpty());
        assertFalse(reviewMgr.hasManagedRole(user, role));
        assertFalse(reviewMgr.hasManagedPermission(user, perm));

        adminMgr.assignManagedRole(user, role);

        List<Role> mRoles = reviewMgr.managedRoles(user);
        assertEquals(1, mRoles.size());
        assertEquals("rev-managedRole-target", mRoles.get(0).getRoleName());

        List<Permission> mPerms = reviewMgr.managedPermissions(user);
        assertEquals(1, mPerms.size());
        assertEquals("rev-managedPerm-target", mPerms.get(0).getPermName());

        assertTrue(reviewMgr.hasManagedRole(user, role));
        assertTrue(reviewMgr.hasManagedPermission(user, perm));
        // Backward-compatible deprecated methods
        assertTrue(reviewMgr.hasManageRole(user, role));
        assertTrue(reviewMgr.hasManagePermission(user, perm));

        adminMgr.deassignManagedRole(user, role);
        assertTrue(reviewMgr.managedRoles(user).isEmpty());
        assertTrue(reviewMgr.managedPermissions(user).isEmpty());
        assertFalse(reviewMgr.hasManagedRole(user, role));
        assertFalse(reviewMgr.hasManagedPermission(user, perm));
    }
}
