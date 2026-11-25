package com.minesunny.permission.core.hierarchy;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACConflictException;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.RBACTopologyException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
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
 * NIST Hierarchical RBAC 角色继承偏序关系（Role Hierarchy）管理与审查测试套件。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class RoleHierarchyTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    /**
     * Test 1: 建立与解除继承偏序关系（AddInheritance / DeleteInheritance）
     * 结构: GeneralManager (GM) >= DepartmentManager (DM) >= Employee (EMP)
     */
    @Test
    @DisplayName("NIST Add/DeleteInheritance: 角色继承关系维护与直接权限向下传递聚合")
    void testAddAndDeleteInheritance() throws RBACException {
        Role gm = adminMgr.addRole(Role.builder().roleName("GeneralManager").build());
        Role dm = adminMgr.addRole(Role.builder().roleName("DepartmentManager").build());
        Role emp = adminMgr.addRole(Role.builder().roleName("Employee").build());

        Permission pEmp = adminMgr.addPermission(Permission.builder().permName("emp:clock-in").resource("clock").operation("IN").build());
        Permission pDm = adminMgr.addPermission(Permission.builder().permName("dm:approve-leave").resource("leave").operation("APPROVE").build());
        Permission pGm = adminMgr.addPermission(Permission.builder().permName("gm:budget").resource("budget").operation("ALLOCATE").build());

        adminMgr.grantPermission(pEmp, emp);
        adminMgr.grantPermission(pDm, dm);
        adminMgr.grantPermission(pGm, gm);

        // 建立继承关系: GM >= DM >= EMP
        adminMgr.addRoleInheritance("GeneralManager", "DepartmentManager");
        adminMgr.addRoleInheritance("DepartmentManager", "Employee");

        // 1. 验证 EMP: 仅有直接权限 1 个
        List<Permission> empAuthPerms = reviewMgr.authorizedPermissions("Employee");
        assertEquals(1, empAuthPerms.size());
        assertEquals("emp:clock-in", empAuthPerms.get(0).getPermName());

        // 2. 验证 DM: 继承 EMP 权限，拥有 2 个权限 (dm:approve-leave, emp:clock-in)
        List<Permission> dmAuthPerms = reviewMgr.authorizedPermissions("DepartmentManager");
        assertEquals(2, dmAuthPerms.size());
        List<String> dmPermNames = dmAuthPerms.stream().map(Permission::getPermName).toList();
        assertTrue(dmPermNames.containsAll(List.of("dm:approve-leave", "emp:clock-in")));

        // 3. 验证 GM: 继承 DM 与 EMP 权限，拥有全量 3 个权限
        List<Permission> gmAuthPerms = reviewMgr.authorizedPermissions("GeneralManager");
        assertEquals(3, gmAuthPerms.size());
        List<String> gmPermNames = gmAuthPerms.stream().map(Permission::getPermName).toList();
        assertTrue(gmPermNames.containsAll(List.of("gm:budget", "dm:approve-leave", "emp:clock-in")));

        // 解除 DM 与 EMP 的继承关系
        adminMgr.deleteRoleInheritance("DepartmentManager", "Employee");

        // 验证解除后: DM 与 GM 不再继承 EMP 的打卡权限
        List<Permission> dmPermsAfter = reviewMgr.authorizedPermissions("DepartmentManager");
        assertEquals(1, dmPermsAfter.size());
        assertEquals("dm:approve-leave", dmPermsAfter.get(0).getPermName());

        List<Permission> gmPermsAfter = reviewMgr.authorizedPermissions("GeneralManager");
        assertEquals(2, gmPermsAfter.size());
        assertFalse(gmPermsAfter.stream().map(Permission::getPermName).toList().contains("emp:clock-in"));
    }

    /**
     * Test 2: 有向无环图 (DAG) 成环检测防御（Cycle Detection）
     * 防止 A >= B, B >= C 情况下，再配置 C >= A 形成循环死锁。
     */
    @Test
    @DisplayName("NIST 拓扑防环测试: 检测到环路死循环时严格抛出 RBACTopologyException")
    void testRoleInheritanceCyclePrevention() throws RBACException {
        Role rA = adminMgr.addRole(Role.builder().roleName("RoleA").build());
        Role rB = adminMgr.addRole(Role.builder().roleName("RoleB").build());
        Role rC = adminMgr.addRole(Role.builder().roleName("RoleC").build());

        adminMgr.addRoleInheritance("RoleA", "RoleB");
        adminMgr.addRoleInheritance("RoleB", "RoleC");

        // 1. 禁止自环 (A >= A)
        assertThrows(RBACTopologyException.class, () ->
                adminMgr.addRoleInheritance("RoleA", "RoleA"));

        // 2. 禁止直接成环 (B >= A)
        assertThrows(RBACTopologyException.class, () ->
                adminMgr.addRoleInheritance("RoleB", "RoleA"));

        // 3. 禁止间接多级成环 (C >= A)
        assertThrows(RBACTopologyException.class, () ->
                adminMgr.addRoleInheritance("RoleC", "RoleA"));

        // 重复添加相同关系报冲突
        assertThrows(RBACConflictException.class, () ->
                adminMgr.addRoleInheritance("RoleA", "RoleB"));
    }

    /**
     * Test 3: AuthorizedRoles 与 AuthorizedActors 双向查询测试
     */
    @Test
    @DisplayName("NIST AuthorizedRoles & AuthorizedActors: 角色与主体层级成员双向穿透审查")
    void testAuthorizedRolesAndActors() throws RBACException {
        User boss = adminMgr.addUser(User.builder().actorName("big-boss").username("big-boss").build());
        User clerk = adminMgr.addUser(User.builder().actorName("office-clerk").username("office-clerk").build());

        Role senior = adminMgr.addRole(Role.builder().roleName("SeniorRole").build());
        Role junior = adminMgr.addRole(Role.builder().roleName("JuniorRole").build());

        adminMgr.addRoleInheritance("SeniorRole", "JuniorRole");

        adminMgr.assignActor(boss, senior);
        adminMgr.assignActor(clerk, junior);

        // 1. boss 分配了 senior，但由于 Senior >= Junior，boss 的 authorizedRoles 包含两者
        List<Role> bossRoles = reviewMgr.authorizedRoles("big-boss");
        assertEquals(2, bossRoles.size());
        List<String> bossRoleNames = bossRoles.stream().map(Role::getRoleName).toList();
        assertTrue(bossRoleNames.containsAll(List.of("SeniorRole", "JuniorRole")));

        // 2. clerk 只分配了 junior，下位没有子角色，authorizedRoles 只有 1 个
        List<Role> clerkRoles = reviewMgr.authorizedRoles("office-clerk");
        assertEquals(1, clerkRoles.size());
        assertEquals("JuniorRole", clerkRoles.get(0).getRoleName());

        // 3. authorizedActors: junior 角色的执行主体应包含 clerk（直接分配）和 boss（上位继承分配）
        List<Actor> juniorActors = reviewMgr.authorizedActors("JuniorRole");
        assertEquals(2, juniorActors.size());
        List<String> juniorActorNames = juniorActors.stream().map(Actor::getActorName).toList();
        assertTrue(juniorActorNames.containsAll(List.of("office-clerk", "big-boss")));

        // senior 角色的执行主体仅有 boss
        List<Actor> seniorActors = reviewMgr.authorizedActors("SeniorRole");
        assertEquals(1, seniorActors.size());
        assertEquals("big-boss", seniorActors.get(0).getActorName());
    }
}
