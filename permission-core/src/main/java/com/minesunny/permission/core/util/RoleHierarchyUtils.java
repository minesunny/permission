package com.minesunny.permission.core.util;

import com.minesunny.permission.core.exception.RBACTopologyException;
import com.minesunny.permission.core.i18n.RBACMessageKeys;

import java.util.*;

/**
 * 角色继承层次（Role Hierarchy）拓扑图分析与有向无环图（DAG）成环检测纯函数工具类。
 * <p>
 * 严格遵循架构规范：纯函数式工具，无状态，不包含 Spring/JPA 依赖。
 */
public final class RoleHierarchyUtils {

    private RoleHierarchyUtils() {
    }

    /**
     * 校验若在现有角色继承偏序关系中新增边 $(parent \rightarrow child)$，是否会产生环路死循环。
     * <p>
     * <b>判定规则</b>：
     * 若从 $child$ 出发向下能遍历到达 $parent$（即 $child \succeq parent$ 已间接或直接成立），
     * 则再增加 $parent \succeq child$ 必将形成有向环路，违反偏序关系的反对称性。
     *
     * @param parentRoleId      拟新增的上位父角色 ID
     * @param childRoleId       拟新增的下位子角色 ID
     * @param parentRoleName    上位父角色名称（用于异常日志）
     * @param childRoleName     下位子角色名称（用于异常日志）
     * @param descendantIdsOfChild $child$ 及其所有已有后继子角色 ID 集合
     * @throws RBACTopologyException 当检测到成环风险或自环时抛出
     */
    public static void validateNoCycle(
            Long parentRoleId,
            Long childRoleId,
            String parentRoleName,
            String childRoleName,
            Set<Long> descendantIdsOfChild
    ) throws RBACTopologyException {
        if (Objects.equals(parentRoleId, childRoleId) || (descendantIdsOfChild != null && descendantIdsOfChild.contains(parentRoleId))) {
            throw new RBACTopologyException(
                    RBACMessageKeys.TOPOLOGY_ROLE_INHERITANCE_CYCLE,
                    "addRoleInheritance",
                    parentRoleName,
                    childRoleName
            );
        }
    }

    /**
     * 计算指定角色在继承层级中向下可继承的所有后继子角色 ID 集合（包含自身）。
     *
     * @param startRoleIds      初始角色 ID 集合
     * @param directChildrenMap parentId -> List of childIds 映射
     * @return 包含起始角色及其所有传递可达子角色的 ID 集合
     */
    public static Set<Long> resolveAllDescendantRoleIds(
            Collection<Long> startRoleIds,
            Map<Long, List<Long>> directChildrenMap
    ) {
        Set<Long> result = new HashSet<>(startRoleIds);
        Queue<Long> queue = new ArrayDeque<>(startRoleIds);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            List<Long> children = directChildrenMap.getOrDefault(current, Collections.emptyList());
            for (Long child : children) {
                if (result.add(child)) {
                    queue.add(child);
                }
            }
        }
        return result;
    }

    /**
     * 计算对指定角色拥有继承权的所有上位祖先父角色 ID 集合（包含自身）。
     *
     * @param startRoleIds     初始角色 ID 集合
     * @param directParentsMap childId -> List of parentIds 映射
     * @return 包含起始角色及其所有传递可达祖先父角色的 ID 集合
     */
    public static Set<Long> resolveAllAscendantRoleIds(
            Collection<Long> startRoleIds,
            Map<Long, List<Long>> directParentsMap
    ) {
        Set<Long> result = new HashSet<>(startRoleIds);
        Queue<Long> queue = new ArrayDeque<>(startRoleIds);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            List<Long> parents = directParentsMap.getOrDefault(current, Collections.emptyList());
            for (Long parent : parents) {
                if (result.add(parent)) {
                    queue.add(parent);
                }
            }
        }
        return result;
    }
}
