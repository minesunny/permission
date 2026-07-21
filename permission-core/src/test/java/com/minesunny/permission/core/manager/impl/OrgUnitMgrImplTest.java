package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.actor.Post;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.OrgUnitMgr;
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

import java.util.Optional;

@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection =
        EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class OrgUnitMgrImplTest {

    @Autowired
    OrgUnitMgr orgUnitMgr;
    @Autowired
    AdminMgr adminMgr;
    @Autowired
    EntityManager entityManager;

    @Test
    void addPost() throws ServiceException {
        Post post = Post.builder().postName("org-addPost").build();
        Post savedPost = orgUnitMgr.addPost(post);
        Assertions.assertTrue(orgUnitMgr.readPost(savedPost).isPresent());

        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.addPost(savedPost));
    }

    @Test
    void updatePost() throws ServiceException {
        Assertions.assertThrows(ValidationException.class,
                () -> orgUnitMgr.updatePost("notExistPost", Post.builder().build()));

        Post post = Post.builder().postName("org-updatePost").build();
        Post savedPost = orgUnitMgr.addPost(post);

        savedPost.setDescription("update post desc");
        orgUnitMgr.updatePost("org-updatePost", savedPost);
        Optional<Post> optionalPost = orgUnitMgr.readPost(Post.builder().postName("org-updatePost").build());
        Assertions.assertTrue(optionalPost.isPresent());
        Assertions.assertEquals("update post desc", optionalPost.get().getDescription());

        entityManager.detach(savedPost);
        savedPost.setPostName("org-updatePost-renamed");
        orgUnitMgr.updatePost("org-updatePost", savedPost);
        Assertions.assertTrue(orgUnitMgr.readPost(Post.builder().postName("org-updatePost-renamed").build()).isPresent());
        Assertions.assertTrue(orgUnitMgr.readPost(Post.builder().postName("org-updatePost").build()).isEmpty());
    }

    @Test
    void deletePost() throws ServiceException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.deletePost(Post.builder().build()));

        Post post = Post.builder().postName("org-deletePost").build();
        Post savedPost = orgUnitMgr.addPost(post);
        orgUnitMgr.deletePost(savedPost);
        Assertions.assertTrue(orgUnitMgr.readPost(savedPost).isEmpty());
    }

    @Test
    void readPost() {
    }

    @Test
    void readPosts() {
    }

    @Test
    void bindPost() throws ServiceException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(RBACEntityConstants.POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.NOT_EXIST_USER));

        Post post = Post.builder().postName("org-bindPost").build();
        Post savedPost = orgUnitMgr.addPost(post);
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(savedPost, RBACEntityConstants.NOT_EXIST_USER));
        User user = User.builder().username("org-bindPost-user").build();
        User savedUser = adminMgr.addUser(user);

        Assertions.assertDoesNotThrow(() -> orgUnitMgr.bindPost(savedPost, savedUser));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(savedPost, savedUser));
        Optional<Post> optionalPost = orgUnitMgr.readPost(savedPost);
        Assertions.assertTrue(optionalPost.isPresent());

        Assertions.assertTrue(optionalPost.get().getUsers().contains(savedUser));
    }

    @Test
    void unbindPost() throws ServiceException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(RBACEntityConstants.POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.NOT_EXIST_USER));

        Post post = Post.builder().postName("org-unbindPost").build();
        Post savedPost = orgUnitMgr.addPost(post);
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(savedPost, RBACEntityConstants.NOT_EXIST_USER));
        User user = User.builder().username("org-unbindPost-user").build();
        User savedUser = adminMgr.addUser(user);
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(savedPost, savedUser));
        Assertions.assertDoesNotThrow(() -> orgUnitMgr.bindPost(savedPost, savedUser));
        Optional<Post> optionalPost = orgUnitMgr.readPost(savedPost);
        Assertions.assertTrue(optionalPost.isPresent());
        Assertions.assertTrue(optionalPost.get().getUsers().contains(savedUser));

        Assertions.assertDoesNotThrow(() -> orgUnitMgr.unbindPost(savedPost, savedUser));

        Optional<Post> optionalPost2 = orgUnitMgr.readPost(savedPost);
        Assertions.assertTrue(optionalPost2.isPresent());
        Assertions.assertFalse(optionalPost2.get().getUsers().contains(savedUser));
    }

    @Test
    void addOrgUnit() {
    }

    @Test
    void updateOrgUnit() {
    }

    @Test
    void deleteOrgUnit() {
    }

    @Test
    void readOrgUnit() {
    }

    @Test
    void readOrgUnits() {
    }

    @Test
    void bindOrgUnit() {
    }

    @Test
    void unbindOrgUnit() {
    }

    @Test
    void testBindOrgUnit() {
    }

    @Test
    void testUnbindOrgUnit() {
    }
}
