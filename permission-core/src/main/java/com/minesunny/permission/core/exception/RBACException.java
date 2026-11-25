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
package com.minesunny.permission.core.exception;

import com.minesunny.permission.core.i18n.RBACMessages;

import java.util.Locale;

/**
 * RBAC 模块运行时根异常。
 * <p>
 * 继承自 {@link RuntimeException}，天然支持 Spring 声明式事务（{@code @Transactional}）
 * 发生异常时默认自动回滚，规避受检异常导致的事务不回滚隐患，
 * 并提升函数式流（Stream / Optional）编程调用的简洁性与健壮性。
 * <p>
 * 内置对 i18n（国际化）消息解析的支持：携带 {@code messageKey} 与占位符 {@code args}，
 * 在 {@link #getMessage()} 与 {@link #getLocalizedMessage(Locale)} 时动态解析为对应语言文本。
 *
 * <p>现代化分层异常体系：
 * <pre>
 * RuntimeException (Java 内置运行时根异常)
 *   └── RBACException (RBAC 模块根异常)
 *         ├── RBACValidationException (400 参数格式与输入校验非法)
 *         │     └── ValidationException (兼容历史旧类)
 *         ├── RBACNotFoundException   (404 目标实体或路径不存在)
 *         ├── RBACConflictException   (409 标识已存在或业务状态冲突)
 *         └── RBACTopologyException   (422 组织树拓扑与自环约束异常)
 * </pre>
 */
public class RBACException extends RuntimeException {

    private final String messageKey;
    private final Object[] args;
    private final String defaultMessage;

    public RBACException(String msg) {
        super(RBACMessages.hasKey(msg) ? RBACMessages.get(Locale.ROOT, msg, null, msg) : msg);
        if (RBACMessages.hasKey(msg)) {
            this.messageKey = msg;
            this.args = null;
            this.defaultMessage = null;
        } else {
            this.messageKey = null;
            this.args = null;
            this.defaultMessage = msg;
        }
    }

    public RBACException(String msg, Throwable cause) {
        super(RBACMessages.hasKey(msg) ? RBACMessages.get(Locale.ROOT, msg, null, msg) : msg, cause);
        if (RBACMessages.hasKey(msg)) {
            this.messageKey = msg;
            this.args = null;
            this.defaultMessage = null;
        } else {
            this.messageKey = null;
            this.args = null;
            this.defaultMessage = msg;
        }
    }

    public RBACException(Throwable cause) {
        super(cause);
        this.messageKey = null;
        this.args = null;
        this.defaultMessage = cause != null ? cause.getMessage() : null;
    }

    /**
     * 国际化模版异常构造（推荐）：根据消息键与动态参数直接解析模版。
     *
     * @param messageKey 国际化消息键模版
     * @param args       模版插值参数列表
     */
    public RBACException(String messageKey, Object... args) {
        super(RBACMessages.get(Locale.ROOT, messageKey, args, messageKey));
        this.messageKey = messageKey;
        this.args = args;
        this.defaultMessage = null;
    }

    /**
     * 国际化模版异常构造（携带 cause）：根据消息键、原因与动态参数直接解析模版。
     *
     * @param messageKey 国际化消息键模版
     * @param cause      底层异常原因
     * @param args       模版插值参数列表
     */
    public RBACException(String messageKey, Throwable cause, Object... args) {
        super(RBACMessages.get(Locale.ROOT, messageKey, args, messageKey), cause);
        this.messageKey = messageKey;
        this.args = args;
        this.defaultMessage = null;
    }

    public RBACException(String messageKey, Object[] args, String defaultMessage) {
        super(defaultMessage);
        this.messageKey = messageKey;
        this.args = args;
        this.defaultMessage = defaultMessage;
    }

    public RBACException(String messageKey, Object[] args, String defaultMessage, Throwable cause) {
        super(defaultMessage, cause);
        this.messageKey = messageKey;
        this.args = args;
        this.defaultMessage = defaultMessage;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 获取支持国际化解析的异常信息。
     * <p>
     * 优先根据当前线程上下文 Locale 进行解析，未匹配时返回兜底 defaultMessage。
     */
    @Override
    public String getMessage() {
        if (messageKey != null) {
            return RBACMessages.get(messageKey, args, defaultMessage);
        }
        return super.getMessage();
    }

    /**
     * 根据指定 Locale 获取国际化异常消息。
     *
     * @param locale 目标语言环境
     * @return 本地化后的异常描述
     */
    public String getLocalizedMessage(Locale locale) {
        if (messageKey != null) {
            return RBACMessages.get(locale, messageKey, args, defaultMessage);
        }
        return defaultMessage != null ? defaultMessage : super.getMessage();
    }
}
