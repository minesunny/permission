/*
 * Copyright 2023 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */
package com.minesunny.permission.core.i18n;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * RBAC 国际化消息解析工具类。
 * <p>
 * 支持在 Spring 容器环境下优先委托给 Spring 的 {@link MessageSource} 进行国际化解析；
 * 在非 Spring 容器（如独立单元测试或轻量级集成场景）下，自动降级使用内置的
 * {@link ResourceBundle}（{@code i18n/rbac-messages}）进行本地化解析与占位符格式化。
 */
@Component
public class RBACMessages implements MessageSourceAware {

    private static final String BUNDLE_BASE_NAME = "i18n/rbac-messages";

    private static volatile MessageSource messageSource;

    @Autowired(required = false)
    public void setRbacMessageSource(@Qualifier("rbacMessageSource") MessageSource ms) {
        if (ms != null) {
            RBACMessages.messageSource = ms;
        }
    }

    @Override
    public void setMessageSource(MessageSource ms) {
        if (RBACMessages.messageSource == null) {
            RBACMessages.messageSource = ms;
        }
    }

    /**
     * 判断指定 key 是否为已注册的国际化消息模版键。
     *
     * @param key 待检测键
     * @return 若在当前资源包中存在该模版键则返回 true，否则返回 false
     */
    public static boolean hasKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (messageSource != null) {
            try {
                if (messageSource.getMessage(key, null, null, Locale.ROOT) != null) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            ResourceBundle rootBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ROOT);
            return rootBundle.containsKey(key);
        } catch (MissingResourceException ignored) {
            return false;
        }
    }

    /**
     * 根据当前线程上下文 Locale 解析消息。
     *
     * @param key            国际化消息键
     * @param args           占位符参数列表
     * @param defaultMessage 未找到时的兜底缺省文本
     * @return 格式化后的本地化文本
     */
    public static String get(String key, Object[] args, String defaultMessage) {
        Locale locale = LocaleContextHolder.getLocale();
        return get(locale, key, args, defaultMessage);
    }

    /**
     * 根据指定 Locale 解析消息。
     *
     * @param locale         目标语言环境
     * @param key            国际化消息键
     * @param args           占位符参数列表
     * @param defaultMessage 未找到时的兜底缺省文本
     * @return 格式化后的本地化文本
     */
    public static String get(Locale locale, String key, Object[] args, String defaultMessage) {
        if (key == null) {
            return defaultMessage != null ? formatPattern(defaultMessage, args) : "";
        }
        Locale targetLocale = locale != null ? locale : LocaleContextHolder.getLocale();

        // 1. 尝试使用 Spring MessageSource (优先使用注入的 rbacMessageSource)
        if (messageSource != null) {
            try {
                String resolved = messageSource.getMessage(key, args, null, targetLocale);
                if (resolved != null && !resolved.isBlank()) {
                    return resolved;
                }
            } catch (Exception ignored) {
                // 降级使用 ResourceBundle
            }
        }

        // 2. 降级使用 ResourceBundle
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, targetLocale);
            if (bundle.containsKey(key)) {
                String pattern = bundle.getString(key);
                return formatPattern(pattern, args);
            }
        } catch (MissingResourceException ignored) {
            // 继续使用 defaultMessage / ROOT 兜底
        }

        // 3. 兜底使用默认语言包 (Locale.ROOT / 英文模版)
        try {
            ResourceBundle rootBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ROOT);
            if (rootBundle.containsKey(key)) {
                String pattern = rootBundle.getString(key);
                return formatPattern(pattern, args);
            }
        } catch (MissingResourceException ignored) {
            // 继续使用 defaultMessage
        }

        // 4. 兜底返回 defaultMessage
        if (defaultMessage != null) {
            return formatPattern(defaultMessage, args);
        }
        return formatPattern(key, args);
    }

    /**
     * 根据当前线程上下文 Locale 解析消息（兼容 Spring MessageSource 命名约定）。
     *
     * @param key            国际化消息键
     * @param args           占位符参数列表
     * @param defaultMessage 未找到时的兜底缺省文本
     * @return 格式化后的本地化文本
     */
    public static String getMessage(String key, Object[] args, String defaultMessage) {
        return get(key, args, defaultMessage);
    }

    /**
     * 根据指定 Locale 解析消息（兼容 Spring MessageSource 命名约定）。
     *
     * @param key            国际化消息键
     * @param args           占位符参数列表
     * @param defaultMessage 未找到时的兜底缺省文本
     * @param locale         目标语言环境
     * @return 格式化后的本地化文本
     */
    public static String getMessage(String key, Object[] args, String defaultMessage, Locale locale) {
        return get(locale, key, args, defaultMessage);
    }

    private static String formatPattern(String pattern, Object[] args) {
        if (args == null || args.length == 0 || pattern == null) {
            return pattern;
        }
        try {
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            return pattern;
        }
    }
}
