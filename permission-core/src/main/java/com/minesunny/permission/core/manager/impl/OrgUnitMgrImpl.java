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
import com.minesunny.permission.core.exception.ValidationException;
import com.minesunny.permission.core.manager.OrgUnitMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.repository.OrgRepository;
import com.minesunny.permission.core.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.AopContext;
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
    private final ReviewMgr reviewMgr;
    private final PostRepository postRepository;

    @Override
    public @NotNull Post addPost(@NotNull Post post) throws ServiceException {
        if (Objects.nonNull(post.getId())) {
            if (postRepository.findById(post.getId()).isPresent()) {
                throw new ValidationException(("[addPost]:  The post entity [id: %s ] is already  exist").formatted(post.getId()));
            }
        }
        Post newPost = new Post();
        BeanUtils.copyProperties(post, newPost, "users");
        return postRepository.saveAndFlush(newPost);
    }

    @Override
    public @NotNull Post updatePost(@NotNull Post post) throws ServiceException {
        if (Objects.isNull(post.getId())) {
            throw new ValidationException("[updatePost]:    The post  entity's id is required and cannot be null");
        }
        Optional<Post> findPost = postRepository.findById(post.getId());
        if (findPost.isEmpty()) {
            throw new ValidationException("[updatePost]:    The post entity [id: %s] is not exist".formatted(post.getId()));
        }

        Post storePost = findPost.get();
        BeanUtils.copyProperties(post, storePost, "id", "users");
        return postRepository.saveAndFlush(storePost);
    }

    @Override
    public void deletePost(@NotNull Post post) throws ServiceException {
        if (Objects.isNull(post.getId())) {
            throw new ValidationException("[deletePost]:    The post entity's id is required and cannot be null");
        }

        Map<String, List<Long>> map = new HashMap<>();
        map.put("post_id", List.of(post.getId()));
        mapMgr.delete(POST_USER, map);
        postRepository.delete(post);
    }

    @Override
    public Optional<Post> readPost(@NotNull Post post) throws ServiceException {
        if (Objects.isNull(post.getId())) {
            throw new ValidationException("[readPost]:    The post entity's id is required and cannot be null");
        }
        return postRepository.findById(post.getId());
    }

    @Override
    public @NotNull List<Post> readPosts(@NotNull Post post) throws ServiceException {
        MineSpecification<Post> search = post.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readUsers]:    The RBAC user searchCondition is required and cannot be null");
        }
        return postRepository.findAll(search);
    }

    @Override
    public void bindPost(@NotNull Post post, @NotNull User user) throws ServiceException {
        if (Objects.isNull(post.getId())) {
            throw new ValidationException("[bindPost]:    The  post entity's id is required and cannot null");
        }
        if (Objects.isNull(user.getId())) {
            throw new ValidationException("[bindPost]:    The RBAC user entity's id is required and cannot null");
        }

        if (!postRepository.existsById(post.getId())) {
            throw new ValidationException("[bindPost]:    The post[id: %s] entity is not exist".formatted(user.getId()));
        }

        if (reviewMgr.readUser(user).isEmpty()) {
            throw new ValidationException(("[bindPost]:    The RBAC user[id: %s] entity is required and cannot null").formatted(user.getId()));
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(user.getId()));
        map.put("post_id", List.of(post.getId()));
        if (mapMgr.exist(POST_USR, map)) {
            throw new ValidationException(String.format("[bindPost]:    The RBAC user [id: %s] entity has " +
                    "post [id: %s] entity", user.getId(), post.getId()));
        }
        mapMgr.save(POST_USER, map);
    }

    @Override
    public void unbindPost(@NotNull Post post, @NotNull User user) throws ServiceException {
        if (Objects.isNull(post.getId())) {
            throw new ValidationException("[unbindPost]:    The  post entity's id is required and cannot null");
        }
        if (Objects.isNull(user.getId())) {
            throw new ValidationException("[unbindPost]:    The RBAC user entity's id is required and cannot null");
        }

        if (!postRepository.existsById(post.getId())) {
            throw new ValidationException("[unbindPost]:    The post[id: %s] entity is not exist".formatted(user.getId()));
        }

        if (reviewMgr.readUser(user).isEmpty()) {
            throw new ValidationException(("[unbindPost]:    The RBAC user[id: %s] entity is required and cannot null").formatted(user.getId()));
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(user.getId()));
        map.put("post_id", List.of(post.getId()));
        if (!mapMgr.exist(POST_USR, map)) {
            throw new ValidationException(String.format("[unbindPost]:    The RBAC user [id: %s] entity has " +
                    "removed post [id: %s] entity", user.getId(), post.getId()));
        }
        mapMgr.delete(POST_USER, map);
    }

    @Override
    public @NotNull OrgUnit addOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        if (Objects.nonNull(orgUnit.getId())) {
            if (orgRepository.findById(orgUnit.getId()).isPresent()) {
                throw new ValidationException(("[addOrgUnit]:  The OrgUnit entity [id: %s ] is already  exist").formatted(orgUnit.getId()));
            }
        }
        OrgUnit newOrgUnit = new OrgUnit();
        BeanUtils.copyProperties(orgUnit, newOrgUnit, "managerUsers", "users");
        return orgRepository.saveAndFlush(newOrgUnit);
    }

    @Override
    public @NotNull OrgUnit updateOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        if (Objects.isNull(orgUnit.getId())) {
            throw new ValidationException("[updateOrgUnit]:    The orgUnit  entity's id is required and cannot be null");
        }
        Optional<OrgUnit> findOrgUnit = orgRepository.findById(orgUnit.getId());
        if (findOrgUnit.isEmpty()) {
            throw new ValidationException("[updateOrgUnit]:    The orgUnit entity [id: %s] is not exist".formatted(orgUnit.getId()));
        }

        OrgUnit storeOrgUnit = findOrgUnit.get();
        BeanUtils.copyProperties(orgUnit, storeOrgUnit, "id", "users", "managerUsers");
        return orgRepository.saveAndFlush(storeOrgUnit);
    }

    @Override
    public void deleteOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        if (Objects.isNull(orgUnit.getId())) {
            throw new ValidationException("[deleteOrgUnit]:    The orgUnit entity's id is required and cannot be null");
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(orgUnit.getId()));
        mapMgr.delete(ORG_UNIT_USER, map);
        mapMgr.delete(USER_ORG_UNIT, map);
        // 删除组织架构树
        OrgSdtNode orgSdtNode = OrgSdtNode.builder().nodeId(orgUnit.getId()).build();
        orgSdtNode.andEq("nodeId");
        List<SdtNode> sdtNodes = nodeMgr.readSdtNodes(orgSdtNode);
        for (SdtNode sdtNode : sdtNodes) {
            nodeMgr.unbindSdtNode(sdtNode);
        }
        orgRepository.deleteById(orgUnit.getId());
    }

    @Override
    public Optional<OrgUnit> readOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        if (Objects.isNull(orgUnit.getId())) {
            throw new ValidationException("[readOrgUnit]:    The orgUnit entity's id is required and cannot be null");
        }
        return orgRepository.findById(orgUnit.getId());
    }

    @Override
    public @NotNull List<OrgUnit> readOrgUnits(@NotNull OrgUnit orgUnit) throws ServiceException {
        MineSpecification<OrgUnit> search = orgUnit.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readOrgUnits]:    The  orgUnit searchCondition is required and cannot be null");
        }
        return orgRepository.findAll(search);
    }

    @Override
    public void bindOrgUnit(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        if (Objects.isNull(user.getId())) {
            throw new ValidationException("[bindOrgUnit]:    The user entity's id is required and cannot be null");
        }
        if (Objects.isNull(orgUnit.getId())) {
            throw new ValidationException("[bindOrgUnit]:    The orgUnit entity's id is required and cannot be null");
        }
        Optional<OrgUnit> findOrgUnit = orgRepository.findById(orgUnit.getId());
        if (findOrgUnit.isEmpty()) {
            throw new ValidationException("[bindOrgUnit]:    The orgUnit entity's id is not exist");
        }
        Optional<User> findUser = reviewMgr.readUser(user);
        if (findUser.isEmpty()) {
            throw new ValidationException("[bindOrgUnit]:    The user entity's id is not exist");
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(orgUnit.getId()));
        map.put("user_id", List.of(orgUnit.getId()));
        if (mapMgr.exist(ORG_UNIT_USER, map)) {
            throw new ValidationException("[bindOrgUnit]:    The user has bind with orgUnit");
        }
        mapMgr.save(ORG_UNIT_USER, map);
    }

    @Override
    public void unbindOrgUnit(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        if (Objects.isNull(user.getId())) {
            throw new ValidationException("[unbindOrgUnit]:    The user entity's id is required and cannot be null");
        }
        if (Objects.isNull(orgUnit.getId())) {
            throw new ValidationException("[unbindOrgUnit]:    The orgUnit entity's id is required and cannot be null");
        }
        Optional<OrgUnit> findOrgUnit = orgRepository.findById(orgUnit.getId());
        if (findOrgUnit.isEmpty()) {
            throw new ValidationException("[unbindOrgUnit]:    The orgUnit entity's id is not exist");
        }
        Optional<User> findUser = reviewMgr.readUser(user);
        if (findUser.isEmpty()) {
            throw new ValidationException("[unbindOrgUnit]:    The user entity's id is not exist");
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(orgUnit.getId()));
        map.put("user_id", List.of(orgUnit.getId()));
        if (!mapMgr.exist(ORG_UNIT_USER, map)) {
            throw new ValidationException("[unbindOrgUnit]:    The user has unbind from orgUnit");
        }
        mapMgr.delete(ORG_UNIT_USER, map);
    }

    @Override
    public void bindOrgUnitNode(@NotNull OrgSdtNode childOrgNode, @NotNull OrgSdtNode parentOrgNode) throws ServiceException {
        if (Objects.isNull(childOrgNode.getNodeId())) {
            throw new ValidationException("[bindOrgUnit]:    The childOrgNode entity's nodeId is required and cannot be null");
        }

        OrgUnitMgr orgUnitMgr = (OrgUnitMgr) AopContext.currentProxy();
        if (orgUnitMgr.readOrgUnit(OrgUnit.builder().id(childOrgNode.getNodeId()).build()).isEmpty()) {
            throw new ValidationException("[bindOrgUnit]:    The childOrgNode entity is not exist");
        }
        if (Objects.isNull(parentOrgNode.getNodeId())) {
            throw new ValidationException("[bindOrgUnit]:    The parentOrgNode entity's nodeId is required and cannot be null");
        }
        if (orgUnitMgr.readOrgUnit(OrgUnit.builder().id(parentOrgNode.getNodeId()).build()).isEmpty()) {
            throw new ValidationException("[bindOrgUnit]:    The parentOrgNode entity is not exist");
        }
        if (Objects.isNull(childOrgNode.getId())) {
            childOrgNode.setId(nodeMgr.addSdtNode(childOrgNode).getId());
        }
        if (Objects.isNull(parentOrgNode.getId())) {
            parentOrgNode.setId(nodeMgr.addSdtNode(parentOrgNode).getId());
        }
        nodeMgr.bindSdtNode(childOrgNode, parentOrgNode);
    }

    @Override
    public void unbindOrgUnitNode(@NotNull OrgSdtNode childOrgNode) throws ServiceException {
        if (Objects.isNull(childOrgNode.getId())) {
            throw new ValidationException("[unbindOrgUnit]:    The childOrgNode entity is not exist");
        }
        Optional<SdtNode> optionalChildSdtNode = nodeMgr.readSdtNode(childOrgNode);
        if (optionalChildSdtNode.isEmpty()) {
            throw new ValidationException("[unbindOrgUnit]:    The childOrgNode entity is not exist");
        }
        nodeMgr.unbindSdtNode(optionalChildSdtNode.get());
    }

    @Override
    public void bindManagerUser(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        if (Objects.isNull(user.getId())) {
            throw new ValidationException("[bindManagerUser]:    The user entity's id is required and cannot be null");
        }
        if (Objects.isNull(orgUnit.getId())) {
            throw new ValidationException("[bindManagerUser]:    The orgUnit entity's id is required and cannot be null");
        }
        Optional<OrgUnit> findOrgUnit = orgRepository.findById(orgUnit.getId());
        if (findOrgUnit.isEmpty()) {
            throw new ValidationException("[bindManagerUser]:    The orgUnit entity's id is not exist");
        }
        Optional<User> findUser = reviewMgr.readUser(user);
        if (findUser.isEmpty()) {
            throw new ValidationException("[bindManagerUser]:    The user entity's id is not exist");
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(orgUnit.getId()));
        map.put("user_id", List.of(orgUnit.getId()));
        if (mapMgr.exist(USER_ORG_UNIT, map)) {
            throw new ValidationException("[bindManagerUser]:    The manager user has bind with orgUnit");
        }
        mapMgr.save(USER_ORG_UNIT, map);
    }

    @Override
    public void unbindManagerUser(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        if (Objects.isNull(user.getId())) {
            throw new ValidationException("[unbindManagerUser]:    The user entity's id is required and cannot be null");
        }
        if (Objects.isNull(orgUnit.getId())) {
            throw new ValidationException("[unbindManagerUser]:    The orgUnit entity's id is required and cannot be null");
        }
        Optional<OrgUnit> findOrgUnit = orgRepository.findById(orgUnit.getId());
        if (findOrgUnit.isEmpty()) {
            throw new ValidationException("[unbindManagerUser]:    The orgUnit entity's id is not exist");
        }
        Optional<User> findUser = reviewMgr.readUser(user);
        if (findUser.isEmpty()) {
            throw new ValidationException("[unbindManagerUser]:    The user entity's id is not exist");
        }
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(orgUnit.getId()));
        map.put("user_id", List.of(orgUnit.getId()));
        if (!mapMgr.exist(USER_ORG_UNIT, map)) {
            throw new ValidationException("[unbindManagerUser]:    The manager user has unbind from orgUnit");
        }
        mapMgr.delete(USER_ORG_UNIT, map);
    }
}
