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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL 专门针对 hasRole / actorPermissions / hasPermission 进行 SQL JOIN 改写后的吞吐比对基准测试。
 */
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({PermissionAutoConfiguration.class, BaseJpaAutoConfiguration.class})
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/postgres?currentSchema=query",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.jpa.database=postgresql",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=ERROR"
})
class PostgreSQLQueryBenchmarkTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final int USER_COUNT = 100;
    private static final int ROLE_COUNT = 100;
    private static final int PERMISSION_COUNT = 1000;

    private static final int PERMS_PER_ROLE = 20;
    private static final int ROLES_PER_USER = 10;

    private final List<User> createdUsers = new ArrayList<>(USER_COUNT);
    private final List<Role> createdRoles = new ArrayList<>(ROLE_COUNT);
    private final List<Permission> createdPerms = new ArrayList<>(PERMISSION_COUNT);

    @Test
    @Commit
    @DisplayName("PG 专项对比: 单 SQL JOIN 改写后 hasRole / actorPermissions / hasPermission 吞吐评测")
    void testQueryOptimizationComparison() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        System.out.println("\n==========================================================================================================");
        System.out.println("                   PostgreSQL 单 SQL JOIN 改写后核心查询性能评测 (无新增辅助索引基准线)                     ");
        System.out.println("==========================================================================================================");

        // 1. 初始化数据
        tx.execute(status -> {
            for (int i = 0; i < USER_COUNT; i++) {
                String name = "q-usr-%04d".formatted(i);
                createdUsers.add(adminMgr.addUser(User.builder().actorName(name).username(name).build()));
            }
            for (int i = 0; i < ROLE_COUNT; i++) {
                String name = "q-role-%04d".formatted(i);
                createdRoles.add(adminMgr.addRole(Role.builder().roleName(name).build()));
            }
            for (int i = 0; i < PERMISSION_COUNT; i++) {
                String name = "q-perm-%05d".formatted(i);
                createdPerms.add(adminMgr.addPermission(Permission.builder()
                        .permName(name).resource("res").operation("op").effect("ALLOW").build()));
            }
            // 绑定关系
            for (int r = 0; r < ROLE_COUNT; r++) {
                Role role = createdRoles.get(r);
                for (int p = 0; p < PERMS_PER_ROLE; p++) {
                    Permission perm = createdPerms.get((r * 5 + p) % PERMISSION_COUNT);
                    adminMgr.grantPermission(perm, role);
                }
            }
            for (int u = 0; u < USER_COUNT; u++) {
                User user = createdUsers.get(u);
                for (int r = 0; r < ROLES_PER_USER; r++) {
                    Role role = createdRoles.get((u * 3 + r) % ROLE_COUNT);
                    adminMgr.assignActor(user, role);
                }
            }
            return null;
        });

        // 2. hasRole 评测 (1,000 次)
        long startHasRole = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 1000; i++) {
                User user = createdUsers.get(i % USER_COUNT);
                Role role = createdRoles.get((i * 3) % ROLE_COUNT);
                boolean exists = reviewMgr.hasRole(user, role);
                assertTrue(exists);
            }
            return null;
        });
        long elapsedHasRole = (System.nanoTime() - startHasRole) / 1_000_000;
        printMetric("1. hasRole (单 SQL JOIN EXISTS, 1,000次)", 1000, elapsedHasRole);

        // 3. actorPermissions 评测 (100 次全量聚合)
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
        printMetric("2. actorPermissions (单 SQL JOIN DISTINCT, 100次)", 100, elapsedActorPerms);

        // 4. hasPermission 评测 (1,000 次)
        long startHasPerm = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 1000; i++) {
                User user = createdUsers.get(i % USER_COUNT);
                Permission perm = createdPerms.get((i * 5) % PERMISSION_COUNT);
                reviewMgr.hasPermission(user, perm);
            }
            return null;
        });
        long elapsedHasPerm = (System.nanoTime() - startHasPerm) / 1_000_000;
        printMetric("3. hasPermission (单 SQL JOIN EXISTS, 1,000次)", 1000, elapsedHasPerm);

        System.out.println("==========================================================================================================\n");
    }

    private void printMetric(String phase, int ops, long elapsedMs) {
        double avgMs = (double) elapsedMs / ops;
        double qps = elapsedMs > 0 ? (ops * 1000.0) / elapsedMs : 0;
        System.out.printf("| %-55s | 样本: %7d | 总耗时: %7d ms | 均值: %6.3f ms | 吞吐: %8.1f ops/s |\n",
                phase, ops, elapsedMs, avgMs, qps);
    }
}
