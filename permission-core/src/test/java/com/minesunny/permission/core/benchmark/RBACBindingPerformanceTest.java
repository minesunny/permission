package com.minesunny.permission.core.benchmark;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RBAC 大量角色、权限、用户绑定与解绑性能基准测试。
 * <p>
 * 压测维度涵盖：
 * 1. 批量实体初始化 (User / Role / Permission)
 * 2. 批量角色与权限绑定 (Role-Permission Grant)
 * 3. 批量用户与角色绑定 (User-Role Assign)
 * 4. 高密度关联下的鉴权与审查查询吞吐 (hasRole, hasPermission, actorPermissions, permissionActors)
 * 5. 批量用户与角色解绑 (User-Role Deassign)
 * 6. 批量角色与权限解绑 (Role-Permission Revoke)
 * 7. 多线程并发鉴权与绑定压力测试 (Concurrent Stress Test)
 * 8. 关联关系中间表级联清理 (Cascade Delete Cleanup)
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
@TestPropertySource(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=ERROR"
})
class RBACBindingPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(RBACBindingPerformanceTest.class);

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final int USER_COUNT = 100;
    private static final int ROLE_COUNT = 50;
    private static final int PERMISSION_COUNT = 200;

    // 每个 Role 绑定 20 个 Permission -> 50 * 20 = 1,000 次 Role-Perm 绑定
    private static final int PERMS_PER_ROLE = 20;

    // 每个 User 绑定 10 个 Role -> 100 * 10 = 1,000 次 User-Role 绑定
    private static final int ROLES_PER_USER = 10;

    private final List<User> createdUsers = new ArrayList<>();
    private final List<Role> createdRoles = new ArrayList<>();
    private final List<Permission> createdPerms = new ArrayList<>();

    @Test
    @Order(1)
    @Commit
    @DisplayName("基准阶段 1: 批量实体创建压测 (User, Role, Permission)")
    void step1_bulkEntityCreation() throws RBACException {
        // 1. 创建 Users
        long startUsers = System.nanoTime();
        for (int i = 0; i < USER_COUNT; i++) {
            String name = "perf-usr-%04d".formatted(i);
            User u = adminMgr.addUser(User.builder().actorName(name).username(name).build());
            createdUsers.add(u);
        }
        long userElapsedMs = (System.nanoTime() - startUsers) / 1_000_000;

        // 2. 创建 Roles
        long startRoles = System.nanoTime();
        for (int i = 0; i < ROLE_COUNT; i++) {
            String name = "perf-role-%03d".formatted(i);
            Role r = adminMgr.addRole(Role.builder().roleName(name).build());
            createdRoles.add(r);
        }
        long roleElapsedMs = (System.nanoTime() - startRoles) / 1_000_000;

        // 3. 创建 Permissions
        long startPerms = System.nanoTime();
        for (int i = 0; i < PERMISSION_COUNT; i++) {
            String name = "perf-perm-%04d".formatted(i);
            Permission p = adminMgr.addPermission(Permission.builder()
                    .permName(name).resource("res-" + (i % 20)).operation("op-" + (i % 5)).build());
            createdPerms.add(p);
        }
        long permElapsedMs = (System.nanoTime() - startPerms) / 1_000_000;

        printMetric("1.1 批量创建用户 (User)", USER_COUNT, userElapsedMs);
        printMetric("1.2 批量创建角色 (Role)", ROLE_COUNT, roleElapsedMs);
        printMetric("1.3 批量创建权限 (Permission)", PERMISSION_COUNT, permElapsedMs);

        assertEquals(USER_COUNT, createdUsers.size());
        assertEquals(ROLE_COUNT, createdRoles.size());
        assertEquals(PERMISSION_COUNT, createdPerms.size());
    }

    @Test
    @Order(2)
    @Commit
    @DisplayName("基准阶段 2: 批量角色-权限授权绑定压测 (grantPermission)")
    void step2_bulkRolePermissionGranting() throws RBACException {
        int totalOps = ROLE_COUNT * PERMS_PER_ROLE; // 1,000
        long start = System.nanoTime();

        for (int rIdx = 0; rIdx < ROLE_COUNT; rIdx++) {
            Role role = createdRoles.get(rIdx);
            for (int p = 0; p < PERMS_PER_ROLE; p++) {
                int permIdx = (rIdx * 3 + p) % PERMISSION_COUNT;
                Permission perm = createdPerms.get(permIdx);
                adminMgr.grantPermission(perm, role);
            }
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        printMetric("2.0 角色-权限绑定 (grantPermission)", totalOps, elapsedMs);

        // 验证部分绑定正确性
        Role firstRole = createdRoles.get(0);
        List<Permission> rolePerms = reviewMgr.rolePermissions(firstRole);
        assertEquals(PERMS_PER_ROLE, rolePerms.size());
    }

    @Test
    @Order(3)
    @Commit
    @DisplayName("基准阶段 3: 批量用户-角色分配绑定压测 (assignActor)")
    void step3_bulkUserRoleAssignment() throws RBACException {
        int totalOps = USER_COUNT * ROLES_PER_USER; // 1,000
        long start = System.nanoTime();

        for (int uIdx = 0; uIdx < USER_COUNT; uIdx++) {
            User user = createdUsers.get(uIdx);
            for (int r = 0; rIdx(r, uIdx) < ROLES_PER_USER; r++) {
                int roleIdx = (uIdx * 2 + r) % ROLE_COUNT;
                Role role = createdRoles.get(roleIdx);
                adminMgr.assignActor(user, role);
            }
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        printMetric("3.0 用户-角色分配 (assignActor)", totalOps, elapsedMs);

        // 验证用户角色数量
        User sampleUser = createdUsers.get(0);
        List<Role> assigned = reviewMgr.assignedRoles(sampleUser);
        assertEquals(ROLES_PER_USER, assigned.size());
    }

    private int rIdx(int r, int uIdx) {
        return r;
    }

    @Test
    @Order(4)
    @DisplayName("基准阶段 4: 高密度绑定关联下的权限与鉴权查询压测")
    void step4_queryAndAuthorizationPerformance() throws RBACException {
        // 4.1 hasRole 查询 (1,000 次)
        int hasRoleOps = 1_000;
        long startRoleCheck = System.nanoTime();
        int roleHitCount = 0;
        for (int i = 0; i < hasRoleOps; i++) {
            User user = createdUsers.get(i % USER_COUNT);
            Role role = createdRoles.get(i % ROLE_COUNT);
            if (reviewMgr.hasRole(user, role)) {
                roleHitCount++;
            }
        }
        long roleCheckMs = (System.nanoTime() - startRoleCheck) / 1_000_000;
        printMetric("4.1 角色判定 (hasRole)", hasRoleOps, roleCheckMs);
        assertTrue(roleHitCount > 0);

        // 4.2 hasPermission 鉴权查询 (1,000 次, 经历 User -> Roles -> Permissions 链式解析)
        int hasPermOps = 1_000;
        long startPermCheck = System.nanoTime();
        int permHitCount = 0;
        for (int i = 0; i < hasPermOps; i++) {
            User user = createdUsers.get(i % USER_COUNT);
            Permission perm = createdPerms.get(i % PERMISSION_COUNT);
            if (reviewMgr.hasPermission(user, perm)) {
                permHitCount++;
            }
        }
        long permCheckMs = (System.nanoTime() - startPermCheck) / 1_000_000;
        printMetric("4.2 全链路权限鉴权 (hasPermission)", hasPermOps, permCheckMs);

        // 4.3 用户聚合权限全量查询 actorPermissions (100 次, 覆盖全部 100 个用户)
        long startActorPerms = System.nanoTime();
        long totalAggregatedPerms = 0;
        for (User user : createdUsers) {
            List<Permission> perms = reviewMgr.actorPermissions(user);
            totalAggregatedPerms += perms.size();
        }
        long actorPermsMs = (System.nanoTime() - startActorPerms) / 1_000_000;
        printMetric("4.3 用户全量权限聚合 (actorPermissions)", USER_COUNT, actorPermsMs);
        assertTrue(totalAggregatedPerms > 0);

        // 4.4 权限反查用户 permissionActors (50 次)
        int permActorOps = 50;
        long startPermActors = System.nanoTime();
        for (int i = 0; i < permActorOps; i++) {
            Permission perm = createdPerms.get(i);
            reviewMgr.permissionActors(perm);
        }
        long permActorsMs = (System.nanoTime() - startPermActors) / 1_000_000;
        printMetric("4.4 权限反查关联用户 (permissionActors)", permActorOps, permActorsMs);
    }

    @Test
    @Order(5)
    @DisplayName("基准阶段 5: 多线程高并发只读鉴权吞吐测试 (Concurrent Stress Test)")
    void step5_concurrentReadStressTest() throws InterruptedException {
        int threadCount = 10;
        int totalRequests = 2_000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        long start = System.nanoTime();
        for (int i = 0; i < totalRequests; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    txTemplate.execute(status -> {
                        try {
                            User user = createdUsers.get(idx % USER_COUNT);
                            Permission perm = createdPerms.get(idx % PERMISSION_COUNT);
                            reviewMgr.hasPermission(user, perm);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            log.error("Concurrent query error", e);
                        }
                        return null;
                    });
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        executor.shutdown();

        printMetric("5.0 并发鉴权压力测试 (10线程 hasPermission)", totalRequests, elapsedMs);
        assertEquals(totalRequests, successCount.get());
    }

    @Test
    @Order(6)
    @Commit
    @DisplayName("基准阶段 6: 批量用户-角色解绑压测 (deassignActor)")
    void step6_bulkUserRoleDeassignment() throws RBACException {
        int totalOps = USER_COUNT * ROLES_PER_USER; // 1,000
        long start = System.nanoTime();

        for (int uIdx = 0; uIdx < USER_COUNT; uIdx++) {
            User user = createdUsers.get(uIdx);
            for (int r = 0; r < ROLES_PER_USER; r++) {
                int roleIdx = (uIdx * 2 + r) % ROLE_COUNT;
                Role role = createdRoles.get(roleIdx);
                adminMgr.deassignActor(user, role);
            }
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        printMetric("6.0 用户-角色解绑 (deassignActor)", totalOps, elapsedMs);

        // 验证用户角色已清空
        User sampleUser = createdUsers.get(0);
        List<Role> assigned = reviewMgr.assignedRoles(sampleUser);
        assertTrue(assigned.isEmpty());
    }

    @Test
    @Order(7)
    @Commit
    @DisplayName("基准阶段 7: 批量角色-权限撤销解绑压测 (revokePermission)")
    void step7_bulkRolePermissionRevocation() throws RBACException {
        int totalOps = ROLE_COUNT * PERMS_PER_ROLE; // 1,000
        long start = System.nanoTime();

        for (int rIdx = 0; rIdx < ROLE_COUNT; rIdx++) {
            Role role = createdRoles.get(rIdx);
            for (int p = 0; p < PERMS_PER_ROLE; p++) {
                int permIdx = (rIdx * 3 + p) % PERMISSION_COUNT;
                Permission perm = createdPerms.get(permIdx);
                adminMgr.revokePermission(perm, role);
            }
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        printMetric("7.0 角色-权限撤销 (revokePermission)", totalOps, elapsedMs);

        // 验证角色权限已清空
        Role firstRole = createdRoles.get(0);
        List<Permission> rolePerms = reviewMgr.rolePermissions(firstRole);
        assertTrue(rolePerms.isEmpty());
    }

    @Test
    @Order(8)
    @Commit
    @DisplayName("基准阶段 8: 级联实体删除及中间表清理性能")
    void step8_cascadeDeletionCleanup() throws RBACException {
        // 先给一部分用户和角色重新绑定关系，然后直接通过 deleteRole / deleteUser 测试级联清理速度
        int sampleRoles = 20;
        for (int i = 0; i < sampleRoles; i++) {
            Role role = createdRoles.get(i);
            for (int p = 0; p < 10; p++) {
                adminMgr.grantPermission(createdPerms.get(p), role);
            }
            for (int u = 0; u < 10; u++) {
                adminMgr.assignActor(createdUsers.get(u), role);
            }
        }

        // 测试 deleteRole 级联清理中间表
        long startRoleDelete = System.nanoTime();
        for (int i = 0; i < sampleRoles; i++) {
            adminMgr.deleteRole(createdRoles.get(i));
        }
        long roleDeleteMs = (System.nanoTime() - startRoleDelete) / 1_000_000;
        printMetric("8.1 密集关联角色级联删除与中间表清理", sampleRoles, roleDeleteMs);

        // 测试 deleteUser 级联清理
        int sampleUsers = 20;
        long startUserDelete = System.nanoTime();
        for (int i = 0; i < sampleUsers; i++) {
            adminMgr.deleteUser(createdUsers.get(i));
        }
        long userDeleteMs = (System.nanoTime() - startUserDelete) / 1_000_000;
        printMetric("8.2 密集关联用户级联删除与中间表清理", sampleUsers, userDeleteMs);
    }

    private static void printMetric(String stage, int ops, long elapsedMs) {
        double elapsedSec = Math.max(elapsedMs / 1000.0, 0.0001);
        double throughput = ops / elapsedSec;
        double avgLatency = (double) elapsedMs / ops;
        System.out.printf("| %-38s | %8d | %12d ms | %11.3f ms | %14.1f ops/s |%n",
                stage, ops, elapsedMs, avgLatency, throughput);
    }
}
