package com.minesunny.permission.core.session;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.Session;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACConflictException;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.RBACNotFoundException;
import com.minesunny.permission.core.exception.RBACValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.manager.SessionMgr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ANSI / NIST RBAC Core Session 规范与动态授权一致性测试套件。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class SessionMgrTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private SessionMgr sessionMgr;

    /**
     * Test 1: 会话创建与初始激活角色校验（NIST CreateSession）
     * 只有已被授予用户的角色才能在会话中激活；未授予角色时拒绝激活抛出 RBACConflictException。
     */
    @Test
    @DisplayName("会话创建测试: 仅允许激活已分配给该用户的角色，未分配角色禁止激活")
    void testCreateSessionWithRoleValidation() throws RBACException {
        User user = adminMgr.addUser(User.builder().actorName("sess-user-1").username("sess-user-1").build());
        Role assignedRole = adminMgr.addRole(Role.builder().roleName("sess-assigned-role").build());
        Role unassignedRole = adminMgr.addRole(Role.builder().roleName("sess-unassigned-role").build());

        adminMgr.assignActor(user, assignedRole);

        // 成功创建带合法已分配角色的会话
        Session session = sessionMgr.createSession(user, List.of(assignedRole));
        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertEquals("sess-user-1", session.getUser().getUsername());
        assertEquals(1, session.getRoles().size());
        assertEquals("sess-assigned-role", session.getRoles().get(0).getRoleName());

        // 尝试激活未分配的角色 -> 严格抛出 RBACConflictException 拦截
        assertThrows(RBACConflictException.class, () ->
                sessionMgr.createSession(user, List.of(unassignedRole)));
    }

    /**
     * Test 2: 动态激活与注销角色（NIST AddActiveRole / DropActiveRole）
     * 验证在同一个会话生命周期内，动态按需升权和降权（最小特权原则）。
     */
    @Test
    @DisplayName("会话生命周期测试: 动态激活角色(AddActiveRole)与注销角色(DropActiveRole)")
    void testDynamicAddAndDropActiveRole() throws RBACException {
        User user = adminMgr.addUser(User.builder().actorName("sess-user-2").username("sess-user-2").build());
        Role r1 = adminMgr.addRole(Role.builder().roleName("sess-r1").build());
        Role r2 = adminMgr.addRole(Role.builder().roleName("sess-r2").build());
        adminMgr.assignActor(user, r1);
        adminMgr.assignActor(user, r2);

        // 创建初始仅激活 r1 的会话
        Session session = sessionMgr.createSession(user, List.of(r1));
        assertEquals(1, session.getRoles().size());

        // 动态激活 r2
        sessionMgr.addActiveRole(session, "sess-r2");
        assertEquals(2, session.getRoles().size());

        // 重复激活已激活角色 -> 抛出冲突异常
        assertThrows(RBACConflictException.class, () -> sessionMgr.addActiveRole(session, "sess-r2"));

        // 动态注销 r1
        sessionMgr.dropActiveRole(session, "sess-r1");
        assertEquals(1, session.getRoles().size());
        assertEquals("sess-r2", session.getRoles().get(0).getRoleName());

        // 注销不存在于会话中的角色 -> 抛出 NotFound 异常
        assertThrows(RBACNotFoundException.class, () -> sessionMgr.dropActiveRole(session, "sess-r1"));

        // 销毁会话
        sessionMgr.deleteSession(session);
        assertNull(session.getUser());
        assertNull(session.getSessionId());
        assertTrue(session.getRoles().isEmpty());
    }

    /**
     * Test 3: 核心痛点验证 —— 权限变动时的会话实时一致性（Dynamic Consistency）
     * 场景：
     * 1. 用户被赋予角色 R，会话 S 激活了角色 R 并享有权限 P。
     * 2. 管理员在后台撤销了用户的角色 R（adminMgr.deassignActor）。
     * 3. 即使会话 S 仍存活于客户端或内存中，在调用 reviewMgr.sessionRoles(S) 和 sessionPermissions(S) 时，
     *    系统必须自动感知并剥离角色 R，权限即时失效，杜绝越权漏洞！
     */
    @Test
    @DisplayName("一致性测试: 管理员在后台撤销用户角色后，既有活动会话立即丧失对应角色与权限")
    void testSessionDynamicConsistencyOnRoleRevocation() throws RBACException {
        User user = adminMgr.addUser(User.builder().actorName("sess-user-3").username("sess-user-3").build());
        Role role = adminMgr.addRole(Role.builder().roleName("sess-revokable-role").build());
        Permission perm = adminMgr.addPermission(Permission.builder().permName("sess-perm-action").build());

        adminMgr.grantPermission(perm, role);
        adminMgr.assignActor(user, role);

        // 创建会话并激活该角色
        Session session = sessionMgr.createSession("sess-user-3", List.of("sess-revokable-role"));
        assertNotNull(session);

        // 验证撤销前：会话拥有激活角色与对应权限
        List<Role> activeRolesBefore = reviewMgr.sessionRoles(session);
        assertEquals(1, activeRolesBefore.size());
        assertEquals("sess-revokable-role", activeRolesBefore.get(0).getRoleName());

        List<Permission> activePermsBefore = reviewMgr.sessionPermissions(session);
        assertEquals(1, activePermsBefore.size());
        assertEquals("sess-perm-action", activePermsBefore.get(0).getPermName());

        // 管理员在后台撤销用户的该角色绑定！
        adminMgr.deassignActor(user, role);

        // 验证撤销后：无需销毁或重启 Session，下一次鉴权立即感知！
        List<Role> activeRolesAfter = reviewMgr.sessionRoles(session);
        assertTrue(activeRolesAfter.isEmpty(), "用户被撤权后，会话中的角色必须自动过滤失效");

        List<Permission> activePermsAfter = reviewMgr.sessionPermissions(session);
        assertTrue(activePermsAfter.isEmpty(), "用户被撤权后，会话通过该角色获得的权限必须自动归零");
    }

    /**
     * Test 4: 角色内部权限动态修改在会话中的实时生效
     * 角色保持激活，管理员给角色追加新权限 P2，活动会话立即能读取到 P2。
     */
    @Test
    @DisplayName("一致性测试: 角色内授权变更时，激活该角色的活动会话权限即时同步")
    void testSessionDynamicPermissionUpdate() throws RBACException {
        User user = adminMgr.addUser(User.builder().actorName("sess-user-4").username("sess-user-4").build());
        Role role = adminMgr.addRole(Role.builder().roleName("sess-role-4").build());
        Permission p1 = adminMgr.addPermission(Permission.builder().permName("sess-p1").build());
        Permission p2 = adminMgr.addPermission(Permission.builder().permName("sess-p2").build());

        adminMgr.grantPermission(p1, role);
        adminMgr.assignActor(user, role);

        Session session = sessionMgr.createSession("sess-user-4", List.of("sess-role-4"));

        // 初始只有 p1
        assertEquals(1, reviewMgr.sessionPermissions(session).size());

        // 管理员为角色追加 p2
        adminMgr.grantPermission(p2, role);

        // 会话即时获得 p1 和 p2
        List<Permission> permissions = reviewMgr.sessionPermissions(session);
        assertEquals(2, permissions.size());
        List<String> permNames = permissions.stream().map(Permission::getPermName).toList();
        assertTrue(permNames.contains("sess-p1"));
        assertTrue(permNames.contains("sess-p2"));
    }
}
