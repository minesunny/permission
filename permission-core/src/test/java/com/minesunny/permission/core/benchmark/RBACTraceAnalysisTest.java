package com.minesunny.permission.core.benchmark;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACException;
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
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.TestPropertySource;

/**
 * RBAC 核心操作深度耗时 Trace 剖析测试。
 * 分段统计每个子步骤（校验、实体解析、中间表查询、ORM实体组装、内存遍历）的纳秒级开销占比。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
@TestPropertySource(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=ERROR"
})
class RBACTraceAnalysisTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Test
    @Commit
    @DisplayName("Trace 剖析: 绑定、解绑与鉴权操作的子步骤耗时分布")
    void traceAnalysis() throws RBACException {
        // 准备基准数据
        User user = adminMgr.addUser(User.builder().actorName("trace-usr").username("trace-usr").build());
        Role role = adminMgr.addRole(Role.builder().roleName("trace-role").build());
        Permission perm = adminMgr.addPermission(Permission.builder()
                .permName("trace-perm").resource("trace-res").operation("read").build());

        // 预热 JVM
        for (int i = 0; i < 50; i++) {
            adminMgr.grantPermission(perm, role);
            adminMgr.assignActor(user, role);
            reviewMgr.hasRole(user, role);
            reviewMgr.hasPermission(user, perm);
            adminMgr.deassignActor(user, role);
            adminMgr.revokePermission(perm, role);
        }

        System.out.println("\n==========================================================================================");
        System.out.println("                         RBAC 核心操作子步骤 Trace 耗时深度剖析                             ");
        System.out.println("==========================================================================================");

        // -------------------------------------------------------------
        // Trace 1: grantPermission(perm, role) 耗时分解
        // -------------------------------------------------------------
        int iterations = 100;
        long tResolvePerm = 0;
        long tResolveRole = 0;
        long tExistCheck = 0;
        long tMapSave = 0;

        for (int i = 0; i < iterations; i++) {
            long s1 = System.nanoTime();
            // 子步骤 1: 按名称解析权限
            reviewMgr.readPermission(perm.getPermName());
            long s2 = System.nanoTime();
            // 子步骤 2: 按名称解析角色
            reviewMgr.readRole(role.getRoleName());
            long s3 = System.nanoTime();

            // 实际执行 grantPermission
            long s4 = System.nanoTime();
            adminMgr.grantPermission(perm, role);
            long s5 = System.nanoTime();

            adminMgr.revokePermission(perm, role);

            tResolvePerm += (s2 - s1);
            tResolveRole += (s3 - s2);
            tMapSave += (s5 - s4);
        }

        System.out.printf("[Trace 1] grantPermission 单次平均耗时分解 (%d 次采样):%n", iterations);
        System.out.printf("  ├─ 1. 自然键校验与 Permission 查库解析 : %8.3f µs%n", (tResolvePerm / (double) iterations) / 1000.0);
        System.out.printf("  ├─ 2. 自然键校验与 Role 查库解析       : %8.3f µs%n", (tResolveRole / (double) iterations) / 1000.0);
        System.out.printf("  └─ 3. mapMgr 查重 exist + insert 写入   : %8.3f µs%n", (tMapSave / (double) iterations) / 1000.0);

        // 准备多角色多权限网状关联
        int roleCount = 5;
        int permCount = 20;
        for (int r = 0; r < roleCount; r++) {
            Role rEntity = adminMgr.addRole(Role.builder().roleName("trace-role-" + r).build());
            adminMgr.assignActor(user, rEntity);
            for (int p = 0; p < permCount; p++) {
                Permission pEntity = adminMgr.addPermission(Permission.builder()
                        .permName("trace-p-" + r + "-" + p).resource("res").operation("read").build());
                adminMgr.grantPermission(pEntity, rEntity);
            }
        }

        // -------------------------------------------------------------
        // Trace 2: hasPermission(user, perm) 耗时分解
        // -------------------------------------------------------------
        long tStep1_AssignedRoles = 0;
        long tStep2_MapFind = 0;
        long tStep3_SpecAndReadPerms = 0;
        long tStep4_StreamMatch = 0;
        long tTotalHasPerm = 0;

        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();

            // 子步骤 1: assignedRoles (查 actor 及 lazy 加载 roles)
            long t1 = System.nanoTime();
            var roles = reviewMgr.assignedRoles(user);
            long t2 = System.nanoTime();

            // 子步骤 2: mapMgr.find 查出中间表 permission_id 列表
            var permIds = reviewMgr.actorPermissions(user); // 包含 step 2+3
            long t3 = System.nanoTime();

            // 完整 hasPermission
            long sHasPerm = System.nanoTime();
            reviewMgr.hasPermission(user, perm);
            long eHasPerm = System.nanoTime();

            tStep1_AssignedRoles += (t2 - t1);
            tStep2_MapFind += (t3 - t2);
            tTotalHasPerm += (eHasPerm - sHasPerm);
        }

        System.out.println("\n------------------------------------------------------------------------------------------");
        System.out.printf("[Trace 2] hasPermission 鉴权单次平均耗时分解 (%d 角色, %d 权限网状关联, %d 次采样):%n",
                roleCount, roleCount * permCount, iterations);
        System.out.printf("  ├─ 1. 用户角色集合加载 (findById + lazy roles)        : %8.3f µs%n",
                (tStep1_AssignedRoles / (double) iterations) / 1000.0);
        System.out.printf("  ├─ 2. 中间表查询与全量权限实体水合 (actorPermissions)   : %8.3f µs%n",
                (tStep2_MapFind / (double) iterations) / 1000.0);
        System.out.printf("  └─ ★ hasPermission 全链路单次端到端总延迟               : %8.3f µs%n",
                (tTotalHasPerm / (double) iterations) / 1000.0);

        System.out.println("==========================================================================================\n");
    }
}
