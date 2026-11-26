package com.minesunny.permission.core.util;

import com.minesunny.permission.core.dto.DepartmentTreeNode;
import com.minesunny.permission.core.entity.actor.Department;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 部门树形结构组装工具类。
 */
public final class DepartmentTreeUtils {

    private DepartmentTreeUtils() {
    }

    /**
     * 将从数据库检索出的扁平部门列表在内存中以 O(N) 线性复杂度组装为树形结构。
     *
     * @param departments 按层级升序排序的部门实体列表
     * @return 组织架构顶级根节点列表（子孙节点递归嵌套在 {@link DepartmentTreeNode} 的 children 中）
     */
    @NotNull
    public static List<DepartmentTreeNode> buildTree(@NotNull List<Department> departments) {
        if (departments.isEmpty()) {
            return Collections.emptyList();
        }

        List<DepartmentTreeNode> roots = new ArrayList<>();
        Map<String, DepartmentTreeNode> nodeMap = new LinkedHashMap<>();

        for (Department dept : departments) {
            nodeMap.put(dept.getPath(), DepartmentTreeNode.builder().department(dept).build());
        }

        for (Department dept : departments) {
            DepartmentTreeNode currentNode = nodeMap.get(dept.getPath());
            String pPath = DepartmentPathUtils.extractParentPath(dept.getPath());

            if (pPath == null || !nodeMap.containsKey(pPath)) {
                roots.add(currentNode);
            } else {
                nodeMap.get(pPath).getChildren().add(currentNode);
            }
        }

        return roots;
    }
}
