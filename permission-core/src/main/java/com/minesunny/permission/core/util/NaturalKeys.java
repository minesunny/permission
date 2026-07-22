package com.minesunny.permission.core.util;

import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.OrgUnit;
import com.minesunny.permission.core.entity.actor.Post;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Operation;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.entity.perm.Resource;
import com.minesunny.permission.core.exception.ValidationException;
import org.springframework.util.StringUtils;

public final class NaturalKeys {

    private NaturalKeys() {
    }

    public static String requireUsername(User user) throws ValidationException {
        return require(user.getUsername(), "RBAC user's username");
    }

    public static String requireRoleName(Role role) throws ValidationException {
        return require(role.getRoleName(), "RBAC role's roleName");
    }

    public static String requirePermName(Permission permission) throws ValidationException {
        return require(permission.getPermName(), "RBAC permission's permName");
    }

    public static String requirePostName(Post post) throws ValidationException {
        return require(post.getPostName(), "post's postName");
    }

    public static String requireOrgName(OrgUnit orgUnit) throws ValidationException {
        return require(orgUnit.getOrgName(), "orgUnit's orgName");
    }

    public static String requireResourceName(Resource resource) throws ValidationException {
        return require(resource.getResourceName(), "resource's resourceName");
    }

    public static String requireOperationName(Operation operation) throws ValidationException {
        return require(operation.getOperationName(), "operation's operationName");
    }

    public static String require(String value, String name) throws ValidationException {
        if (!StringUtils.hasText(value)) {
            throw new ValidationException("The " + name + " is required and cannot be null");
        }
        return value;
    }
}
