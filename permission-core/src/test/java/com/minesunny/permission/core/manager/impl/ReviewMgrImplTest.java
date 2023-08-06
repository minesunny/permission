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

import com.minesunny.jpa.ServiceException;
import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.manager.impl.AdminMgrImpl;
import com.minesunny.permission.core.manager.impl.ReviewMgrImpl;
import com.minesunny.permission.core.type.RBACEntityConstants;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.Commit;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection =
        EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class,BaseJpaAutoConfiguration.class})
class ReviewMgrImplTest {
    @Autowired
    private AdminMgr adminMgr;
    @Autowired
    private ReviewMgr reviewMgr;

    @Test
    @Commit
    void testReadUser() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readUser(RBACEntityConstants.USER));
        // read byId;
        User user = User.builder().description("test readUser").build();

        Optional<User> readUser = reviewMgr.readUser(adminMgr.addUser(user));
        assertTrue(readUser.isPresent());
        assertEquals("test readUser", readUser.get().getDescription());

        // read by specification;
        user.andEq("description");
        List<User> users = reviewMgr.readUsers(user);
        assertFalse(users.isEmpty());
        assertEquals("test readUser", users.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadUsers() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readUsers(RBACEntityConstants.USER));
        assertDoesNotThrow(() -> adminMgr.addUser(User.builder().description("abcdefg").build()));

        assertDoesNotThrow(() -> adminMgr.addUser(User.builder().description("abc").build()));

        List<User> users = reviewMgr.readUsers(User.builder().description("abc").build().like("description", true));

        assertEquals(2, users.size());
    }


    @Test
    @Commit
    void testReadRole() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readRole(RBACEntityConstants.ROLE));
        // read byId;
        Role role = Role.builder().description("test testReadRole").build();

        Optional<Role> readRole = reviewMgr.readRole(adminMgr.addRole(role));
        assertTrue(readRole.isPresent());
        assertEquals("test testReadRole", readRole.get().getDescription());

        // read by specification;
        role.andEq("description");
        List<Role> roles = reviewMgr.readRoles(role);
        assertFalse(roles.isEmpty());
        assertEquals("test testReadRole", roles.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadRoles() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readRoles(RBACEntityConstants.ROLE));
        assertDoesNotThrow(() -> adminMgr.addRole(Role.builder().description("abcdefg").build()));

        assertDoesNotThrow(() -> adminMgr.addRole(Role.builder().description("abc").build()));

        List<Role> roles = reviewMgr.readRoles(Role.builder().description("abc").build().like("description", true));

        assertEquals(2, roles.size());
    }


    @Test
    @Commit
    void testReadPermission() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readPermission(RBACEntityConstants.PERMISSION));
        // read byId;
        Permission permission = Permission.builder().description("test testReadPermission").build();

        Optional<Permission> readPermission = reviewMgr.readPermission(adminMgr.addPermission(permission));
        assertTrue(readPermission.isPresent());
        assertEquals(readPermission.get().getDescription(), "test testReadPermission");

        // read by specification;
        permission.andEq("description");
        List<Permission> permissions = reviewMgr.readPermissions(permission);
        assertFalse(permissions.isEmpty());
        assertEquals("test testReadPermission", permissions.get(0).getDescription());
    }

    @Test
    @Commit
    void testReadPermissions() throws ServiceException {
        assertThrows(ValidationException.class, () -> reviewMgr.readPermission(RBACEntityConstants.PERMISSION));
        assertDoesNotThrow(() -> adminMgr.addPermission(Permission.builder().description("abcdefg").build()));

        assertDoesNotThrow(() -> adminMgr.addPermission(Permission.builder().description("abc").build()));

        List<Permission> permissions = reviewMgr.readPermissions(Permission.builder().description("abc").build().like(
                "description",
                true));

        assertEquals(2, permissions.size());
    }


    @Test
    @Commit
    void testRolePermissions() throws ServiceException {

        assertEquals(0, reviewMgr.rolePermissions(RBACEntityConstants.NOT_EXIST_ROLE).size());


        Role role = adminMgr.addRole(new Role());
        Permission p1 = adminMgr.addPermission(new Permission());
        Permission p2 = adminMgr.addPermission(new Permission());
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

        Role role = adminMgr.addRole(new Role());
        User user = adminMgr.addUser(new User());
        Permission p1 = adminMgr.addPermission(new Permission());
        Permission p2 = adminMgr.addPermission(new Permission());
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

        Role role = adminMgr.addRole(new Role());
        User user1 = adminMgr.addUser(new User());
        User user2 = adminMgr.addUser(new User());

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

        Role role1 = adminMgr.addRole(new Role());
        Role role2 = adminMgr.addRole(new Role());
        User user = adminMgr.addUser(new User());

        adminMgr.assignUser(user, role1);
        adminMgr.assignUser(user, role2);

        List<Role> roles = reviewMgr.assignedRoles(user);
        assertTrue(roles.contains(role1));
        assertTrue(roles.contains(role2));
    }
}
