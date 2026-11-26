package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.*;
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
        // Empty actor (no actorName) → requireActorName throws RBACValidationException
        assertThrows(RBACValidationException.class, () -> reviewMgr.readActor(RBACEntityConstants.ACTOR));

        User user = User.builder()
                .actorName("rev-readActor")
                .username("rev-readActor")
                .description("test readActor")
                .build();
        Actor saved = adminMgr.addActor(user);

        Actor readActor = reviewMgr.readActor(saved);
        assertNotNull(readActor);
        // JPA JOINED inheritance returns the concrete subtype; cast is safe
        assertEquals("test readActor", ((User) readActor).getDescription());

        // findActor & not found tests
        assertNotNull(reviewMgr.findActor(saved));
        assertNotNull(reviewMgr.findActor("rev-readActor"));
        assertNull(reviewMgr.findActor("not-exist-actor-xyz"));
        assertNull(reviewMgr.findActor(User.builder().actorName("not-exist-actor-xyz").build()));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readActor("not-exist-actor-xyz"));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readActor(User.builder().actorName("not-exist-actor-xyz").build()));
    }

    @Test
    @Commit
    void testReadActors() throws RBACException {
        // No search condition → returns empty list
        assertTrue(reviewMgr.readActors(RBACEntityConstants.ACTOR).isEmpty());

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
        assertThrows(RBACValidationException.class, () -> reviewMgr.readRole(RBACEntityConstants.ROLE));
        Role role = Role.builder().roleName("rev-readRole").description("test testReadRole").build();

        Role readRole = reviewMgr.readRole(adminMgr.addRole(role));
        assertNotNull(readRole);
        assertEquals("test testReadRole", readRole.getDescription());

        // findRole & not found tests
        assertNotNull(reviewMgr.findRole(role));
        assertNotNull(reviewMgr.findRole("rev-readRole"));
        assertNull(reviewMgr.findRole("not-exist-role-xyz"));
        assertNull(reviewMgr.findRole(Role.builder().roleName("not-exist-role-xyz").build()));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readRole("not-exist-role-xyz"));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readRole(Role.builder().roleName("not-exist-role-xyz").build()));

        role.andEq("description");
        List<Role> roles = reviewMgr.readRoles(role);
        assertFalse(roles.isEmpty());
        assertEquals("test testReadRole", roles.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadRoles() throws RBACException {
        assertTrue(reviewMgr.readRoles(RBACEntityConstants.ROLE).isEmpty());
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
        assertThrows(RBACValidationException.class, () -> reviewMgr.readPermission(RBACEntityConstants.PERMISSION));
        Permission permission = Permission.builder().permName("rev-readPerm").description("test testReadPermission").build();

        Permission readPermission = reviewMgr.readPermission(adminMgr.addPermission(permission));
        assertNotNull(readPermission);
        assertEquals("test testReadPermission", readPermission.getDescription());

        // findPermission & not found tests
        assertNotNull(reviewMgr.findPermission(permission));
        assertNotNull(reviewMgr.findPermission("rev-readPerm"));
        assertNull(reviewMgr.findPermission("not-exist-perm-xyz"));
        assertNull(reviewMgr.findPermission(Permission.builder().permName("not-exist-perm-xyz").build()));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readPermission("not-exist-perm-xyz"));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readPermission(Permission.builder().permName("not-exist-perm-xyz").build()));

        permission.andEq("description");
        List<Permission> permissions = reviewMgr.readPermissions(permission);
        assertFalse(permissions.isEmpty());
        assertEquals("test testReadPermission", permissions.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadPermissions() throws RBACException {
        assertTrue(reviewMgr.readPermissions(RBACEntityConstants.PERMISSION).isEmpty());
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
    // readUser / readUsers
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testReadUserAndUsers() throws RBACException {
        // 空对象：单个查询抛异常，复数查询返回空列表
        assertThrows(RBACValidationException.class, () -> reviewMgr.readUser(RBACEntityConstants.USER));
        assertTrue(reviewMgr.readUsers(RBACEntityConstants.USER).isEmpty());

        User user1 = adminMgr.addUser(User.builder().username("rev-u1").email("u1@test.com").build());
        User user2 = adminMgr.addUser(User.builder().username("rev-u2").email("u2@test.com").build());

        // 按用户名查询
        User readByName = reviewMgr.readUser("rev-u1");
        assertNotNull(readByName);
        assertEquals("u1@test.com", readByName.getEmail());

        // 按用户实体查询
        User readByEntity = reviewMgr.readUser(user2);
        assertNotNull(readByEntity);
        assertEquals("u2@test.com", readByEntity.getEmail());

        // findUser & not found tests
        assertNotNull(reviewMgr.findUser("rev-u1"));
        assertNotNull(reviewMgr.findUser(user2));
        assertNull(reviewMgr.findUser("not-exist-user-xyz"));
        assertNull(reviewMgr.findUser(User.builder().username("not-exist-user-xyz").build()));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readUser("not-exist-user-xyz"));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readUser(User.builder().username("not-exist-user-xyz").build()));

        // 条件列表查询
        List<User> list = reviewMgr.readUsers(User.builder().username("rev-u").build().like("username", true));
        assertEquals(2, list.size());
    }

    // ------------------------------------------------------------------
    // permissionRoles / permissionActors
    // ------------------------------------------------------------------
    @Test
    @Commit
    void testPermissionRolesAndActors() throws RBACException {
        Role role = adminMgr.addRole(Role.builder().roleName("rev-permRoles-role").build());
        Permission perm = adminMgr.addPermission(Permission.builder().permName("rev-permRoles-p1").build());
        User user = newUser("rev-permRoles-user");

        adminMgr.grantPermission(perm, role);
        adminMgr.assignActor(user, role);

        // 使用仅包含 permName 的非托管 Permission 对象查询角色
        Permission unmanagedPerm = Permission.builder().permName("rev-permRoles-p1").build();
        List<Role> roles = reviewMgr.permissionRoles(unmanagedPerm);
        assertEquals(1, roles.size());
        assertEquals("rev-permRoles-role", roles.get(0).getRoleName());

        // 查询拥有该权限的所有参与者
        List<Actor> actors = reviewMgr.permissionActors(unmanagedPerm);
        assertEquals(1, actors.size());
        assertEquals("rev-permRoles-user", actors.get(0).getActorName());
    }
}
