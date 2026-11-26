package com.minesunny.permission.core.org;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.dto.DepartmentTreeNode;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.exception.RBACConflictException;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.RBACNotFoundException;
import com.minesunny.permission.core.exception.RBACTopologyException;
import com.minesunny.permission.core.exception.RBACValidationException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.OrgMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.util.DepartmentPathUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 组织架构与物化路径极限/边界综合测试套件。
 * 覆盖超深层级、非法路径规范化、孤儿节点防护、级联删除保护与用户继承汇聚等关键场景。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class DepartmentPathAndBoundaryTest {

    @Autowired
    private OrgMgr orgMgr;

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Test 1: 纯工具类路径规范化与边界测试
     * 验证末尾斜杠去除、首尾空白去除、父路径截取等极端情况。
     */
    @Test
    @DisplayName("路径工具测试: 复杂与非标路径的规范化和父路径提取")
    void testDepartmentPathUtils() throws RBACException {
        assertEquals("/dept1", DepartmentPathUtils.normalizePath("/dept1"));
        assertEquals("/dept1", DepartmentPathUtils.normalizePath("  /dept1/  "));
        assertEquals("/dept1/sub1", DepartmentPathUtils.normalizePath("/dept1/sub1/"));
        assertEquals("/a/b/c", DepartmentPathUtils.normalizePath("/a/b/c/"));

        // 空路径或空白应抛出 RBACValidationException
        assertThrows(RBACValidationException.class, () -> DepartmentPathUtils.normalizePath(null));
        assertThrows(RBACValidationException.class, () -> DepartmentPathUtils.normalizePath("   "));
        // 不以 / 开头的路径应抛出 RBACValidationException
        assertThrows(RBACValidationException.class, () -> DepartmentPathUtils.normalizePath("dept1"));

        // extractParentPath
        assertNull(DepartmentPathUtils.extractParentPath("/root"));
        assertEquals("/root", DepartmentPathUtils.extractParentPath("/root/dept1"));
        assertEquals("/a/b", DepartmentPathUtils.extractParentPath("/a/b/c"));
    }

    /**
     * Test 2: 超深层级树创建与查询（深度 25 层）
     * 验证物化路径在超深层级链条下的级联创建、层级 level 正确递增（1 到 25），
     * 以及树形结构组装与递归解析无栈溢出。
     */
    @Test
    @DisplayName("极限测试: 深度达 25 层的超深单链部门树创建与递归检索")
    void testDeepHierarchyDepartmentTree() throws RBACException {
        int depth = 25;
        StringBuilder pathBuilder = new StringBuilder();

        for (int i = 1; i <= depth; i++) {
            pathBuilder.append("/node").append(i);
            String currentPath = pathBuilder.toString();
            String deptName = "deep-dept-" + i;

            Department dept = orgMgr.createDepartment(Department.builder()
                    .actorName(deptName)
                    .deptName(deptName)
                    .path(currentPath)
                    .build());

            assertNotNull(dept);
            assertEquals(i, dept.getLevel(), "第 " + i + " 层部门的 level 必须准确为 " + i);
            assertEquals(currentPath, dept.getPath());
        }

        // 查询第 25 层的子树
        DepartmentTreeNode leafSubTree = orgMgr.readDepartmentSubTree("deep-dept-25");
        assertNotNull(leafSubTree);
        assertEquals(25, leafSubTree.getDepartment().getLevel());
        assertTrue(leafSubTree.getChildren().isEmpty());

        // 查询根节点并验证整棵子树深度
        DepartmentTreeNode rootSubTree = orgMgr.readDepartmentSubTree("deep-dept-1");
        assertNotNull(rootSubTree);
        assertEquals(1, rootSubTree.getDepartment().getLevel());

        // 递归下钻验证 25 层子节点
        DepartmentTreeNode current = rootSubTree;
        for (int i = 2; i <= depth; i++) {
            assertEquals(1, current.getChildren().size(), "第 " + (i - 1) + " 层应有且仅有 1 个子节点");
            current = current.getChildren().get(0);
            assertEquals(i, current.getDepartment().getLevel());
            assertEquals("deep-dept-" + i, current.getDepartment().getDeptName());
        }
    }

    /**
     * Test 3: 孤儿节点防御测试（Orphan Prevention）
     * 尝试直接创建多级路径但中间父路径在数据库中不存在的部门，系统必须拦截抛出 RBACNotFoundException。
     */
    @Test
    @DisplayName("边界测试: 父级部门不存在时，禁止创建跨层孤儿部门")
    void testOrphanDepartmentCreationPrevention() {
        // 父路径 /nonexistent/parent 并不存在
        Department orphan = Department.builder()
                .actorName("orphan-child")
                .deptName("orphan-child")
                .path("/nonexistent/parent/child")
                .build();

        assertThrows(RBACNotFoundException.class, () -> orgMgr.createDepartment(orphan),
                "当父路径未持久化时，应抛出 RBACNotFoundException 阻止孤儿部门");
    }

    /**
     * Test 4: 部门名称与物化路径冲突与重复性测试
     */
    @Test
    @DisplayName("边界测试: 部门名称重复或路径已被占用时的冲突拦截")
    void testDepartmentConflictValidation() throws RBACException {
        orgMgr.createDepartment(Department.builder()
                .actorName("unique-dept-1")
                .deptName("unique-dept-1")
                .path("/conflict-root")
                .build());

        // 重复部门名
        assertThrows(RBACConflictException.class, () -> orgMgr.createDepartment(Department.builder()
                .actorName("unique-dept-1")
                .deptName("unique-dept-1")
                .path("/conflict-other-path")
                .build()));

        // 重复路径
        assertThrows(RBACConflictException.class, () -> orgMgr.createDepartment(Department.builder()
                .actorName("unique-dept-2")
                .deptName("unique-dept-2")
                .path("/conflict-root")
                .build()));
    }

    /**
     * Test 5: 级联删除保护与用户关系清理
     * 1. 包含子部门的父部门，调用删除时必须被拦截阻止（RBACConflictException / RBACException）。
     * 2. 删除叶子部门时，系统应自动清理关联的 DepartmentUserMap，且不影响直属用户本身。
     */
    @Test
    @DisplayName("级联测试: 父部门删除保护与叶子部门删除时用户映射联动清理")
    void testCascadeDeleteProtectionAndUserUnlink() throws RBACException {
        // 创建 parent -> child
        Department parent = orgMgr.createDepartment(Department.builder()
                .actorName("protect-parent")
                .deptName("protect-parent")
                .path("/protect-parent")
                .build());

        Department child = orgMgr.createDepartment(Department.builder()
                .actorName("protect-child")
                .deptName("protect-child")
                .path("/protect-parent/child")
                .build());

        // 尝试删除具有子部门的 parent 必须被拓扑保护拦截
        assertThrows(RBACTopologyException.class, () -> orgMgr.deleteDepartment("protect-parent"),
                "存在子部门时，直接删除父部门必须被阻止抛出 RBACTopologyException");

        // 向 child 添加用户
        User user = adminMgr.addUser(User.builder().actorName("cascade-user").username("cascade-user").build());
        orgMgr.addUser("protect-child", "cascade-user");

        List<User> childUsers = orgMgr.readDepartmentUsers("protect-child");
        assertEquals(1, childUsers.size());
        assertEquals("cascade-user", childUsers.get(0).getUsername());

        // 成功删除叶子部门
        orgMgr.deleteDepartment("protect-child");
        assertNull(orgMgr.findDepartment("protect-child"));

        // 用户实体依然完好保留在系统内，没有被错误级联删除
        assertNotNull(reviewMgr.findUser("cascade-user"));

        // 现在 parent 没有任何子部门了，可以成功删除
        orgMgr.deleteDepartment("protect-parent");
        assertNull(orgMgr.findDepartment("protect-parent"));
    }

    /**
     * Test 6: 后继子孙用户跨级聚合与去重测试（readDepartmentSuccessorUsers）
     * 结构:
     *        Root (/corp)
     *        ├── u_root
     *        ├── Tech (/corp/tech)
     *        │   ├── u_tech
     *        │   └── Dev (/corp/tech/dev)
     *        │       └── u_dev
     *        └── HR (/corp/hr)
     *            └── u_hr
     *
     * 验证:
     * - Tech 的后继用户应包含: u_tech + u_dev
     * - Root 的后继用户应包含: u_root + u_tech + u_dev + u_hr
     */
    @Test
    @DisplayName("聚合测试: 多级部门后继成员跨级汇总与去重验证")
    void testDepartmentSuccessorUsersAggregation() throws RBACException {
        // 部门树构建
        orgMgr.createDepartment(Department.builder().actorName("corp-root").deptName("corp-root").path("/corp").build());
        orgMgr.createDepartment(Department.builder().actorName("corp-tech").deptName("corp-tech").path("/corp/tech").build());
        orgMgr.createDepartment(Department.builder().actorName("corp-dev").deptName("corp-dev").path("/corp/tech/dev").build());
        orgMgr.createDepartment(Department.builder().actorName("corp-hr").deptName("corp-hr").path("/corp/hr").build());

        // 创建用户
        adminMgr.addUser(User.builder().actorName("u_root").username("u_root").build());
        adminMgr.addUser(User.builder().actorName("u_tech").username("u_tech").build());
        adminMgr.addUser(User.builder().actorName("u_dev").username("u_dev").build());
        adminMgr.addUser(User.builder().actorName("u_hr").username("u_hr").build());

        // 分配到各部门
        orgMgr.addUser("corp-root", "u_root");
        orgMgr.addUser("corp-tech", "u_tech");
        orgMgr.addUser("corp-dev", "u_dev");
        orgMgr.addUser("corp-hr", "u_hr");

        // 验证直属用户
        assertEquals(1, orgMgr.readDepartmentUsers("corp-root").size());
        assertEquals("u_root", orgMgr.readDepartmentUsers("corp-root").get(0).getUsername());
        assertEquals(1, orgMgr.readDepartmentUsers("corp-tech").size());
        assertEquals("u_tech", orgMgr.readDepartmentUsers("corp-tech").get(0).getUsername());

        // 验证 Tech 后继用户（应包含 u_tech 和 u_dev 共 2 人）
        List<User> techSuccessors = orgMgr.readDepartmentSuccessorUsers("corp-tech");
        assertEquals(2, techSuccessors.size());
        List<String> techUsernames = techSuccessors.stream().map(User::getUsername).toList();
        assertTrue(techUsernames.contains("u_tech"));
        assertTrue(techUsernames.contains("u_dev"));

        // 验证 Root 全量后继用户（应包含全部 4 人）
        List<User> rootSuccessors = orgMgr.readDepartmentSuccessorUsers("corp-root");
        assertEquals(4, rootSuccessors.size());
        List<String> rootUsernames = rootSuccessors.stream().map(User::getUsername).toList();
        assertTrue(rootUsernames.containsAll(List.of("u_root", "u_tech", "u_dev", "u_hr")));
    }
}
