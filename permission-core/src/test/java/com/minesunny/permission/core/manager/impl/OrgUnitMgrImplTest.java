package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.actor.OrgUnit;
import com.minesunny.permission.core.entity.actor.Post;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.sdt.OrgSdtNode;
import com.minesunny.permission.core.exception.RBACException;
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

import java.util.List;
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

    private Post newPost(String name) {
        return Post.builder().actorName(name).postName(name).build();
    }

    // ------------------------------------------------------------------
    // Post Tests
    // ------------------------------------------------------------------

    @Test
    void addPost() throws RBACException {
        Post post = newPost("org-addPost");
        Post savedPost = orgUnitMgr.addPost(post);
        Assertions.assertTrue(orgUnitMgr.readPost(savedPost).isPresent());

        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.addPost(savedPost));
    }

    @Test
    void updatePost() throws RBACException {
        Assertions.assertThrows(ValidationException.class,
                () -> orgUnitMgr.updatePost("notExistPost", newPost("dummy")));

        Post savedPost = orgUnitMgr.addPost(newPost("org-updatePost"));

        savedPost.setDescription("update post desc");
        orgUnitMgr.updatePost("org-updatePost", savedPost);
        Optional<Post> optionalPost = orgUnitMgr.readPost(newPost("org-updatePost"));
        Assertions.assertTrue(optionalPost.isPresent());
        Assertions.assertEquals("update post desc", optionalPost.get().getDescription());

        entityManager.detach(savedPost);
        savedPost.setPostName("org-updatePost-renamed");
        orgUnitMgr.updatePost("org-updatePost", savedPost);
        Assertions.assertTrue(orgUnitMgr.readPost(newPost("org-updatePost-renamed")).isPresent());
        Assertions.assertTrue(orgUnitMgr.readPost(newPost("org-updatePost")).isEmpty());
    }

    @Test
    void deletePost() throws RBACException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.deletePost(newPost("notExist")));

        Post savedPost = orgUnitMgr.addPost(newPost("org-deletePost"));
        orgUnitMgr.deletePost(savedPost);
        Assertions.assertTrue(orgUnitMgr.readPost(savedPost).isEmpty());
    }

    @Test
    void readPosts() throws RBACException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.readPosts(new Post()));
        orgUnitMgr.addPost(Post.builder().actorName("org-readPosts-1").postName("org-readPosts-1").description("dept1").build());
        orgUnitMgr.addPost(Post.builder().actorName("org-readPosts-2").postName("org-readPosts-2").description("dept2").build());

        List<Post> posts = orgUnitMgr.readPosts(Post.builder().description("dept").build().like("description", true));
        Assertions.assertEquals(2, posts.size());
    }

    @Test
    void bindPost() throws RBACException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(RBACEntityConstants.POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.NOT_EXIST_USER));

        Post savedPost = orgUnitMgr.addPost(newPost("org-bindPost"));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(savedPost, RBACEntityConstants.NOT_EXIST_USER));
        User user = User.builder().actorName("org-bindPost-user").username("org-bindPost-user").build();
        User savedUser = adminMgr.addActor(user);

        Assertions.assertDoesNotThrow(() -> orgUnitMgr.bindPost(savedPost, savedUser));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindPost(savedPost, savedUser));
        Optional<Post> optionalPost = orgUnitMgr.readPost(savedPost);
        Assertions.assertTrue(optionalPost.isPresent());
        Assertions.assertTrue(optionalPost.get().getUsers().contains(savedUser));
    }

    @Test
    void unbindPost() throws RBACException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(RBACEntityConstants.POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.USER));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(RBACEntityConstants.NOT_EXIST_POST, RBACEntityConstants.NOT_EXIST_USER));

        Post savedPost = orgUnitMgr.addPost(newPost("org-unbindPost"));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindPost(savedPost, RBACEntityConstants.NOT_EXIST_USER));
        User user = User.builder().actorName("org-unbindPost-user").username("org-unbindPost-user").build();
        User savedUser = adminMgr.addActor(user);
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

    // ------------------------------------------------------------------
    // OrgUnit Tests
    // ------------------------------------------------------------------

    @Test
    void addOrgUnit() throws RBACException {
        OrgUnit org = OrgUnit.builder().actorName("org-addOrg").orgName("org-addOrg").build();
        OrgUnit saved = orgUnitMgr.addOrgUnit(org);
        Assertions.assertNotNull(saved);
        Assertions.assertTrue(orgUnitMgr.readOrgUnit(saved).isPresent());
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.addOrgUnit(saved));
    }

    @Test
    void updateOrgUnit() throws RBACException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.updateOrgUnit("notExistOrg", OrgUnit.builder().build()));

        OrgUnit org = OrgUnit.builder().actorName("org-updateOrg").orgName("org-updateOrg").build();
        OrgUnit saved = orgUnitMgr.addOrgUnit(org);
        saved.setDescription("updated org desc");
        orgUnitMgr.updateOrgUnit("org-updateOrg", saved);

        Optional<OrgUnit> read = orgUnitMgr.readOrgUnit(OrgUnit.builder().orgName("org-updateOrg").build());
        Assertions.assertTrue(read.isPresent());
        Assertions.assertEquals("updated org desc", read.get().getDescription());
    }

    @Test
    void deleteOrgUnit() throws RBACException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.deleteOrgUnit(OrgUnit.builder().orgName("notExistOrg").build()));
        OrgUnit org = OrgUnit.builder().actorName("org-deleteOrg").orgName("org-deleteOrg").build();
        OrgUnit saved = orgUnitMgr.addOrgUnit(org);
        orgUnitMgr.deleteOrgUnit(saved);
        Assertions.assertTrue(orgUnitMgr.readOrgUnit(saved).isEmpty());
    }

    @Test
    void readOrgUnits() throws RBACException {
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.readOrgUnits(new OrgUnit()));
        orgUnitMgr.addOrgUnit(OrgUnit.builder().actorName("org-read1").orgName("org-read1").description("dept1").build());
        orgUnitMgr.addOrgUnit(OrgUnit.builder().actorName("org-read2").orgName("org-read2").description("dept2").build());

        List<OrgUnit> list = orgUnitMgr.readOrgUnits(OrgUnit.builder().description("dept").build().like("description", true));
        Assertions.assertEquals(2, list.size());
    }

    @Test
    void bindAndUnbindOrgUnit() throws RBACException {
        User user = adminMgr.addActor(User.builder().actorName("org-user1").username("org-user1").build());
        OrgUnit org = orgUnitMgr.addOrgUnit(OrgUnit.builder().actorName("org-unit1").orgName("org-unit1").build());

        Assertions.assertDoesNotThrow(() -> orgUnitMgr.bindOrgUnit(user, org));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindOrgUnit(user, org));

        Assertions.assertDoesNotThrow(() -> orgUnitMgr.unbindOrgUnit(user, org));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindOrgUnit(user, org));
    }

    @Test
    void bindAndUnbindManagerUser() throws RBACException {
        User user = adminMgr.addActor(User.builder().actorName("org-mgr-user1").username("org-mgr-user1").build());
        OrgUnit org = orgUnitMgr.addOrgUnit(OrgUnit.builder().actorName("org-mgr-unit1").orgName("org-mgr-unit1").build());

        Assertions.assertDoesNotThrow(() -> orgUnitMgr.bindManagerUser(user, org));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.bindManagerUser(user, org));

        Assertions.assertDoesNotThrow(() -> orgUnitMgr.unbindManagerUser(user, org));
        Assertions.assertThrows(ValidationException.class, () -> orgUnitMgr.unbindManagerUser(user, org));
    }

    @Test
    void bindAndUnbindOrgUnitNode() throws RBACException {
        OrgUnit childOrg = orgUnitMgr.addOrgUnit(OrgUnit.builder().actorName("org-child").orgName("org-child").build());
        OrgUnit parentOrg = orgUnitMgr.addOrgUnit(OrgUnit.builder().actorName("org-parent").orgName("org-parent").build());

        OrgSdtNode childNode = OrgSdtNode.builder().nodeId(childOrg.getId()).build();
        OrgSdtNode parentNode = OrgSdtNode.builder().nodeId(parentOrg.getId()).build();

        Assertions.assertDoesNotThrow(() -> orgUnitMgr.bindOrgUnitNode(childNode, parentNode));
        Assertions.assertDoesNotThrow(() -> orgUnitMgr.unbindOrgUnitNode(childNode));
    }
}
