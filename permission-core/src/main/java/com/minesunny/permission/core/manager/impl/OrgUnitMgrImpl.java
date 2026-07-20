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
import org.springframework.util.StringUtils;

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
        requirePostName(post);
        if (postRepository.findByPostName(post.getPostName()).isPresent()) {
            throw new ValidationException("[addPost]:  The post entity [postName: %s ] already exists".formatted(post.getPostName()));
        }
        Post newPost = new Post();
        BeanUtils.copyProperties(post, newPost, "users");
        return postRepository.saveAndFlush(newPost);
    }

    @Override
    public @NotNull Post updatePost(@NotNull Post post) throws ServiceException {
        requirePostName(post);
        Post storePost = postRepository.findByPostName(post.getPostName())
                .orElseThrow(() -> new ValidationException("[updatePost]:    The post entity [postName: %s] does not exist".formatted(post.getPostName())));
        BeanUtils.copyProperties(post, storePost, "id", "postName", "users");
        return postRepository.saveAndFlush(storePost);
    }

    @Override
    public void deletePost(@NotNull Post post) throws ServiceException {
        requirePostName(post);
        Post storePost = postRepository.findByPostName(post.getPostName())
                .orElseThrow(() -> new ValidationException("[deletePost]:    The post entity [postName: %s] does not exist".formatted(post.getPostName())));
        Map<String, List<Long>> map = new HashMap<>();
        map.put("post_id", List.of(storePost.getId()));
        mapMgr.delete(POST_USER, map);
        postRepository.delete(storePost);
    }

    @Override
    public Optional<Post> readPost(@NotNull Post post) throws ServiceException {
        requirePostName(post);
        return postRepository.findByPostName(post.getPostName());
    }

    @Override
    public @NotNull List<Post> readPosts(@NotNull Post post) throws ServiceException {
        MineSpecification<Post> search = post.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readPosts]:    The post searchCondition is required and cannot be null");
        }
        return postRepository.findAll(search);
    }

    @Override
    public void bindPost(@NotNull Post post, @NotNull User user) throws ServiceException {
        Long postId = resolvePostId(post, "bindPost");
        Long userId = resolveUserId(user, "bindPost");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(userId));
        map.put("post_id", List.of(postId));
        if (mapMgr.exist(POST_USR, map)) {
            throw new ValidationException("[bindPost]:    The user[id: %s] already has post[id: %s]".formatted(userId, postId));
        }
        mapMgr.save(POST_USER, map);
    }

    @Override
    public void unbindPost(@NotNull Post post, @NotNull User user) throws ServiceException {
        Long postId = resolvePostId(post, "unbindPost");
        Long userId = resolveUserId(user, "unbindPost");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("user_id", List.of(userId));
        map.put("post_id", List.of(postId));
        if (!mapMgr.exist(POST_USR, map)) {
            throw new ValidationException("[unbindPost]:    The user[id: %s] has no post[id: %s]".formatted(userId, postId));
        }
        mapMgr.delete(POST_USER, map);
    }

    @Override
    public @NotNull OrgUnit addOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        requireOrgName(orgUnit);
        if (orgRepository.findByOrgName(orgUnit.getOrgName()).isPresent()) {
            throw new ValidationException("[addOrgUnit]:  The OrgUnit entity [orgName: %s ] already exists".formatted(orgUnit.getOrgName()));
        }
        OrgUnit newOrgUnit = new OrgUnit();
        BeanUtils.copyProperties(orgUnit, newOrgUnit, "managerUsers", "users");
        return orgRepository.saveAndFlush(newOrgUnit);
    }

    @Override
    public @NotNull OrgUnit updateOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        requireOrgName(orgUnit);
        OrgUnit storeOrgUnit = orgRepository.findByOrgName(orgUnit.getOrgName())
                .orElseThrow(() -> new ValidationException("[updateOrgUnit]:    The orgUnit entity [orgName: %s] does not exist".formatted(orgUnit.getOrgName())));
        BeanUtils.copyProperties(orgUnit, storeOrgUnit, "id", "orgName", "users", "managerUsers");
        return orgRepository.saveAndFlush(storeOrgUnit);
    }

    @Override
    public void deleteOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        requireOrgName(orgUnit);
        OrgUnit storeOrgUnit = orgRepository.findByOrgName(orgUnit.getOrgName())
                .orElseThrow(() -> new ValidationException("[deleteOrgUnit]:    The orgUnit entity [orgName: %s] does not exist".formatted(orgUnit.getOrgName())));
        Long orgId = storeOrgUnit.getId();
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(orgId));
        mapMgr.delete(ORG_UNIT_USER, map);
        mapMgr.delete(USER_ORG_UNIT, map);
        // 删除组织架构树
        OrgSdtNode orgSdtNode = OrgSdtNode.builder().nodeId(orgId).build();
        orgSdtNode.andEq("nodeId");
        List<SdtNode> sdtNodes = nodeMgr.readSdtNodes(orgSdtNode);
        for (SdtNode sdtNode : sdtNodes) {
            nodeMgr.unbindSdtNode(sdtNode);
        }
        orgRepository.delete(storeOrgUnit);
    }

    @Override
    public Optional<OrgUnit> readOrgUnit(@NotNull OrgUnit orgUnit) throws ServiceException {
        requireOrgName(orgUnit);
        return orgRepository.findByOrgName(orgUnit.getOrgName());
    }

    @Override
    public @NotNull List<OrgUnit> readOrgUnits(@NotNull OrgUnit orgUnit) throws ServiceException {
        MineSpecification<OrgUnit> search = orgUnit.specification();
        if (Objects.isNull(search)) {
            throw new ValidationException("[readOrgUnits]:    The orgUnit searchCondition is required and cannot be null");
        }
        return orgRepository.findAll(search);
    }

    @Override
    public void bindOrgUnit(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        long[] ids = resolveUserOrg(user, orgUnit, "bindOrgUnit");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(ids[1]));
        map.put("user_id", List.of(ids[0]));
        if (mapMgr.exist(ORG_UNIT_USER, map)) {
            throw new ValidationException("[bindOrgUnit]:    The user has bind with orgUnit");
        }
        mapMgr.save(ORG_UNIT_USER, map);
    }

    @Override
    public void unbindOrgUnit(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        long[] ids = resolveUserOrg(user, orgUnit, "unbindOrgUnit");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(ids[1]));
        map.put("user_id", List.of(ids[0]));
        if (!mapMgr.exist(ORG_UNIT_USER, map)) {
            throw new ValidationException("[unbindOrgUnit]:    The user has not bind with orgUnit");
        }
        mapMgr.delete(ORG_UNIT_USER, map);
    }

    @Override
    public void bindOrgUnitNode(@NotNull OrgSdtNode childOrgNode, @NotNull OrgSdtNode parentOrgNode) throws ServiceException {
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
            throw new ValidationException("[unbindOrgUnitNode]:    The childOrgNode entity does not exist");
        }
        Optional<SdtNode> optionalChildSdtNode = nodeMgr.readSdtNode(childOrgNode);
        if (optionalChildSdtNode.isEmpty()) {
            throw new ValidationException("[unbindOrgUnitNode]:    The childOrgNode entity does not exist");
        }
        nodeMgr.unbindSdtNode(optionalChildSdtNode.get());
    }

    @Override
    public void bindManagerUser(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        long[] ids = resolveUserOrg(user, orgUnit, "bindManagerUser");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(ids[1]));
        map.put("user_id", List.of(ids[0]));
        if (mapMgr.exist(USER_ORG_UNIT, map)) {
            throw new ValidationException("[bindManagerUser]:    The manager user has bind with orgUnit");
        }
        mapMgr.save(USER_ORG_UNIT, map);
    }

    @Override
    public void unbindManagerUser(@NotNull User user, @NotNull OrgUnit orgUnit) throws ServiceException {
        long[] ids = resolveUserOrg(user, orgUnit, "unbindManagerUser");
        Map<String, List<Long>> map = new HashMap<>();
        map.put("org_id", List.of(ids[1]));
        map.put("user_id", List.of(ids[0]));
        if (!mapMgr.exist(USER_ORG_UNIT, map)) {
            throw new ValidationException("[unbindManagerUser]:    The manager user has not bind with orgUnit");
        }
        mapMgr.delete(USER_ORG_UNIT, map);
    }

    private void requirePostName(Post post) throws ValidationException {
        if (!StringUtils.hasText(post.getPostName())) {
            throw new ValidationException("The post entity's postName is required and cannot be null");
        }
    }

    private void requireOrgName(OrgUnit orgUnit) throws ValidationException {
        if (!StringUtils.hasText(orgUnit.getOrgName())) {
            throw new ValidationException("The orgUnit entity's orgName is required and cannot be null");
        }
    }

    private Long resolvePostId(Post post, String op) throws ValidationException {
        requirePostName(post);
        return postRepository.findByPostName(post.getPostName())
                .orElseThrow(() -> new ValidationException("[%s]:    The post[postName: %s] does not exist".formatted(op, post.getPostName())))
                .getId();
    }

    private Long resolveUserId(User user, String op) throws ServiceException {
        return reviewMgr.readUser(user)
                .orElseThrow(() -> new ValidationException("[%s]:    The user does not exist".formatted(op)))
                .getId();
    }

    private long[] resolveUserOrg(User user, OrgUnit orgUnit, String op) throws ServiceException {
        Long userId = resolveUserId(user, op);
        requireOrgName(orgUnit);
        Long orgId = orgRepository.findByOrgName(orgUnit.getOrgName())
                .orElseThrow(() -> new ValidationException("[%s]:    The orgUnit[orgName: %s] does not exist".formatted(op, orgUnit.getOrgName())))
                .getId();
        return new long[]{userId, orgId};
    }
}
