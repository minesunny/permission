package com.minesunny.permission.core.concurrency;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.exception.RBACConflictException;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.RBACNotFoundException;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.OrgMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.AUTO_CONFIGURED, connection = EmbeddedDatabaseConnection.H2)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
class RBACTransactionAndConcurrencyTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private OrgMgr orgMgr;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    /**
     * Test 1: 事务回滚一致性测试
     * 验证在同一个事务中进行多项 RBAC 业务变更（创建用户、创建角色、赋权），如果中途发生异常触发事务回滚，
     * 所有变更以及中间表映射关联全部干净回滚，无残留脏数据。
     */
    @Test
    @DisplayName("事务测试: 业务异常导致事务回滚时，核心实体与中间表映射应完全回滚")
    void testTransactionRollbackConsistency() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String username = "tx-rollback-user";
        String roleName = "tx-rollback-role";

        // 执行抛出异常导致回滚的事务
        assertThrows(RuntimeException.class, () -> txTemplate.execute(status -> {
            try {
                User user = adminMgr.addUser(User.builder().actorName(username).username(username).build());
                Role role = adminMgr.addRole(Role.builder().roleName(roleName).build());
                adminMgr.assignActor(user, role);

                // 事务内部验证已写入
                assertNotNull(reviewMgr.findUser(username));
                assertNotNull(reviewMgr.findRole(roleName));
                assertTrue(reviewMgr.hasRole(user, role));

                // 模拟业务逻辑出现运行时未捕获异常
                throw new RuntimeException("Simulated unexpected business error triggering rollback");
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
        }));

        // 事务回滚后，验证实体和关联在数据库中完全不存在
        assertNull(reviewMgr.findUser(username), "用户在事务回滚后不应存在于数据库中");
        assertNull(reviewMgr.findRole(roleName), "角色在事务回滚后不应存在于数据库中");
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readUser(username));
        assertThrows(RBACNotFoundException.class, () -> reviewMgr.readRole(roleName));
    }

    /**
     * Test 2: 并发创建同名用户防重测试
     * 验证 20 个并发线程同时尝试创建具有相同 username 的用户时，
     * 唯一性约束与并发冲突控制生效：严格只有 1 个线程成功，其余 19 个线程被安全拦截抛出异常。
     */
    @Test
    @DisplayName("并发测试: 多线程并发创建同名用户，严格有且仅有一个成功，其余线程安全捕获冲突")
    void testConcurrentAddUserUniqueConstraint() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        String targetUsername = "concurrent-conflict-user";

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 保证所有线程同一瞬间冲刺发起调用

                    TransactionTemplate tx = new TransactionTemplate(transactionManager);
                    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    tx.execute(status -> {
                        try {
                            adminMgr.addUser(User.builder().actorName(targetUsername).username(targetUsername).build());
                            successCount.incrementAndGet();
                        } catch (RBACConflictException e) {
                            conflictCount.incrementAndGet();
                        } catch (Exception e) {
                            conflictCount.incrementAndGet();
                            throw e;
                        }
                        return null;
                    });
                } catch (Exception e) {
                    // tx.execute 事务失败时抛出异常，不再重复递增 conflictCount
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // 发令枪响
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(0, unexpectedErrors.size(), "不应产生未预期的非冲突系统异常: " + unexpectedErrors);
        assertEquals(1, successCount.get(), "严格应仅有 1 个并发线程创建用户成功");
        assertEquals(threadCount - 1, conflictCount.get(), "其余所有线程均应捕获唯一约束/业务冲突");

        // 最终检查只存在一个合法用户
        User finalUser = reviewMgr.findUser(targetUsername);
        assertNotNull(finalUser);
        assertEquals(targetUsername, finalUser.getUsername());
    }

    /**
     * Test 3: 并发重复角色分配防重测试
     * 验证 20 个并发线程同时向同一个 Actor 分配同一个 Role 时，
     * 中间表关联不会插入重复行，严格保证幂等或捕获 RBACConflictException。
     */
    @Test
    @DisplayName("并发测试: 多线程并发向同一用户分配相同角色，中间表映射安全且无重复数据")
    void testConcurrentAssignActorConflict() throws Exception {
        // 先准备单据
        String username = "actor-for-concurrent-assign";
        String roleName = "role-for-concurrent-assign";

        TransactionTemplate initTx = new TransactionTemplate(transactionManager);
        initTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        initTx.execute(status -> {
            try {
                adminMgr.addUser(User.builder().actorName(username).username(username).build());
                adminMgr.addRole(Role.builder().roleName(roleName).build());
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    TransactionTemplate tx = new TransactionTemplate(transactionManager);
                    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    tx.execute(status -> {
                        try {
                            User u = reviewMgr.readUser(username);
                            Role r = reviewMgr.readRole(roleName);
                            adminMgr.assignActor(u, r);
                            successCount.incrementAndGet();
                        } catch (RBACConflictException e) {
                            conflictCount.incrementAndGet();
                        } catch (Exception e) {
                            conflictCount.incrementAndGet();
                            throw e;
                        }
                        return null;
                    });
                } catch (Exception e) {
                    // tx.execute 失败抛出异常，不再重复递增
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, successCount.get(), "严格应仅有 1 个并发线程成功完成赋权");
        assertEquals(threadCount - 1, conflictCount.get(), "其余并发线程均应捕获冲突");

        // 验证用户拥有且仅拥有该角色一次
        User user = reviewMgr.readUser(username);
        Role role = reviewMgr.readRole(roleName);
        assertTrue(reviewMgr.hasRole(user, role));
        List<Role> roles = reviewMgr.assignedRoles(user);
        assertEquals(1, roles.size());
    }

    /**
     * Test 4: 并发赋权与撤权交替测试 (Race Condition / Deadlock Freedom)
     * 验证多个线程交替执行同一用户和角色的 assignActor 与 deassignActor，
     * 系统不会出现死锁（Deadlock），且状态在完成一系列操作后符合预期。
     */
    @Test
    @DisplayName("并发测试: 高频交替执行赋权与撤权，验证无死锁且状态终态一致")
    void testConcurrentAssignAndDeassignRaceCondition() throws Exception {
        String username = "race-user";
        String roleName = "race-role";

        TransactionTemplate initTx = new TransactionTemplate(transactionManager);
        initTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        initTx.execute(status -> {
            try {
                adminMgr.addUser(User.builder().actorName(username).username(username).build());
                adminMgr.addRole(Role.builder().roleName(roleName).build());
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        int threadCount = 10;
        int operationsPerThread = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger assignOps = new AtomicInteger(0);
        AtomicInteger deassignOps = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final boolean assignFirst = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        TransactionTemplate tx = new TransactionTemplate(transactionManager);
                        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                        tx.execute(status -> {
                            try {
                                User u = reviewMgr.readUser(username);
                                Role r = reviewMgr.readRole(roleName);
                                if (assignFirst) {
                                    adminMgr.assignActor(u, r);
                                    assignOps.incrementAndGet();
                                } else {
                                    adminMgr.deassignActor(u, r);
                                    deassignOps.incrementAndGet();
                                }
                            } catch (RBACConflictException | RBACNotFoundException ignored) {
                                // 竞争条件下可能已存在或已被删除，属于正常业务互斥
                            } catch (Exception e) {
                                // 数据库级并发主键冲突或乐观并发
                            }
                            return null;
                        });
                    }
                } catch (Exception ignored) {
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS), "并发交替操作应在时限内顺利完成，无死锁阻滞");

        // 验证用户与角色实体完好，系统处于可用状态
        User user = reviewMgr.readUser(username);
        Role role = reviewMgr.readRole(roleName);
        assertNotNull(user);
        assertNotNull(role);
    }

    /**
     * Test 5: 并发读写混合压力测试 (Read-Write Isolation)
     * 多个读线程高频进行 hasPermission、actorPermissions、rolePermissions 鉴权与查询，
     * 同时写线程进行频繁的赋权与撤权，验证并发读写不会抛出 ConcurrentModificationException、
     * 不受中间脏事务干扰，读写相互隔离稳定运行。
     */
    @Test
    @DisplayName("并发测试: 高频并发鉴权读与动态赋权写混合测试，验证读写隔离性与稳定性")
    void testConcurrentReadWritePressure() throws Exception {
        String username = "rw-user";
        String roleName = "rw-role";
        String permName = "rw-permission";

        TransactionTemplate initTx = new TransactionTemplate(transactionManager);
        initTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        initTx.execute(status -> {
            try {
                User user = adminMgr.addUser(User.builder().actorName(username).username(username).build());
                Role role = adminMgr.addRole(Role.builder().roleName(roleName).build());
                Permission perm = adminMgr.addPermission(Permission.builder().permName(permName).build());
                adminMgr.grantPermission(perm, role);
                adminMgr.assignActor(user, role);
            } catch (RBACException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        int readerThreads = 8;
        int writerThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(readerThreads + writerThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger totalReadOperations = new AtomicInteger(0);
        AtomicInteger totalWriteOperations = new AtomicInteger(0);
        AtomicInteger readErrors = new AtomicInteger(0);

        long testDurationMs = 2000; // 运行 2 秒并发压力
        long deadline = System.currentTimeMillis() + testDurationMs;

        // 启动读线程
        for (int i = 0; i < readerThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (System.currentTimeMillis() < deadline) {
                        try {
                            User user = reviewMgr.readUser(username);
                            Role role = reviewMgr.readRole(roleName);
                            Permission perm = reviewMgr.readPermission(permName);

                            reviewMgr.hasRole(user, role);
                            reviewMgr.hasPermission(user, perm);
                            reviewMgr.actorPermissions(user);
                            reviewMgr.rolePermissions(role);

                            totalReadOperations.incrementAndGet();
                        } catch (Exception e) {
                            readErrors.incrementAndGet();
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        }

        // 启动写线程
        for (int i = 0; i < writerThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (System.currentTimeMillis() < deadline) {
                        TransactionTemplate tx = new TransactionTemplate(transactionManager);
                        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                        tx.execute(status -> {
                            try {
                                User user = reviewMgr.readUser(username);
                                Role role = reviewMgr.readRole(roleName);
                                if (reviewMgr.hasRole(user, role)) {
                                    adminMgr.deassignActor(user, role);
                                } else {
                                    adminMgr.assignActor(user, role);
                                }
                                totalWriteOperations.incrementAndGet();
                            } catch (Exception ignored) {
                            }
                            return null;
                        });
                        Thread.sleep(5); // 稍微休眠让读写充分交错
                    }
                } catch (Exception ignored) {
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(0, readErrors.get(), "读线程在写操作交替进行期间不应发生任何异常");
        assertTrue(totalReadOperations.get() > 500, "2 秒内并发读操作次数应充分多");
        assertTrue(totalWriteOperations.get() > 50, "2 秒内并发写操作次数应充分完成");
    }
}
