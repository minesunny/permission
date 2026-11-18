package com.minesunny.permission.core.manager.impl;

import com.minesunny.jpa.MineSpecification;
import com.minesunny.jpa.entity.SdtNode;
import com.minesunny.jpa.exception.ServiceException;
import com.minesunny.jpa.manager.MapMgr;
import com.minesunny.jpa.manager.SdtNodeMgr;
import com.minesunny.permission.core.entity.actor.OrgUnit;
import com.minesunny.permission.core.entity.actor.Post;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.sdt.OrgSdtNode;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.OrgUnitMgr;
import com.minesunny.permission.core.repository.ActorRepository;
import com.minesunny.permission.core.repository.OrgRepository;
import com.minesunny.permission.core.repository.PostRepository;
import com.minesunny.permission.core.util.NaturalKeys;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.minesunny.permission.core.type.MapTableConstants.*;

@Service
@RequiredArgsConstructor
public class OrgUnitMgrImpl implements OrgUnitMgr {
    private final SdtNodeMgr nodeMgr;
    private final OrgRepository orgRepository;
    private final MapMgr mapMgr;
    private final PostRepository postRepository;
    private final ActorRepository actorRepository;

    @Override
    public @NotNull Post addPost(@NotNull Post post) throws RBACException {
        NaturalKeys.requirePostName(post);
        if (postRepository.findByPostName(post.getPostName()).isPresent()) {
            throw new ValidationException("[addPost]:  The post entity [postName: %s ] already exists".formatted(post.getPostName()));
        }
        Post newPost = new Post();
        BeanUtils.copyProperties(post, newPost, "users");
        return postRepository.saveAndFlush(newPost);
    }

    @Override
    public @NotNull Post updatePost(@NotNull String postName, @NotNull Post post) throws RBACException {
        Post storePost = postRepository.findByPostName(postName)
                .orElseThrow(() -> new ValidationException("[updatePost]:    The post entity [postName: %s] does not exist".formatted(postName)));
        String newPostName = post.getPostName();
        if (newPostName != null && !newPostName.equals(postName)
                && postRepository.findByPostName(newPostName).isPresent()) {
            throw new ValidationException("[updatePost]:    The post entity [postName: %s ] already exists".formatted(newPostName));
        }
        BeanUtils.copyProperties(post, storePost, "id", "users");
        return postRepository.saveAndFlush(storePost);
    }

    @Override
    public void deletePost(@NotNull Post post) throws RBACException {
        NaturalKeys.requirePostName(post);
        Post storePost = postRepository.findByPostName(post.getPostName())
                .orElseThrow(() -> new ValidationException("[deletePost]:    The post entity [postName: %s] does not exist".formatted(post.getPostName())));
        Map<String, List<Long>> map = new HashMap<>();
        map.put("post_id", List.of(storePost.getId()));
        try {
            mapMgr.delete(POST_USER, map);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
        postRepository.delete(storePost);
    }

    @Override
    public Optional<Post> readPost(@NotNull Post post) throws RBACException {
        NaturalKeys.requirePostName(post);
        return postRepository.findByPostName(post.getPostName());
    }

    @Override
    public @NotNull List<Post> readPosts(@NotNull Post post) throws RBACException {
        MineSpecification<Post> search = post.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readPosts]:    The post searchCondition is required and cannot be null");
        }
        return postRepository.findAll(search);
    }

    @Override
    public void bindPost(@NotNull Post post, @NotNull User user) throws RBACException {
        NaturalKeys.requirePostName(post);
        Long postId = postRepository.findByPostName(post.getPostName())
                .orElseThrow(() -> new ValidationException("[bindPost]:    The post[postName: %s] does not exist".formatted(post.getPostName())))
                .getId();
        NaturalKeys.requireActorName(user);
        Long userId = actorRepository.findByActorName(user.getActorName())
                .orElseThrow(() -> new ValidationException("[bindPost]:    The user does not exist"))
                .getId();

        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(userId));
        map.put("post_id", List.of(postId));
        try {
            if (mapMgr.exist(POST_USER, map)) {
                throw new ValidationException("[bindPost]:    The user[id: %s] already has post[id: %s]".formatted(userId, postId));
            }
            mapMgr.save(POST_USER, map);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    @Override
    public void unbindPost(@NotNull Post post, @NotNull User user) throws RBACException {
        NaturalKeys.requirePostName(post);
        Long postId = postRepository.findByPostName(post.getPostName())
                .orElseThrow(() -> new ValidationException("[unbindPost]:    The post[postName: %s] does not exist".formatted(post.getPostName())))
                .getId();
        NaturalKeys.requireActorName(user);
        Long userId = actorRepository.findByActorName(user.getActorName())
                .orElseThrow(() -> new ValidationException("[unbindPost]:    The user does not exist"))
                .getId();

        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(userId));
        map.put("post_id", List.of(postId));
        try {
            if (!mapMgr.exist(POST_USER, map)) {
                throw new ValidationException("[unbindPost]:    The user[id: %s] has no post[id: %s]".formatted(userId, postId));
            }
            mapMgr.delete(POST_USER, map);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    @Override
    public @NotNull OrgUnit addOrgUnit(@NotNull OrgUnit orgUnit) throws RBACException {
        NaturalKeys.requireOrgName(orgUnit);
        if (orgRepository.findByOrgName(orgUnit.getOrgName()).isPresent()) {
            throw new ValidationException("[addOrgUnit]:  The OrgUnit entity [orgName: %s ] already exists".formatted(orgUnit.getOrgName()));
        }
        OrgUnit newOrgUnit = new OrgUnit();
        BeanUtils.copyProperties(orgUnit, newOrgUnit, "managerUsers", "users");
        return orgRepository.saveAndFlush(newOrgUnit);
    }

    @Override
    public @NotNull OrgUnit updateOrgUnit(@NotNull String orgName, @NotNull OrgUnit orgUnit) throws RBACException {
        OrgUnit storeOrgUnit = orgRepository.findByOrgName(orgName)
                .orElseThrow(() -> new ValidationException("[updateOrgUnit]:    The orgUnit entity [orgName: %s] does not exist".formatted(orgName)));
        String newOrgName = orgUnit.getOrgName();
        if (newOrgName != null && !newOrgName.equals(orgName)
                && orgRepository.findByOrgName(newOrgName).isPresent()) {
            throw new ValidationException("[updateOrgUnit]:    The orgUnit entity [orgName: %s ] already exists".formatted(newOrgName));
        }
        BeanUtils.copyProperties(orgUnit, storeOrgUnit, "id", "users", "managerUsers");
        return orgRepository.saveAndFlush(storeOrgUnit);
    }

    @Override
    public void deleteOrgUnit(@NotNull OrgUnit orgUnit) throws RBACException {
        NaturalKeys.requireOrgName(orgUnit);
        OrgUnit storeOrgUnit = orgRepository.findByOrgName(orgUnit.getOrgName())
                .orElseThrow(() -> new ValidationException("[deleteOrgUnit]:    The orgUnit entity [orgName: %s] does not exist".formatted(orgUnit.getOrgName())));
        Long orgId = storeOrgUnit.getId();
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(orgId));
        try {
            mapMgr.delete(ORG_UNIT_USER, map);
            mapMgr.delete(USER_ORG_UNIT, map);
            // 删除组织架构树
            OrgSdtNode orgSdtNode = OrgSdtNode.builder().nodeId(orgId).build();
            orgSdtNode.andEq("nodeId");
            List<SdtNode> sdtNodes = nodeMgr.readSdtNodes(orgSdtNode);
            for (SdtNode sdtNode : sdtNodes) {
                nodeMgr.unbindSdtNode(sdtNode);
            }
        } catch (ServiceException e) {
            throw rethrow(e);
        }
        orgRepository.delete(storeOrgUnit);
    }

    @Override
    public Optional<OrgUnit> readOrgUnit(@NotNull OrgUnit orgUnit) throws RBACException {
        NaturalKeys.requireOrgName(orgUnit);
        return orgRepository.findByOrgName(orgUnit.getOrgName());
    }

    @Override
    public @NotNull List<OrgUnit> readOrgUnits(@NotNull OrgUnit orgUnit) throws RBACException {
        MineSpecification<OrgUnit> search = orgUnit.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readOrgUnits]:    The orgUnit searchCondition is required and cannot be null");
        }
        return orgRepository.findAll(search);
    }

    @Override
    public void bindOrgUnit(@NotNull User user, @NotNull OrgUnit orgUnit) throws RBACException {
        NaturalKeys.requireActorName(user);
        Long userId = actorRepository.findByActorName(user.getActorName())
                .orElseThrow(() -> new ValidationException("[bindOrgUnit]:    The user does not exist"))
                .getId();
        NaturalKeys.requireOrgName(orgUnit);
        Long orgId = orgRepository.findByOrgName(orgUnit.getOrgName())
                .orElseThrow(() -> new ValidationException("[bindOrgUnit]:    The orgUnit[orgName: %s] does not exist".formatted(orgUnit.getOrgName())))
                .getId();

        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(orgId));
        map.put("user_id", List.of(userId));
        try {
            if (mapMgr.exist(ORG_UNIT_USER, map)) {
                throw new ValidationException("[bindOrgUnit]:    The user has bind with orgUnit");
            }
            mapMgr.save(ORG_UNIT_USER, map);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    @Override
    public void unbindOrgUnit(@NotNull User user, @NotNull OrgUnit orgUnit) throws RBACException {
        NaturalKeys.requireActorName(user);
        Long userId = actorRepository.findByActorName(user.getActorName())
                .orElseThrow(() -> new ValidationException("[unbindOrgUnit]:    The user does not exist"))
                .getId();
        NaturalKeys.requireOrgName(orgUnit);
        Long orgId = orgRepository.findByOrgName(orgUnit.getOrgName())
                .orElseThrow(() -> new ValidationException("[unbindOrgUnit]:    The orgUnit[orgName: %s] does not exist".formatted(orgUnit.getOrgName())))
                .getId();

        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(orgId));
        map.put("user_id", List.of(userId));
        try {
            if (!mapMgr.exist(ORG_UNIT_USER, map)) {
                throw new ValidationException("[unbindOrgUnit]:    The user has not bind with orgUnit");
            }
            mapMgr.delete(ORG_UNIT_USER, map);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    @Override
    public void bindOrgUnitNode(@NotNull OrgSdtNode childOrgNode, @NotNull OrgSdtNode parentOrgNode) throws RBACException {
        if (Objects.isNull(childOrgNode.getNodeId())) {
            throw new ValidationException("[bindOrgUnitNode]:    The childOrgNode entity's nodeId is required and cannot be null");
        }
        if (orgRepository.findById(childOrgNode.getNodeId()).isEmpty()) {
            throw new ValidationException("[bindOrgUnitNode]:    The childOrgNode entity does not exist");
        }
        if (Objects.isNull(parentOrgNode.getNodeId())) {
            throw new ValidationException("[bindOrgUnitNode]:    The parentOrgNode entity's nodeId is required and cannot be null");
        }
        if (orgRepository.findById(parentOrgNode.getNodeId()).isEmpty()) {
            throw new ValidationException("[bindOrgUnitNode]:    The parentOrgNode entity does not exist");
        }
        try {
            if (Objects.isNull(childOrgNode.getId())) {
                childOrgNode.setId(nodeMgr.addSdtNode(childOrgNode).getId());
            }
            if (Objects.isNull(parentOrgNode.getId())) {
                parentOrgNode.setId(nodeMgr.addSdtNode(parentOrgNode).getId());
            }
            nodeMgr.bindSdtNode(childOrgNode, parentOrgNode);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    @Override
    public void unbindOrgUnitNode(@NotNull OrgSdtNode childOrgNode) throws RBACException {
        if (Objects.isNull(childOrgNode.getId())) {
            throw new ValidationException("[unbindOrgUnitNode]:    The childOrgNode entity does not exist");
        }
        try {
            Optional<SdtNode> optionalChildSdtNode = nodeMgr.readSdtNode(childOrgNode);
            if (optionalChildSdtNode.isEmpty()) {
                throw new ValidationException("[unbindOrgUnitNode]:    The childOrgNode entity does not exist");
            }
            nodeMgr.unbindSdtNode(optionalChildSdtNode.get());
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    @Override
    public void bindManagerUser(@NotNull User user, @NotNull OrgUnit orgUnit) throws RBACException {
        NaturalKeys.requireActorName(user);
        Long userId = actorRepository.findByActorName(user.getActorName())
                .orElseThrow(() -> new ValidationException("[bindManagerUser]:    The user does not exist"))
                .getId();
        NaturalKeys.requireOrgName(orgUnit);
        Long orgId = orgRepository.findByOrgName(orgUnit.getOrgName())
                .orElseThrow(() -> new ValidationException("[bindManagerUser]:    The orgUnit[orgName: %s] does not exist".formatted(orgUnit.getOrgName())))
                .getId();

        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(userId));
        map.put("user_id", List.of(orgId));
        try {
            if (mapMgr.exist(USER_ORG_UNIT, map)) {
                throw new ValidationException("[bindManagerUser]:    The manager user has bind with orgUnit");
            }
            mapMgr.save(USER_ORG_UNIT, map);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    @Override
    public void unbindManagerUser(@NotNull User user, @NotNull OrgUnit orgUnit) throws RBACException {
        NaturalKeys.requireActorName(user);
        Long userId = actorRepository.findByActorName(user.getActorName())
                .orElseThrow(() -> new ValidationException("[unbindManagerUser]:    The user does not exist"))
                .getId();
        NaturalKeys.requireOrgName(orgUnit);
        Long orgId = orgRepository.findByOrgName(orgUnit.getOrgName())
                .orElseThrow(() -> new ValidationException("[unbindManagerUser]:    The orgUnit[orgName: %s] does not exist".formatted(orgUnit.getOrgName())))
                .getId();

        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(userId));
        map.put("user_id", List.of(orgId));
        try {
            if (!mapMgr.exist(USER_ORG_UNIT, map)) {
                throw new ValidationException("[unbindManagerUser]:    The manager user has not bind with orgUnit");
            }
            mapMgr.delete(USER_ORG_UNIT, map);
        } catch (ServiceException e) {
            throw rethrow(e);
        }
    }

    private RBACException rethrow(ServiceException e) {
        if (e instanceof RBACException rbacException) {
            return rbacException;
        }
        return new RBACException(e.getMessage(), e);
    }
}
