package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
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

    @Test
    @Commit
    void testReadUser() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readUser(RBACEntityConstants.USER));
        User user = User.builder().username("rev-readUser").description("test readUser").build();

        Optional<User> readUser = reviewMgr.readUser(adminMgr.addUser(user));
        assertTrue(readUser.isPresent());
        assertEquals("test readUser", readUser.get().getDescription());

        user.andEq("description");
        List<User> users = reviewMgr.readUsers(user);
        assertFalse(users.isEmpty());
        assertEquals("test readUser", users.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadUsers() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readUsers(RBACEntityConstants.USER));
        assertDoesNotThrow(() -> adminMgr.addUser(User.builder().username("rev-readUsers-1").description("abcdefg").build()));
        assertDoesNotThrow(() -> adminMgr.addUser(User.builder().username("rev-readUsers-2").description("abc").build()));

        List<User> users = reviewMgr.readUsers(User.builder().description("abc").build().like("description", true));
        assertEquals(2, users.size());
    }

    @Test
    @Commit
    void testReadRole() throws ServiceException {
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
    void testReadRoles() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readRoles(RBACEntityConstants.ROLE));
        assertDoesNotThrow(() -> adminMgr.addRole(Role.builder().roleName("rev-readRoles-1").description("abcdefg").build()));
        assertDoesNotThrow(() -> adminMgr.addRole(Role.builder().roleName("rev-readRoles-2").description("abc").build()));

        List<Role> roles = reviewMgr.readRoles(Role.builder().description("abc").build().like("description", true));
        assertEquals(2, roles.size());
    }

    @Test
    @Commit
    void testReadPermission() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readPermission(RBACEntityConstants.PERMISSION));
        Permission permission = Permission.builder().permName("rev-readPerm").description("test testReadPermission").build();

        Optional<Permission> readPermission = reviewMgr.readPermission(adminMgr.addPermission(permission));
        assertTrue(readPermission.isPresent());
        assertEquals(readPermission.get().getDescription(), "test testReadPermission");

        permission.andEq("description");
        List<Permission> permissions = reviewMgr.readPermissions(permission);
        assertFalse(permissions.isEmpty());
        assertEquals("test testReadPermission", permissions.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadPermissions() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readPermission(RBACEntityConstants.PERMISSION));
        assertDoesNotThrow(() -> adminMgr.addPermission(Permission.builder().permName("rev-readPerms-1").description("abcdefg").build()));
        assertDoesNotThrow(() -> adminMgr.addPermission(Permission.builder().permName("rev-readPerms-2").description("abc").build()));

        List<Permission> permissions = reviewMgr.readPermissions(Permission.builder().description("abc").build().like("description", true));
        assertEquals(2, permissions.size());
    }

    @Test
    @Commit
    void testRolePermissions() throws ServiceException {
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

    @Test
    @Commit
    void testUserPermissions() throws ServiceException {
        assertEquals(reviewMgr.userPermissions(RBACEntityConstants.NOT_EXIST_USER).size(), 0);

        Role role = adminMgr.addRole(Role.builder().roleName("rev-userPerms-role").build());
        User user = adminMgr.addUser(User.builder().username("rev-userPerms-user").build());
        Permission p1 = adminMgr.addPermission(Permission.builder().permName("rev-userPerms-p1").build());
        Permission p2 = adminMgr.addPermission(Permission.builder().permName("rev-userPerms-p2").build());
        adminMgr.grantPermission(p1, role);
        adminMgr.grantPermission(p2, role);
        adminMgr.assignUser(user, role);

        List<Permission> permissions = reviewMgr.userPermissions(user);

        assertTrue(permissions.contains(p1));
        assertTrue(permissions.contains(p2));
    }

    @Test
    @Commit
    void testAssignedUsers() throws ServiceException {
        assertEquals(reviewMgr.assignedUsers(RBACEntityConstants.NOT_EXIST_ROLE).size(), 0);

        Role role = adminMgr.addRole(Role.builder().roleName("rev-assignedUsers-role").build());
        User user1 = adminMgr.addUser(User.builder().username("rev-assignedUsers-u1").build());
        User user2 = adminMgr.addUser(User.builder().username("rev-assignedUsers-u2").build());

        adminMgr.assignUser(user1, role);
        adminMgr.assignUser(user2, role);

        List<User> users = reviewMgr.assignedUsers(role);
        assertTrue(users.contains(user1));
        assertTrue(users.contains(user2));
    }

    @Test
    @Commit
    void testAssignedRoles() throws ServiceException {
        assertEquals(reviewMgr.assignedRoles(RBACEntityConstants.NOT_EXIST_USER).size(), 0);

        Role role1 = adminMgr.addRole(Role.builder().roleName("rev-assignedRoles-r1").build());
        Role role2 = adminMgr.addRole(Role.builder().roleName("rev-assignedRoles-r2").build());
        User user = adminMgr.addUser(User.builder().username("rev-assignedRoles-user").build());

        adminMgr.assignUser(user, role1);
        adminMgr.assignUser(user, role2);

        List<Role> roles = reviewMgr.assignedRoles(user);
        assertTrue(roles.contains(role1));
        assertTrue(roles.contains(role2));
    }
}
