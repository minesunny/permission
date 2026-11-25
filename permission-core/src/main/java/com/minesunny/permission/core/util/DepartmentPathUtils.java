package com.minesunny.permission.core.util;

import com.minesunny.permission.core.exception.RBACValidationException;
import com.minesunny.permission.core.i18n.RBACMessageKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;

/**
 * 部门物化路径（Materialized Path）校验与计算工具类。
 */
public final class DepartmentPathUtils {

    private DepartmentPathUtils() {
    }

    /**
     * 标准化路径格式并校验基本合法性。
     * <ul>
     *     <li>非空且必须以 '/' 开头；</li>
     *     <li>自动去除首尾空白字符；</li>
     *     <li>多级路径去除末尾的 '/'（如 '/a/b/' -> '/a/b'；单个 '/' 保留）。</li>
     * </ul>
     *
     * @param path 待标准化的物化路径
     * @return 标准化后的路径字符串
     * @throws RBACValidationException 当路径为空或不以 '/' 开头时抛出
     */
    public static String normalizePath(String path) throws RBACValidationException {
        if (!StringUtils.hasText(path)) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_PATH_EMPTY);
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_PATH_START_SLASH);
        }
        if (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 提取指定路径的父级路径。
     * <p>例如：
     * <ul>
     *     <li>{@code "/1/2/5"} -> {@code "/1/2"}</li>
     *     <li>{@code "/1"} -> {@code null}（顶级根节点无父路径）</li>
     * </ul>
     *
     * @param path 已标准化的物化路径
     * @return 父级路径字符串，若当前路径为根节点则返回 {@code null}
     */
    @Nullable
    public static String extractParentPath(@NotNull String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash > 0) {
            return path.substring(0, lastSlash);
        }
        return null;
    }
}
