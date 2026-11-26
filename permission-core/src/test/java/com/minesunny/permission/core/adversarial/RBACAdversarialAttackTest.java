package com.minesunny.permission.core.adversarial;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Actor;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.*;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.OrgMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Commit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RBAC 安全与边界攻击测试套件（Security & Adversarial Attack Test Suite）。
 * <p>
 * 模拟渗透攻击、拓扑破坏、自环递归、幽灵数据复活、中间表脏数据注入与绕过校验等对抗场景。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
@DisplayName("RBAC 对抗与边界渗透攻击测试")
public class RBACAdversarialAttackTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private OrgMgr orgMgr;

    // =========================================================================
    // 攻击向量 1: 路径自环与深度递归逃逸攻击 (Deep Cycle & Path Traversal Attack)
    // =========================================================================

    @Test
    @Commit
    @DisplayName("攻击1: 深度多级子树自环移动攻击——试图将祖先节点移入末梢子孙节点之下")
    void attackDeepCycleMovement() throws RBACException {
        // 构建 4 级深树: /atk-root -> /atk-root/b -> /atk-root/b/c -> /atk-root/b/c/d
        orgMgr.createDepartment(Department.builder().deptName("atk-node-a").path("/atk-root").build());
        orgMgr.createDepartment(Department.builder().deptName("atk-node-b").path("/atk-root/b").build());
        orgMgr.createDepartment(Department.builder().deptName("atk-node-c").path("/atk-root/b/c").build());
        orgMgr.createDepartment(Department.builder().deptName("atk-node-d").path("/atk-root/b/c/d").build());

        // 攻击动作：试图将顶层 A 移到其最深子孙 D 的分支之下 (/atk-root/b/c/d/hacked)
        RBACTopologyException ex = assertThrows(RBACTopologyException.class, () ->
                orgMgr.updateDepartment("atk-node-a", Department.builder().path("/atk-root/b/c/d/hacked").build()));
        assertTrue(ex.getMessage().contains("descendant path"), "必须阻断自环移动攻击");

        // 验证拓扑完全未被篡改
        Department nodeA = orgMgr.readDepartment("atk-node-a");
        assertEquals("/atk-root", nodeA.getPath());
        assertEquals(1, nodeA.getLevel());
    }

    @Test
    @Commit
    @DisplayName("攻击2: 畸形物化路径注入攻击（空路径、无前导斜杠、空格混淆）")
    void attackMalformedPathInjection() throws RBACException {
        // 尝试以缺少前导斜杠的路径创建
        assertThrows(RBACValidationException.class, () ->
                orgMgr.createDepartment(Department.builder().deptName("atk-bad-path-1").path("invalid/path").build()));

        // 尝试以纯空白字符创建路径
        assertThrows(RBACValidationException.class, () ->
                orgMgr.createDepartment(Department.builder().deptName("atk-bad-path-2").path("   ").build()));

        // 创建一个合法节点用于测试更新畸变路径
        orgMgr.createDepartment(Department.builder().deptName("atk-bad-path-target").path("/atk-bad-path-target").build());

        // 尝试以非法无斜杠路径更新
        assertThrows(RBACValidationException.class, () ->
                orgMgr.updateDepartment("atk-bad-path-target", Department.builder().path("no-slash-path").build()));
    }

    // =========================================================================
    // 攻击向量 2: 幽灵角色复活与权限泄漏攻击 (Ghost Role Resurrection Attack)
    // =========================================================================

    @Test
    @Commit
    @DisplayName("攻击3: 幽灵角色清理攻击——删除角色后关联彻底清空，新角色无幽灵残留")
    void attackGhostRoleResurrection() throws RBACException {
        String roleName = "ghost-role-atk";
        String userName = "ghost-user-atk";
        String permName = "ghost-perm-atk";

        Role oldRole = adminMgr.addRole(Role.builder().roleName(roleName).build());
        User user = adminMgr.addActor(User.builder().actorName(userName).username(userName).build());
        Permission perm = adminMgr.addPermission(Permission.builder().permName(permName).build());

        // 赋权与授权
        adminMgr.assignActor(userName, roleName);
        adminMgr.grantPermission(permName, roleName);

        // 确认权限已生效
        assertTrue(reviewMgr.hasRole(userName, roleName));
        assertTrue(reviewMgr.hasPermission(userName, permName));

        // 毁灭性动作：删除角色
        adminMgr.deleteRole(roleName);
        assertNull(reviewMgr.findRole(roleName));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readRole(roleName));

        // 攻击验证1：用户身上的角色和权限必须立即失效且无孤儿残留
        assertFalse(reviewMgr.hasRole(userName, roleName));
        assertFalse(reviewMgr.hasPermission(userName, permName));
        assertTrue(reviewMgr.assignedRoles(userName).isEmpty());

        // 攻击验证2：创建另一个新角色，验证新角色绝对不会继承历史用户的关系
        Role newRole = adminMgr.addRole(Role.builder().roleName("brand-new-role-atk").build());
        assertFalse(reviewMgr.hasRole(userName, "brand-new-role-atk"));
        assertTrue(reviewMgr.assignedActors("brand-new-role-atk").isEmpty());
    }

    @Test
    @DisplayName("攻击3.1: 软删除角色唯一键冲突防御——试图同名复活被系统安全拦截归一")
    void attackSoftDeletedRoleConflictDefense() throws RBACException {
        String roleName = "conflict-role-atk";
        adminMgr.addRole(Role.builder().roleName(roleName).build());
        adminMgr.deleteRole(roleName);

        // 试图同名复活已被归档的软删除角色，必须被系统捕获并归一为 RBACConflictException
        RBACConflictException resEx = assertThrows(RBACConflictException.class, () ->
                adminMgr.addRole(Role.builder().roleName(roleName).build()));
        assertTrue(resEx.getMessage().contains("already exists or conflicts with archived records"));
    }

    // =========================================================================
    // 攻击向量 3: 幽灵部门复活与角色/用户泄漏攻击 (Ghost Department Resurrection Attack)
    // =========================================================================

    @Test
    @Commit
    @DisplayName("攻击4: 幽灵部门清理攻击——删除部门后用户与角色关联彻底清空")
    void attackGhostDepartmentResurrection() throws RBACException {
        String deptName = "ghost-dept-atk";
        String roleName = "ghost-dept-role-atk";
        String userName = "ghost-dept-user-atk";

        Department oldDept = orgMgr.createDepartment(Department.builder().deptName(deptName).path("/ghost-dept-path").build());
        Role role = adminMgr.addRole(Role.builder().roleName(roleName).build());
        User user = adminMgr.addActor(User.builder().actorName(userName).username(userName).build());

        // 关联用户到部门，并将角色赋予部门作为 Actor
        orgMgr.addUser(deptName, userName);
        adminMgr.assignActor(deptName, roleName);

        assertEquals(1, orgMgr.readDepartmentUsers(deptName).size());
        assertTrue(reviewMgr.assignedRoles(deptName).stream().anyMatch(r -> r.getRoleName().equals(roleName)));

        // 毁灭性动作：删除部门
        orgMgr.deleteDepartment(deptName);
        assertNull(orgMgr.findDepartment(deptName));
        assertThrows(RBACNotFoundException.class, () -> orgMgr.readDepartment(deptName));

        // 攻击验证：角色对应的参与者列表中已剔除该部门，用户直属部门也已解除
        assertTrue(reviewMgr.assignedActors(roleName).stream().noneMatch(a -> a.getActorName().equals(deptName)));
    }

    @Test
    @DisplayName("攻击4.1: 软删除部门唯一键冲突防御——试图同名复活被系统安全拦截归一")
    void attackSoftDeletedDepartmentConflictDefense() throws RBACException {
        String deptName = "conflict-dept-atk";
        orgMgr.createDepartment(Department.builder().deptName(deptName).path("/conflict-dept-path").build());
        orgMgr.deleteDepartment(deptName);

        // 试图以同名重新创建部门，必须被系统拦截为 RBACConflictException
        RBACConflictException resEx = assertThrows(RBACConflictException.class, () ->
                orgMgr.createDepartment(Department.builder().deptName(deptName).path("/conflict-dept-path-new").build()));
        assertTrue(resEx.getMessage().contains("already exists or conflicts with archived records"));
    }

    // =========================================================================
    // 攻击向量 4: 悬空孤儿攻击（绕过 OrgMgr 直删父部门防护）
    // =========================================================================

    @Test
    @Commit
    @DisplayName("攻击5: 悬空孤儿攻击——试图绕过 OrgMgr 通过底层 AdminMgr 强删存在子部门的父节点")
    void attackOrphanSplittingViaAdminMgr() throws RBACException {
        orgMgr.createDepartment(Department.builder().deptName("atk-split-parent").path("/atk-split").build());
        orgMgr.createDepartment(Department.builder().deptName("atk-split-child").path("/atk-split/sub").build());

        // 攻击路径 1: 走 OrgMgr 删除受保护
        RBACTopologyException ex1 = assertThrows(RBACTopologyException.class, () ->
                orgMgr.deleteDepartment("atk-split-parent"));
        assertTrue(ex1.getMessage().contains("has child departments"));

        // 攻击路径 2: 绕道走 AdminMgr.deleteActor("atk-split-parent") 试图穿透
        RBACTopologyException ex2 = assertThrows(RBACTopologyException.class, () ->
                adminMgr.deleteActor("atk-split-parent"));
        assertTrue(ex2.getMessage().contains("has child departments"), "AdminMgr 必须具备相同防护力，绝不允许悬空子部门");

        // 验证拓扑结构完好
        assertNotNull(orgMgr.readDepartment("atk-split-parent"));
        assertNotNull(orgMgr.readDepartment("atk-split-child"));
    }

    // =========================================================================
    // 攻击向量 5: 脏键注入与恶意字符边界测试
    // =========================================================================

    @Test
    @Commit
    @DisplayName("攻击6: 恶意与畸变键攻击——传入空白键、换行符、空对象进行非法请求")
    void attackMalformedKeyInputs() {
        // 空白/制表符键攻击
        assertThrows(RBACValidationException.class, () -> adminMgr.addRole(Role.builder().roleName("   ").build()));
        assertThrows(RBACValidationException.class, () -> adminMgr.addPermission(Permission.builder().permName("\n\t").build()));
        assertThrows(RBACValidationException.class, () -> orgMgr.createDepartment(Department.builder().deptName(" ").path("/ok").build()));

        // 空对象调用防御
        assertThrows(RBACValidationException.class, () -> reviewMgr.hasRole(" ", "role-test"));
        assertThrows(RBACValidationException.class, () -> reviewMgr.hasPermission("user-test", "  "));
        assertThrows(RBACValidationException.class, () -> orgMgr.readDepartmentUsers("   "));
        assertThrows(RBACValidationException.class, () -> orgMgr.readDepartmentSuccessorUsers(" "));
    }

    // =========================================================================
    // 攻击向量 6: 移动撞车与子孙路径冲突破坏测试
    // =========================================================================

    @Test
    @Commit
    @DisplayName("攻击7: 迁移碰撞攻击——试图将子树迁移到与已有部门冲突的目的地，验证原子阻断")
    void attackSubTreeCollisionMovement() throws RBACException {
        // 构造分支 1: /src-p -> /src-p/c1
        orgMgr.createDepartment(Department.builder().deptName("atk-src-p").path("/src-p").build());
        orgMgr.createDepartment(Department.builder().deptName("atk-src-c1").path("/src-p/c1").build());

        // 构造独立节点: /dest-conflict/c1 (与即将级联迁移的子节点冲突)
        orgMgr.createDepartment(Department.builder().deptName("atk-dest-p").path("/dest-conflict").build());
        orgMgr.createDepartment(Department.builder().deptName("atk-dest-c1").path("/dest-conflict/c1").build());

        // 试图把 /src-p 迁移为 /dest-conflict/src-sub，其子节点 /src-p/c1 将尝试变为 /dest-conflict/src-sub/c1 (正常)
        // 但如果我们试图把 /src-p 迁移为已存在的 /dest-conflict 路径：
        assertThrows(RBACConflictException.class, () ->
                orgMgr.updateDepartment("atk-src-p", Department.builder().path("/dest-conflict").build()));

        // 原分支 1 保持完整
        Department srcP = orgMgr.readDepartment("atk-src-p");
        assertEquals("/src-p", srcP.getPath());
        Department srcC1 = orgMgr.readDepartment("atk-src-c1");
        assertEquals("/src-p/c1", srcC1.getPath());
    }

    // =========================================================================
    // 攻击向量 7: 重复授权与解除不存在映射的非法操作测试
    // =========================================================================

    @Test
    @Commit
    @DisplayName("攻击8: 重复赋权与撤销不存在关系的非法操作拦截")
    void attackDuplicateAndNonExistentMappings() throws RBACException {
        String role = "atk-dup-role";
        String user = "atk-dup-user";
        String perm = "atk-dup-perm";

        adminMgr.addRole(Role.builder().roleName(role).build());
        adminMgr.addActor(User.builder().actorName(user).username(user).build());
        adminMgr.addPermission(Permission.builder().permName(perm).build());

        // 正常赋予
        adminMgr.assignActor(user, role);
        adminMgr.grantPermission(perm, role);

        // 重复赋予攻击：必须抛出 RBACConflictException，不允许产生重复脏记录
        assertThrows(RBACConflictException.class, () -> adminMgr.assignActor(user, role));
        assertThrows(RBACConflictException.class, () -> adminMgr.grantPermission(perm, role));

        // 正常撤销
        adminMgr.revokePermission(perm, role);
        adminMgr.deassignActor(user, role);

        // 重复撤销攻击：不存在的关系强行撤销必须抛出 RBACConflictException
        assertThrows(RBACConflictException.class, () -> adminMgr.revokePermission(perm, role));
        assertThrows(RBACConflictException.class, () -> adminMgr.deassignActor(user, role));
    }
}
