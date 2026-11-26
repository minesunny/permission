package com.minesunny.permission.core.util;

import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACValidationException;
import com.minesunny.permission.core.i18n.RBACMessageKeys;
import org.springframework.util.StringUtils;

/**
 * 自然主键与业务必填字段校验工具类。
 */
public final class NaturalKeys {

    private NaturalKeys() {
    }

    /**
     * 校验并获取参与者的 actorName。
     *
     * @param actor 参与者实体
     * @return 非空的 actorName
     * @throws RBACValidationException 当 actorName 为空时抛出
     */
    public static String requireActorName(Actor actor) throws RBACValidationException {
        return require(actor.getActorName(), RBACMessageKeys.VALIDATION_ACTOR_NAME_REQUIRED, "RBAC actor's actorName");
    }

    /**
     * 校验并获取用户的 username。
     *
     * @param user 用户实体
     * @return 非空的 username
     * @throws RBACValidationException 当 username 为空时抛出
     */
    public static String requireUsername(User user) throws RBACValidationException {
        return require(user.getUsername(), RBACMessageKeys.VALIDATION_USERNAME_REQUIRED, "RBAC user's username");
    }

    /**
     * 校验并获取角色的 roleName。
     *
     * @param role 角色实体
     * @return 非空的 roleName
     * @throws RBACValidationException 当 roleName 为空时抛出
     */
    public static String requireRoleName(Role role) throws RBACValidationException {
        return require(role.getRoleName(), RBACMessageKeys.VALIDATION_ROLE_NAME_REQUIRED, "RBAC role's roleName");
    }

    /**
     * 校验并获取权限的 permName。
     *
     * @param permission 权限实体
     * @return 非空的 permName
     * @throws RBACValidationException 当 permName 为空时抛出
     */
    public static String requirePermName(Permission permission) throws RBACValidationException {
        return require(permission.getPermName(), RBACMessageKeys.VALIDATION_PERM_NAME_REQUIRED, "RBAC permission's permName");
    }

    /**
     * 校验并获取部门的 deptName。
     *
     * @param department 部门实体
     * @return 非空的 deptName
     * @throws RBACValidationException 当 deptName 为空时抛出
     */
    public static String requireDeptName(Department department) throws RBACValidationException {
        return require(department.getDeptName(), RBACMessageKeys.VALIDATION_DEPT_NAME_REQUIRED, "department's deptName");
    }

    /**
     * 校验并获取部门的物化路径 path。
     *
     * @param department 部门实体
     * @return 非空的 path
     * @throws RBACValidationException 当 path 为空时抛出
     */
    public static String requirePath(Department department) throws RBACValidationException {
        return require(department.getPath(), RBACMessageKeys.VALIDATION_PATH_REQUIRED, "department's path");
    }

    /**
     * 校验给定字符串非空。
     *
     * @param value 待校验值
     * @param name 字段说明名称
     * @return 非空字符串
     * @throws RBACValidationException 当字符串为空或仅空白字符时抛出
     */
    public static String require(String value, String name) throws RBACValidationException {
        return require(value, RBACMessageKeys.VALIDATION_REQUIRED, name);
    }

    /**
     * 校验给定字符串非空（携带国际化消息键）。
     *
     * @param value      待校验值
     * @param messageKey 国际化消息键
     * @param name       字段说明名称
     * @return 非空字符串
     * @throws RBACValidationException 当字符串为空或仅空白字符时抛出
     */
    public static String require(String value, String messageKey, String name) throws RBACValidationException {
        if (!StringUtils.hasText(value)) {
            if (RBACMessageKeys.VALIDATION_REQUIRED.equals(messageKey)) {
                throw new RBACValidationException(messageKey, name);
            }
            throw new RBACValidationException(messageKey);
        }
        return value;
    }
}
