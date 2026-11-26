package com.minesunny.permission.core.session;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.Session;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACException;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NIST Core RBAC CheckAccess 系统支撑函数与 OperationsOnResource 审查函数测试。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class NISTCheckAccessAndOperationsTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private SessionMgr sessionMgr;

    @Test
    @DisplayName("NIST CheckAccess: 基于会话与主体维度的 CheckAccess(op, obj) 访问决策测试")
    void testCheckAccess() throws RBACException {
        User user = adminMgr.addUser(User.builder().actorName("alice").username("alice").build());
        Role doctor = adminMgr.addRole(Role.builder().roleName("Doctor").build());

        // 授予正向权限: READ, WRITE on patient_record
        Permission pRead = adminMgr.addPermission(Permission.builder()
                .permName("perm:read:patient_record")
                .resource("patient_record")
                .operation("READ")
                .effect("ALLOW")
                .build());
        Permission pWrite = adminMgr.addPermission(Permission.builder()
                .permName("perm:write:patient_record")
                .resource("patient_record")
                .operation("WRITE")
                .effect("ALLOW")
                .build());

        adminMgr.grantPermission(pRead, doctor);
        adminMgr.grantPermission(pWrite, doctor);
        adminMgr.assignActor(user, doctor);

        // 1. 基于主体维度的 checkAccess
        assertTrue(reviewMgr.checkAccess("alice", "READ", "patient_record"));
        assertTrue(reviewMgr.checkAccess("alice", "WRITE", "patient_record"));
        assertFalse(reviewMgr.checkAccess("alice", "DELETE", "patient_record")); // 未授权动作
        assertFalse(reviewMgr.checkAccess("alice", "READ", "financial_record")); // 未授权对象

        // 2. 基于会话维度的 checkAccess
        Session session = sessionMgr.createSession("alice", List.of("Doctor"));
        assertTrue(reviewMgr.checkAccess(session, "READ", "patient_record"));
        assertTrue(reviewMgr.checkAccess(session, "WRITE", "patient_record"));
        assertFalse(reviewMgr.checkAccess(session, "DELETE", "patient_record"));

        // 3. 显式 DENY 规则测试 (Deny-Overrides)
        Permission pDenyDelete = adminMgr.addPermission(Permission.builder()
                .permName("perm:deny:delete:patient_record")
                .resource("patient_record")
                .operation("DELETE")
                .effect("DENY")
                .build());
        adminMgr.grantPermission(pDenyDelete, doctor);
        assertFalse(reviewMgr.checkAccess(session, "DELETE", "patient_record"));
    }

    @Test
    @DisplayName("NIST OperationsOnResource: 查询角色与主体在特定资源上可执行的动作集合")
    void testOperationsOnResource() throws RBACException {
        User bob = adminMgr.addUser(User.builder().actorName("bob").username("bob").build());
        Role accountant = adminMgr.addRole(Role.builder().roleName("Accountant").build());

        Permission pInvoiceRead = adminMgr.addPermission(Permission.builder()
                .permName("invoice:view")
                .resource("invoice")
                .operation("READ")
                .build());
        Permission pInvoiceExport = adminMgr.addPermission(Permission.builder()
                .permName("invoice:export")
                .resource("invoice")
                .operation("EXPORT")
                .build());
        Permission pTaxRead = adminMgr.addPermission(Permission.builder()
                .permName("tax:view")
                .resource("tax_report")
                .operation("READ")
                .build());

        adminMgr.grantPermission(pInvoiceRead, accountant);
        adminMgr.grantPermission(pInvoiceExport, accountant);
        adminMgr.grantPermission(pTaxRead, accountant);
        adminMgr.assignActor(bob, accountant);

        // 1. roleOperationsOnResource
        List<String> roleOpsInvoice = reviewMgr.roleOperationsOnResource("Accountant", "invoice");
        assertEquals(2, roleOpsInvoice.size());
        assertTrue(roleOpsInvoice.containsAll(List.of("READ", "EXPORT")));

        List<String> roleOpsTax = reviewMgr.roleOperationsOnResource("Accountant", "tax_report");
        assertEquals(1, roleOpsTax.size());
        assertEquals("READ", roleOpsTax.get(0));

        List<String> roleOpsUnknown = reviewMgr.roleOperationsOnResource("Accountant", "payroll");
        assertTrue(roleOpsUnknown.isEmpty());

        // 2. userOperationsOnResource
        List<String> userOpsInvoice = reviewMgr.userOperationsOnResource("bob", "invoice");
        assertEquals(2, userOpsInvoice.size());
        assertTrue(userOpsInvoice.containsAll(List.of("READ", "EXPORT")));
    }
}
