package com.minesunny.permission.core.i18n;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.exception.RBACConflictException;
import com.minesunny.permission.core.exception.RBACNotFoundException;
import com.minesunny.permission.core.exception.RBACTopologyException;
import com.minesunny.permission.core.exception.RBACValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.OrgMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RBAC 异常国际化 (i18n) 测试套件。
 * 验证中英文、日文、韩文多语言切换、占位符插值、降级回退及容器集成表现。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class RBACInternationalizationTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private OrgMgr orgMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("测试直接通过 RBACMessages 工具类获取中/英/日/韩消息与降级")
    void testDirectRBACMessagesResolution() {
        // 英文解析
        String enReq = RBACMessages.getMessage(RBACMessageKeys.VALIDATION_REQUIRED, new Object[]{"testField"}, "default msg", Locale.ENGLISH);
        assertEquals("The testField is required and cannot be null", enReq);

        // 中文解析
        String zhReq = RBACMessages.getMessage(RBACMessageKeys.VALIDATION_REQUIRED, new Object[]{"测试字段"}, "default msg", Locale.SIMPLIFIED_CHINESE);
        assertEquals("测试字段 不能为空", zhReq);

        // 日文解析
        String jaReq = RBACMessages.getMessage(RBACMessageKeys.VALIDATION_REQUIRED, new Object[]{"テスト項目"}, "default msg", Locale.JAPANESE);
        assertEquals("テスト項目 は必須であり、null にすることはできません", jaReq);

        // 韩文解析
        String koReq = RBACMessages.getMessage(RBACMessageKeys.VALIDATION_REQUIRED, new Object[]{"테스트항목"}, "default msg", Locale.KOREAN);
        assertEquals("테스트항목은(는) 필수 항목이며 null일 수 없습니다", koReq);

        // 未知 key 降级回退到 defaultMessage
        String fallback = RBACMessages.getMessage("non.existent.key", null, "Default Fallback", Locale.SIMPLIFIED_CHINESE);
        assertEquals("Default Fallback", fallback);
    }

    @Test
    @DisplayName("测试基于 LocaleContextHolder 的异常消息动态解析与显式 getLocalizedMessage (中英日韩)")
    void testExceptionDynamicLocaleResolution() {
        RBACValidationException ex = new RBACValidationException(
                RBACMessageKeys.VALIDATION_USERNAME_REQUIRED);

        // 显式指定 Locale
        assertEquals("用户登录名 (username) 不能为空", ex.getLocalizedMessage(Locale.SIMPLIFIED_CHINESE));
        assertEquals("The user's username is required and cannot be null", ex.getLocalizedMessage(Locale.ENGLISH));
        assertEquals("ユーザー名 (username) は必須であり、null にすることはできません", ex.getLocalizedMessage(Locale.JAPANESE));
        assertEquals("사용자 이름 (username)은 필수 항목이며 null일 수 없습니다", ex.getLocalizedMessage(Locale.KOREAN));

        // 模拟切换当前线程上下文 Locale 到日文
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        assertEquals("ユーザー名 (username) は必須であり、null にすることはできません", ex.getMessage());

        // 模拟切换当前线程上下文 Locale 到韩文
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertEquals("사용자 이름 (username)은 필수 항목이며 null일 수 없습니다", ex.getMessage());

        // 模拟切换当前线程上下文 Locale 到中文
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        assertEquals("用户登录名 (username) 不能为空", ex.getMessage());

        // 模拟切换当前线程上下文 Locale 到英文
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertEquals("The user's username is required and cannot be null", ex.getMessage());
    }

    @Test
    @DisplayName("测试 RBAC 各异常子类的模版驱动多语言插值行为")
    void testAllSubclassesInterpolation() {
        // 404 Not Found (直接传递模版键与参数列表)
        RBACNotFoundException nfEx = new RBACNotFoundException(
                RBACMessageKeys.NOT_FOUND_ROLE,
                "deleteRole", "roleName: super_admin");
        assertEquals("[deleteRole]: RBAC 角色实体 [roleName: super_admin] 不存在", nfEx.getLocalizedMessage(Locale.SIMPLIFIED_CHINESE));
        assertEquals("[deleteRole]: The RBAC role entity [roleName: super_admin] does not exist", nfEx.getLocalizedMessage(Locale.ENGLISH));
        assertEquals("[deleteRole]: RBAC ロールエンティティ [roleName: super_admin] が存在しません", nfEx.getLocalizedMessage(Locale.JAPANESE));
        assertEquals("[deleteRole]: RBAC 역할 엔티티 [roleName: super_admin]이(가) 존재하지 않습니다", nfEx.getLocalizedMessage(Locale.KOREAN));

        // 409 Conflict (直接传递模版键与参数列表)
        RBACConflictException cfEx = new RBACConflictException(
                RBACMessageKeys.CONFLICT_ROLE_EXISTS,
                "addRole", "ops_admin");
        assertEquals("[addRole]: RBAC 角色实体 [roleName: ops_admin] 已存在", cfEx.getLocalizedMessage(Locale.SIMPLIFIED_CHINESE));
        assertEquals("[addRole]: The RBAC role entity [roleName: ops_admin] already exists", cfEx.getLocalizedMessage(Locale.ENGLISH));
        assertEquals("[addRole]: RBAC ロールエンティティ [roleName: ops_admin] は既に存在します", cfEx.getLocalizedMessage(Locale.JAPANESE));
        assertEquals("[addRole]: RBAC 역할 엔티티 [roleName: ops_admin]이(가) 이미 존재합니다", cfEx.getLocalizedMessage(Locale.KOREAN));

        // 422 Topology (直接传递模版键与参数列表)
        RBACTopologyException tpEx = new RBACTopologyException(
                RBACMessageKeys.TOPOLOGY_CHILD_DEPARTMENTS,
                "deleteDepartment", "engineering");
        assertEquals("[deleteDepartment]: 部门 [engineering] 包含子部门，禁止删除", tpEx.getLocalizedMessage(Locale.SIMPLIFIED_CHINESE));
        assertEquals("[deleteDepartment]: Cannot delete department [engineering] because it has child departments", tpEx.getLocalizedMessage(Locale.ENGLISH));
        assertEquals("[deleteDepartment]: 部署 [engineering] には子部署が存在するため、削除できません", tpEx.getLocalizedMessage(Locale.JAPANESE));
        assertEquals("[deleteDepartment]: 부서 [engineering]에 하위 부서가 포함되어 있어 삭제할 수 없습니다", tpEx.getLocalizedMessage(Locale.KOREAN));
    }

    @Test
    @DisplayName("测试 Spring 上下文中文环境下业务抛出的异常消息自动国际化")
    void testSpringManagedManagerUnderChineseLocale() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        // 校验异常 (400)
        RBACValidationException valEx = assertThrows(RBACValidationException.class, () -> adminMgr.readUser(""));
        assertEquals("用户登录名 (username) 不能为空", valEx.getMessage());

        // 实体未找到异常 (404)
        RBACNotFoundException nfEx = assertThrows(RBACNotFoundException.class, () -> adminMgr.deleteRole("non_existent_i18n_role"));
        assertEquals("[deleteRole]: RBAC 角色实体 [non_existent_i18n_role] 不存在", nfEx.getMessage());

        // 冲突异常 (409)
        Role role = Role.builder().roleName("i18n_role_dup").build();
        adminMgr.addRole(role);
        RBACConflictException cfEx = assertThrows(RBACConflictException.class, () -> adminMgr.addRole(role));
        assertEquals("[addRole]: RBAC 角色实体 [roleName: i18n_role_dup] 已存在", cfEx.getMessage());
    }

    @Test
    @DisplayName("测试 Spring 上下文英文环境下业务抛出的异常消息保持英文输出")
    void testSpringManagedManagerUnderEnglishLocale() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        // 校验异常 (400)
        RBACValidationException valEx = assertThrows(RBACValidationException.class, () -> adminMgr.readUser(""));
        assertEquals("The user's username is required and cannot be null", valEx.getMessage());

        // 实体未找到异常 (404)
        RBACNotFoundException nfEx = assertThrows(RBACNotFoundException.class, () -> adminMgr.deleteRole("non_existent_i18n_role_en"));
        assertEquals("[deleteRole]: The RBAC role entity [non_existent_i18n_role_en] does not exist", nfEx.getMessage());
    }

    @Test
    @DisplayName("测试 Spring 上下文日文环境下业务抛出的异常消息自动国际化")
    void testSpringManagedManagerUnderJapaneseLocale() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);

        // 校验异常 (400)
        RBACValidationException valEx = assertThrows(RBACValidationException.class, () -> adminMgr.readUser(""));
        assertEquals("ユーザー名 (username) は必須であり、null にすることはできません", valEx.getMessage());

        // 实体未找到异常 (404)
        RBACNotFoundException nfEx = assertThrows(RBACNotFoundException.class, () -> adminMgr.deleteRole("non_existent_i18n_role_ja"));
        assertEquals("[deleteRole]: RBAC ロールエンティティ [non_existent_i18n_role_ja] が存在しません", nfEx.getMessage());

        // 冲突异常 (409)
        Role role = Role.builder().roleName("i18n_role_dup_ja").build();
        adminMgr.addRole(role);
        RBACConflictException cfEx = assertThrows(RBACConflictException.class, () -> adminMgr.addRole(role));
        assertEquals("[addRole]: RBAC ロールエンティティ [roleName: i18n_role_dup_ja] は既に存在します", cfEx.getMessage());
    }

    @Test
    @DisplayName("测试 Spring 上下文韩文环境下业务抛出的异常消息自动国际化")
    void testSpringManagedManagerUnderKoreanLocale() {
        LocaleContextHolder.setLocale(Locale.KOREAN);

        // 校验异常 (400)
        RBACValidationException valEx = assertThrows(RBACValidationException.class, () -> adminMgr.readUser(""));
        assertEquals("사용자 이름 (username)은 필수 항목이며 null일 수 없습니다", valEx.getMessage());

        // 实体未找到异常 (404)
        RBACNotFoundException nfEx = assertThrows(RBACNotFoundException.class, () -> adminMgr.deleteRole("non_existent_i18n_role_ko"));
        assertEquals("[deleteRole]: RBAC 역할 엔티티 [non_existent_i18n_role_ko]이(가) 존재하지 않습니다", nfEx.getMessage());

        // 冲突异常 (409)
        Role role = Role.builder().roleName("i18n_role_dup_ko").build();
        adminMgr.addRole(role);
        RBACConflictException cfEx = assertThrows(RBACConflictException.class, () -> adminMgr.addRole(role));
        assertEquals("[addRole]: RBAC 역할 엔티티 [roleName: i18n_role_dup_ko]이(가) 이미 존재합니다", cfEx.getMessage());
    }
}
