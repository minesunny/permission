package com.minesunny.permission.core.benchmark;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL 生产规模级基准压测:
 * 用户规模: 1,000 Users
 * 角色规模: 1,000 Roles
 * 权限规模: 10,000 Permissions
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/postgres?currentSchema=query",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.jpa.database=postgresql",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=ERROR"
})
class PostgreSQLScaleBenchmarkTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final int USER_COUNT = 1000;
    private static final int ROLE_COUNT = 1000;
    private static final int PERMISSION_COUNT = 10000;

    // 每个 Role 绑定 20 个 Permission
    private static final int PERMS_PER_ROLE = 20;
    // 每个 User 分配 10 个 Role
    private static final int ROLES_PER_USER = 10;

    private final List<User> createdUsers = new ArrayList<>(USER_COUNT);
    private final List<Role> createdRoles = new ArrayList<>(ROLE_COUNT);
    private final List<Permission> createdPerms = new ArrayList<>(PERMISSION_COUNT);

    @Test
    @Order(1)
    @Commit
    @DisplayName("PG 规模阶梯 1: 批量实体写入 (1,000 Users, 1,000 Roles, 10,000 Permissions)")
    void step1_bulkEntityCreation() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        System.out.println("\n==========================================================================================================");
        System.out.println("                 PostgreSQL 超大规模 (1,000 Users / 1,000 Roles / 10,000 Permissions) 基准压测               ");
        System.out.println("==========================================================================================================");

        // 1.1 写入 1,000 用户
        long startUsers = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < USER_COUNT; i++) {
                String name = "pg-usr-%05d".formatted(i);
                User u = adminMgr.addUser(User.builder().actorName(name).username(name).build());
                createdUsers.add(u);
            }
            return null;
        });
        long elapsedUsers = (System.nanoTime() - startUsers) / 1_000_000;
        printMetric("1.1 写入 1,000 用户 (User)", USER_COUNT, elapsedUsers);

        // 1.2 写入 1,000 角色
        long startRoles = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < ROLE_COUNT; i++) {
                String name = "pg-role-%05d".formatted(i);
                Role r = adminMgr.addRole(Role.builder().roleName(name).build());
                createdRoles.add(r);
            }
            return null;
        });
        long elapsedRoles = (System.nanoTime() - startRoles) / 1_000_000;
        printMetric("1.2 写入 1,000 角色 (Role)", ROLE_COUNT, elapsedRoles);

        // 1.3 写入 10,000 权限 (分 10 批事务写入避免超大事务)
        long startPerms = System.nanoTime();
        int batchSize = 1000;
        for (int b = 0; b < PERMISSION_COUNT / batchSize; b++) {
            final int batchIdx = b;
            tx.execute(status -> {
                for (int i = 0; i < batchSize; i++) {
                    int seq = batchIdx * batchSize + i;
                    String name = "pg-perm-%06d".formatted(seq);
                    Permission p = adminMgr.addPermission(Permission.builder()
                            .permName(name)
                            .resource("res-%d".formatted(seq % 100))
                            .operation("op-%d".formatted(seq % 10))
                            .effect("ALLOW")
                            .build());
                    createdPerms.add(p);
                }
                return null;
            });
        }
        long elapsedPerms = (System.nanoTime() - startPerms) / 1_000_000;
        printMetric("1.3 写入 10,000 权限 (Permission)", PERMISSION_COUNT, elapsedPerms);

        assertEquals(USER_COUNT, createdUsers.size());
        assertEquals(ROLE_COUNT, createdRoles.size());
        assertEquals(PERMISSION_COUNT, createdPerms.size());
    }

    @Test
    @Order(2)
    @Commit
    @DisplayName("PG 规模阶梯 2: 批量角色-权限关系授权 (1,000 角色 × 20 权限 = 20,000 条授权映射)")
    void step2_bulkRolePermissionGranting() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int totalGrants = ROLE_COUNT * PERMS_PER_ROLE; // 20,000

        long start = System.nanoTime();
        int batchSize = 100; // 每 100 个角色提交一次事务 (2,000 次操作)
        for (int b = 0; b < ROLE_COUNT / batchSize; b++) {
            final int startR = b * batchSize;
            tx.execute(status -> {
                for (int rIdx = startR; rIdx < startR + batchSize; rIdx++) {
                    Role role = createdRoles.get(rIdx);
                    for (int p = 0; p < PERMS_PER_ROLE; p++) {
                        int permIdx = (rIdx * 7 + p) % PERMISSION_COUNT;
                        Permission perm = createdPerms.get(permIdx);
                        adminMgr.grantPermission(perm, role);
                    }
                }
                return null;
            });
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        printMetric("2.0 批量角色赋权 (grantPermission)", totalGrants, elapsed);
    }

    @Test
    @Order(3)
    @Commit
    @DisplayName("PG 规模阶梯 3: 批量用户-角色分配指派 (1,000 用户 × 10 角色 = 10,000 条指派映射)")
    void step3_bulkUserRoleAssignment() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int totalAssignments = USER_COUNT * ROLES_PER_USER; // 10,000

        long start = System.nanoTime();
        int batchSize = 100;
        for (int b = 0; b < USER_COUNT / batchSize; b++) {
            final int startU = b * batchSize;
            tx.execute(status -> {
                for (int uIdx = startU; uIdx < startU + batchSize; uIdx++) {
                    User user = createdUsers.get(uIdx);
                    for (int r = 0; r < ROLES_PER_USER; r++) {
                        int roleIdx = (uIdx * 3 + r) % ROLE_COUNT;
                        Role role = createdRoles.get(roleIdx);
                        adminMgr.assignActor(user, role);
                    }
                }
                return null;
            });
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        printMetric("3.0 批量用户指派角色 (assignActor)", totalAssignments, elapsed);
    }

    @Test
    @Order(4)
    @DisplayName("PG 规模阶梯 4: 超大规模下的密集鉴权检索吞吐评估 (hasRole, actorPermissions, hasPermission)")
    void step4_massiveReviewAndEvaluation() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 4.1 角色判定 (1,000 次)
        long startHasRole = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 1000; i++) {
                User user = createdUsers.get(i % USER_COUNT);
                Role role = createdRoles.get((i * 3) % ROLE_COUNT);
                reviewMgr.hasRole(user, role);
            }
            return null;
        });
        long elapsedHasRole = (System.nanoTime() - startHasRole) / 1_000_000;
        printMetric("4.1 角色判定 (hasRole, 1,000次)", 1000, elapsedHasRole);

        // 4.2 用户全量聚合权限检索 (100 次，每次聚合 10 个角色约 200 个权限)
        long startActorPerms = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 100; i++) {
                User user = createdUsers.get(i % USER_COUNT);
                List<Permission> perms = reviewMgr.actorPermissions(user);
                assertTrue(perms.size() > 0);
            }
            return null;
        });
        long elapsedActorPerms = (System.nanoTime() - startActorPerms) / 1_000_000;
        printMetric("4.2 用户全量聚合权限检索 (actorPermissions, 100次)", 100, elapsedActorPerms);

        // 4.3 终极细粒度鉴权 (hasPermission, 1,000次)
        long startHasPerm = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 1000; i++) {
                User user = createdUsers.get(i % USER_COUNT);
                Permission perm = createdPerms.get((i * 7) % PERMISSION_COUNT);
                reviewMgr.hasPermission(user, perm);
            }
            return null;
        });
        long elapsedHasPerm = (System.nanoTime() - startHasPerm) / 1_000_000;
        printMetric("4.3 全链路鉴权评估 (hasPermission, 1,000次)", 1000, elapsedHasPerm);
    }

    @Test
    @Order(5)
    @DisplayName("PG 规模阶梯 5: 多线程高并发访问控制鉴权压测 (16 线程, 3,200 次并发查询)")
    void step5_concurrentStressTest() throws InterruptedException {
        int threads = 16;
        int requestsPerThread = 200;
        int totalRequests = threads * requestsPerThread; // 3,200

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threads);
        AtomicInteger successCounter = new AtomicInteger(0);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        for (int t = 0; t < threads; t++) {
            final int tId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    tx.execute(status -> {
                        for (int i = 0; i < requestsPerThread; i++) {
                            int idx = (tId * requestsPerThread + i);
                            User user = createdUsers.get(idx % USER_COUNT);
                            Role role = createdRoles.get((idx * 3) % ROLE_COUNT);
                            if (reviewMgr.hasRole(user, role)) {
                                successCounter.incrementAndGet();
                            }
                        }
                        return null;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        long start = System.nanoTime();
        startLatch.countDown();
        finishLatch.await(60, TimeUnit.SECONDS);
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        executor.shutdown();

        printMetric("5.0 多线程并发判定压测 (16并发, 3,200次)", totalRequests, elapsed);
        assertEquals(totalRequests, successCounter.get());
        System.out.println("==========================================================================================================\n");
    }

    private void printMetric(String phase, int ops, long elapsedMs) {
        double avgMs = (double) elapsedMs / ops;
        double qps = elapsedMs > 0 ? (ops * 1000.0) / elapsedMs : 0;
        System.out.printf("| %-45s | 样本: %7d | 总耗时: %7d ms | 均值: %6.3f ms | 吞吐: %8.1f ops/s |\n",
                phase, ops, elapsedMs, avgMs, qps);
    }
}
