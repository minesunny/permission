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
import com.minesunny.permission.core.entity.example.e1.ExampleResource1;
import com.minesunny.permission.core.entity.example.e2.ExampleResource2;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.entity.perm.Resource;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.type.RBACEntityConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Commit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection =
        EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class,BaseJpaAutoConfiguration.class})
class AdminMgrImplTest {
    @Autowired
    private AdminMgr adminMgr;
    @Autowired
    private ReviewMgr reviewMgr;

    @Test
    @Commit
    void testAddUser() throws ServiceException {

        User user = new User();
        User addUser = adminMgr.addUser(user);
        Assertions.assertNotNull(addUser);
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.addUser(addUser));
    }


    @Test
    @Commit
    void testUpdateUser() throws ServiceException {

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updateUser(RBACEntityConstants.USER));

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updateUser(RBACEntityConstants.NOT_EXIST_USER));
        User addUser = adminMgr.addUser(new User());
        addUser.setDescription("update user");
        Assertions.assertEquals(adminMgr.updateUser(addUser).getDescription(), "update user");
    }


    @Test
    @Commit
    void testDeleteUser() throws ServiceException {
        User addUser = adminMgr.addUser(new User());
        adminMgr.deleteUser(addUser);
        Assertions.assertTrue(reviewMgr.readUser(addUser).isEmpty());

    }


    @Test
    @Commit
    void testAddRole() throws ServiceException {

        Role role = new Role();
        Role addRole = adminMgr.addRole(role);
        Assertions.assertNotNull(addRole);
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.addRole(addRole));
    }


    @Test
    @Commit
    void testUpdateRole() throws ServiceException {

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updateRole(RBACEntityConstants.ROLE));

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updateRole(RBACEntityConstants.NOT_EXIST_ROLE));
        Role addRole = adminMgr.addRole(new Role());
        addRole.setDescription("update role");
        Assertions.assertEquals(adminMgr.updateRole(addRole).getDescription(), "update role");
    }


    @Test
    @Commit
    void testDeleteRole() throws ServiceException {

        Role addRole = adminMgr.addRole(new Role());
        adminMgr.deleteRole(addRole);
        Assertions.assertTrue(reviewMgr.readRole(addRole).isEmpty());

    }

    @Test
    @Commit
    void testAssignUser() throws ServiceException {

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.assignUser(RBACEntityConstants.NOT_EXIST_USER, RBACEntityConstants.NOT_EXIST_ROLE));

        User user = adminMgr.addUser(User.builder().build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.assignUser(user, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().build());
        Assertions.assertDoesNotThrow(() -> adminMgr.assignUser(user, role));
        List<Role> roles = reviewMgr.assignedRoles(user);
        Assertions.assertTrue(roles.contains(role));

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.assignUser(user, role));

    }


    @Test
    @Commit
    void testDeassignUser() throws ServiceException {

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.deassignUser(RBACEntityConstants.NOT_EXIST_USER, RBACEntityConstants.NOT_EXIST_ROLE));

        User user = adminMgr.addUser(User.builder().build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.deassignUser(user, RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(Role.builder().build());
        Assertions.assertDoesNotThrow(() -> adminMgr.assignUser(user, role));
        List<Role> roles = reviewMgr.assignedRoles(user);
        Assertions.assertTrue(roles.contains(role));

        Assertions.assertDoesNotThrow(() -> adminMgr.deassignUser(user, role));
        roles = reviewMgr.assignedRoles(user);
        Assertions.assertFalse(roles.contains(role));


        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.deassignUser(user, role));

    }


    @Test
    @Commit
    void testAddPermission() throws ServiceException {

        Resource resource1 = ExampleResource1.builder().message("this is a ExampleResource1").build();
        Permission permission1 = adminMgr.addPermission(Permission.builder().resources(Set.of(resource1)).build());
        Assertions.assertTrue(reviewMgr.readPermission(permission1).isPresent());

        Resource resource2 = ExampleResource2.builder().message("this is a ExampleResource2").build();
        Permission permission2 = adminMgr.addPermission(Permission.builder().resources(Set.of(resource2)).build());
        Assertions.assertTrue(reviewMgr.readPermission(permission2).isPresent());

        Assertions.assertThrows(ValidationException.class, () -> adminMgr.addPermission(permission1));
        Assertions.assertThrows(ValidationException.class, () -> adminMgr.addPermission(permission2));


        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.addPermission(Permission.builder().resources(Set.of(resource1, resource2)).build()));
    }

    @Test
    @Commit
    void updatePermission() throws ServiceException {

        Assertions.assertThrows(ValidationException.class, () -> adminMgr.updatePermission(RBACEntityConstants.PERMISSION));

        Assertions.assertThrows(ValidationException.class, () -> adminMgr.updatePermission(RBACEntityConstants.NOT_EXIST_PERMISSION));

        Resource resource1 = ExampleResource1.builder().message("this is a ExampleResource1").build();
        Set<Resource> objects = new HashSet<>();
        objects.add(resource1);
        Permission permission1 = adminMgr.addPermission(Permission.builder().resources(objects).build());
        Resource resource2 = ExampleResource2.builder().message("this is a ExampleResource2").build();
        permission1.getResources().add(resource2);

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.updatePermission(permission1));

        permission1.getResources().remove(resource2);
        Resource resource11 = ExampleResource1.builder().message("this is a ExampleResource1").build();

        permission1.getResources().add(resource11);
        Assertions.assertDoesNotThrow(
                () -> adminMgr.updatePermission(permission1));
    }

    @Test
    @Commit
    void deletePermission() throws ServiceException {
        Assertions.assertThrows(ValidationException.class, () -> adminMgr.deletePermission(RBACEntityConstants.PERMISSION));

        Resource resource1 = ExampleResource1.builder().message("this is a ExampleResource1").build();
        Set<Resource> objects = new HashSet<>();
        objects.add(resource1);
        Permission permission1 = adminMgr.addPermission(Permission.builder().resources(objects).build());
        Assertions.assertTrue(reviewMgr.readPermission(permission1).isPresent());

        adminMgr.deletePermission(permission1);
        Assertions.assertFalse(reviewMgr.readPermission(permission1).isPresent());

    }

    @Test
    @Commit
    void testGrantPermission() throws ServiceException {



        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.grantPermission(RBACEntityConstants.NOT_EXIST_PERMISSION,
                        RBACEntityConstants.NOT_EXIST_ROLE));
        Resource resource1 = ExampleResource1.builder().message("this is a ExampleResource1").build();
        Set<Resource> objects = new HashSet<>();
        objects.add(resource1);
        Permission permission1 = adminMgr.addPermission(Permission.builder().resources(objects).build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.grantPermission(permission1,
                        RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(new Role());
        Assertions.assertDoesNotThrow(() -> adminMgr.grantPermission(permission1,
                role));
        List<Permission> permissions = reviewMgr.rolePermissions(role);
        Assertions.assertTrue(permissions.contains(permission1));


        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.grantPermission(permission1,
                        role));
    }


    @Test
    @Commit
    void testRevokePermission() throws ServiceException {



        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.revokePermission(RBACEntityConstants.NOT_EXIST_PERMISSION,
                        RBACEntityConstants.NOT_EXIST_ROLE));
        Resource resource1 = ExampleResource1.builder().message("this is a ExampleResource1").build();
        Set<Resource> objects = new HashSet<>();
        objects.add(resource1);
        Permission permission1 = adminMgr.addPermission(Permission.builder().resources(objects).build());
        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.revokePermission(permission1,
                        RBACEntityConstants.NOT_EXIST_ROLE));

        Role role = adminMgr.addRole(new Role());
        Assertions.assertDoesNotThrow(() -> adminMgr.grantPermission(permission1,
                role));
        List<Permission> permissions = reviewMgr.rolePermissions(role);
        Assertions.assertTrue(permissions.contains(permission1));
        Assertions.assertDoesNotThrow(() -> adminMgr.revokePermission(permission1,
                role));
        permissions = reviewMgr.rolePermissions(role);
        Assertions.assertFalse(permissions.contains(permission1));

        Assertions.assertThrows(ValidationException.class,
                () -> adminMgr.revokePermission(permission1,
                        role));
    }


}