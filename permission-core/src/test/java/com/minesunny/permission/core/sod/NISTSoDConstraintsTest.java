package com.minesunny.permission.core.sod;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.Session;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.sod.DSDSet;
import com.minesunny.permission.core.entity.sod.SSDSet;
import com.minesunny.permission.core.exception.RBACConflictException;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.RBACNotFoundException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.manager.SessionMgr;
import com.minesunny.permission.core.manager.SoDMgr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ANSI INCITS 359-2004 / NIST RBAC 约束职责分离 (Constrained RBAC: SSD & DSD) 完整测试套件。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class NISTSoDConstraintsTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private SessionMgr sessionMgr;

    @Autowired
    private SoDMgr soDMgr;

    /**
     * Test 1: SSD 规则管理函数与审查函数 (CreateSsdSet, SsdRoleSets, SsdRoleSetRoles, SsdRoleSetCardinality, DeleteSsdSet)
     */
    @Test
    @DisplayName("NIST SSD: SSD 约束集生命周期与审查函数测试")
    void testSSDSetLifecycleAndReview() throws RBACException {
        Role r1 = adminMgr.addRole(Role.builder().roleName("Purchaser").build());
        Role r2 = adminMgr.addRole(Role.builder().roleName("Approver").build());
        Role r3 = adminMgr.addRole(Role.builder().roleName("Auditor").build());

        // 1. 创建 SSD 规则集: 采购审批互斥，基数 2（至多选 1 个）
        SSDSet ssd = soDMgr.createSSDSet("ProcurementSSD", Set.of("Purchaser", "Approver"), 2);
        assertNotNull(ssd.getId());
        assertEquals("ProcurementSSD", ssd.getName());
        assertEquals(2, ssd.getCardinality());

        // 2. 审查函数验证
        List<SSDSet> allSets = soDMgr.ssdRoleSets();
        assertTrue(allSets.stream().anyMatch(s -> "ProcurementSSD".equals(s.getName())));

        Set<Role> roles = soDMgr.ssdRoleSetRoles("ProcurementSSD");
        assertEquals(2, roles.size());
        assertTrue(roles.stream().anyMatch(r -> "Purchaser".equals(r.getRoleName())));
        assertTrue(roles.stream().anyMatch(r -> "Approver".equals(r.getRoleName())));

        assertEquals(2, soDMgr.ssdRoleSetCardinality("ProcurementSSD"));

        // 3. 动态追加成员角色与修改基数
        soDMgr.addSSDRoleMember("ProcurementSSD", "Auditor");
        assertEquals(3, soDMgr.ssdRoleSetRoles("ProcurementSSD").size());

        soDMgr.setSSDSetCardinality("ProcurementSSD", 3);
        assertEquals(3, soDMgr.ssdRoleSetCardinality("ProcurementSSD"));

        // 4. 移除角色成员与删除 SSD 规则集
        soDMgr.setSSDSetCardinality("ProcurementSSD", 2);
        soDMgr.deleteSSDRoleMember("ProcurementSSD", "Auditor");
        assertEquals(2, soDMgr.ssdRoleSetRoles("ProcurementSSD").size());

        soDMgr.deleteSSDSet("ProcurementSSD");
        assertThrows(RBACNotFoundException.class, () -> soDMgr.ssdRoleSetCardinality("ProcurementSSD"));
    }

    /**
     * Test 2: SSD 静态职责分离分配时强制拦截 (AssignActor 静态阻断)
     */
    @Test
    @DisplayName("NIST SSD: 静态赋权冲突强制拦截 (|AssignedRoles(u) ∩ role_set| < n)")
    void testSSDConflictInterceptionOnAssign() throws RBACException {
        Role accountant = adminMgr.addRole(Role.builder().roleName("Accountant").build());
        Role cashier = adminMgr.addRole(Role.builder().roleName("Cashier").build());

        // 创建财务两权分离 (会计与出纳互斥，基数 2)
        soDMgr.createSSDSet("FinanceSSD", Set.of("Accountant", "Cashier"), 2);

        User alice = adminMgr.addUser(User.builder().username("alice").build());

        // 授予 Alice 会计角色成功
        assertDoesNotThrow(() -> adminMgr.assignActor("alice", "Accountant"));
        assertTrue(reviewMgr.hasRole("alice", "Accountant"));

        // 尝试授予 Alice 出纳角色，必须被 SSD 规则拦截抛出 RBACConflictException
        RBACConflictException ex = assertThrows(RBACConflictException.class,
                () -> adminMgr.assignActor("alice", "Cashier"));
        assertTrue(ex.getMessage().contains("SSD") || ex.getMessage().contains("FinanceSSD"));

        // 验证出纳角色未被分配
        assertFalse(reviewMgr.hasRole("alice", "Cashier"));
    }

    /**
     * Test 3: DSD 动态职责分离在会话激活时强制拦截 (CreateSession / AddActiveRole 动态阻断)
     */
    @Test
    @DisplayName("NIST DSD: 动态会话角色激活互斥拦截 (|ActiveRoles(s) ∩ role_set| < n)")
    void testDSDConflictInterceptionOnSessionActivation() throws RBACException {
        Role applicant = adminMgr.addRole(Role.builder().roleName("Applicant").build());
        Role reviewer = adminMgr.addRole(Role.builder().roleName("Reviewer").build());

        // 创建动态互斥规则 (同一会话中不能同时激活申请人与审查人，基数 2)
        soDMgr.createDSDSet("ApprovalDSD", Set.of("Applicant", "Reviewer"), 2);

        User bob = adminMgr.addUser(User.builder().username("bob").build());

        // 用户可以静态被同时授予这两个角色（SSD 允许，因为未配置为 SSD）
        adminMgr.assignActor("bob", "Applicant");
        adminMgr.assignActor("bob", "Reviewer");
        assertTrue(reviewMgr.hasRole("bob", "Applicant"));
        assertTrue(reviewMgr.hasRole("bob", "Reviewer"));

        // 1. 测试在 createSession 中同时传入这两个互斥角色，必须被阻断
        assertThrows(RBACConflictException.class, () ->
                sessionMgr.createSession("bob", List.of("Applicant", "Reviewer")));

        // 2. 测试先激活 Applicant，再通过 addActiveRole 激活 Reviewer，必须被阻断
        Session session = sessionMgr.createSession("bob", List.of("Applicant"));
        assertEquals(1, session.getRoles().size());

        assertThrows(RBACConflictException.class, () ->
                sessionMgr.addActiveRole(session, "Reviewer"));

        // 会话中依然只有 1 个角色
        assertEquals(1, session.getRoles().size());
        assertEquals("Applicant", session.getRoles().get(0).getRoleName());
    }

    /**
     * Test 4: Role 最大主体基数限制 (maxCardinality) 赋权拦截
     */
    @Test
    @DisplayName("Role Constraint: maxCardinality 角色最大分配主体基数限制拦截")
    void testRoleMaxCardinalityConstraint() throws RBACException {
        // 创建一个最多只能由 1 个主体担任的角色 (如首席执行官 CEO)
        Role ceo = adminMgr.addRole(Role.builder()
                .roleName("CEO")
                .maxCardinality(1)
                .build());

        User user1 = adminMgr.addUser(User.builder().username("user1").build());
        User user2 = adminMgr.addUser(User.builder().username("user2").build());

        // 分配给 user1 成功
        assertDoesNotThrow(() -> adminMgr.assignActor("user1", "CEO"));

        // 分配给 user2 触发基数溢出，必须拦截
        assertThrows(RBACConflictException.class, () -> adminMgr.assignActor("user2", "CEO"));
    }

    /**
     * Test 5: Role Temporal TRBAC 时空约束 (锁定状态 / 时间窗口限制激活)
     */
    @Test
    @DisplayName("Temporal TRBAC: 角色临时锁定或未生效时禁止在会话中激活")
    void testTemporalRoleActivationRestriction() throws RBACException {
        Calendar future = Calendar.getInstance();
        future.add(Calendar.DAY_OF_MONTH, 1);

        // 创建一个未来才生效的角色
        Role futureRole = adminMgr.addRole(Role.builder()
                .roleName("FutureRole")
                .beginDateTime(future)
                .build());

        User charlie = adminMgr.addUser(User.builder().username("charlie").build());
        adminMgr.assignActor("charlie", "FutureRole");

        // 在当前会话激活时，因角色尚未到达生效时间，必须被拦截
        assertThrows(RBACConflictException.class, () ->
                sessionMgr.createSession("charlie", List.of("FutureRole")));
    }

    /**
     * Test 6: DSD 规则管理函数与审查函数 (CreateDsdSet, DsdRoleSets, DsdRoleSetRoles, DsdRoleSetCardinality, DeleteDsdSet)
     */
    @Test
    @DisplayName("NIST DSD: DSD 约束集生命周期与审查函数测试")
    void testDSDSetLifecycleAndReview() throws RBACException {
        Role d1 = adminMgr.addRole(Role.builder().roleName("Doctor").build());
        Role d2 = adminMgr.addRole(Role.builder().roleName("Pharmacist").build());
        Role d3 = adminMgr.addRole(Role.builder().roleName("Nurse").build());

        // 1. 创建 DSD 规则集: 医生与药剂师会话互斥，基数 2
        DSDSet dsd = soDMgr.createDSDSet("MedicalDSD", Set.of("Doctor", "Pharmacist"), 2);
        assertNotNull(dsd.getId());
        assertEquals("MedicalDSD", dsd.getName());
        assertEquals(2, dsd.getCardinality());

        // 2. 审查函数验证
        List<DSDSet> allSets = soDMgr.dsdRoleSets();
        assertTrue(allSets.stream().anyMatch(s -> "MedicalDSD".equals(s.getName())));

        Set<Role> roles = soDMgr.dsdRoleSetRoles("MedicalDSD");
        assertEquals(2, roles.size());
        assertTrue(roles.stream().anyMatch(r -> "Doctor".equals(r.getRoleName())));
        assertTrue(roles.stream().anyMatch(r -> "Pharmacist".equals(r.getRoleName())));

        assertEquals(2, soDMgr.dsdRoleSetCardinality("MedicalDSD"));

        // 3. 动态追加成员角色与修改基数
        soDMgr.addDSDRoleMember("MedicalDSD", "Nurse");
        assertEquals(3, soDMgr.dsdRoleSetRoles("MedicalDSD").size());

        soDMgr.setDSDSetCardinality("MedicalDSD", 3);
        assertEquals(3, soDMgr.dsdRoleSetCardinality("MedicalDSD"));

        // 4. 移除角色成员与删除 DSD 规则集
        soDMgr.setDSDSetCardinality("MedicalDSD", 2);
        soDMgr.deleteDSDRoleMember("MedicalDSD", "Nurse");
        assertEquals(2, soDMgr.dsdRoleSetRoles("MedicalDSD").size());

        soDMgr.deleteDSDSet("MedicalDSD");
        assertThrows(RBACNotFoundException.class, () -> soDMgr.dsdRoleSetCardinality("MedicalDSD"));
    }

    /**
     * Test 7: SoD 边界与非法校验 (基数范围、重复成员、空成员集合)
     */
    @Test
    @DisplayName("NIST SoD: 边界与非法参数校验 (2 <= n <= |role_set|)")
    void testSoDValidationBoundaries() throws RBACException {
        adminMgr.addRole(Role.builder().roleName("RoleA").build());
        adminMgr.addRole(Role.builder().roleName("RoleB").build());

        // 1. 基数小于 2 或大于角色集合大小
        assertThrows(RBACException.class, () ->
                soDMgr.createSSDSet("InvalidSSD1", Set.of("RoleA", "RoleB"), 1));
        assertThrows(RBACException.class, () ->
                soDMgr.createSSDSet("InvalidSSD2", Set.of("RoleA", "RoleB"), 3));

        // 2. 重复创建同名规则集冲突
        soDMgr.createSSDSet("DuplicateSSD", Set.of("RoleA", "RoleB"), 2);
        assertThrows(RBACConflictException.class, () ->
                soDMgr.createSSDSet("DuplicateSSD", Set.of("RoleA", "RoleB"), 2));

        // 3. 重复添加已有角色成员冲突
        assertThrows(RBACConflictException.class, () ->
                soDMgr.addSSDRoleMember("DuplicateSSD", "RoleA"));

        // 4. 移除成员导致剩余角色少于基数限制
        assertThrows(RBACException.class, () ->
                soDMgr.deleteSSDRoleMember("DuplicateSSD", "RoleA"));
    }
}
