package com.minesunny.permission.core.benchmark;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.manager.AdminMgr;
import com.minesunny.permission.core.manager.ReviewMgr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三组技术方案性能对决基准测试:
 * 方案 A: 单 SQL JOIN 连表（默认复合主键，无反向索引）
 * 方案 B: 单 SQL JOIN 连表 + 中间表反向覆盖索引
 * 方案 C: PostgreSQL 物化视图 (Materialized View) 单表索引直查
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
class ThreeWayOptimizationBenchmarkTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("方案横向大比武: 方案A (基线JOIN) vs 方案B (反向索引) vs 方案C (物化视图)")
    void runThreeWayBenchmark() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        System.out.println("\n==========================================================================================================");
        System.out.println("                 PostgreSQL 三组优化方案极限性能对比 (方案A vs 方案B vs 方案C)                            ");
        System.out.println("==========================================================================================================");

        // -----------------------------------------------------------------------------------------
        // 0. 数据初始化 (100 Users, 100 Roles, 1,000 Permissions, 2,000 PA映射, 1,000 UA映射)
        // -----------------------------------------------------------------------------------------
        tx.execute(status -> {
            for (int i = 0; i < USER_COUNT; i++) {
                String name = "u-%04d".formatted(i);
                createdUsers.add(adminMgr.addUser(User.builder().actorName(name).username(name).build()));
            }
            for (int i = 0; i < ROLE_COUNT; i++) {
                String name = "r-%04d".formatted(i);
                createdRoles.add(adminMgr.addRole(Role.builder().roleName(name).build()));
            }
            for (int i = 0; i < PERMISSION_COUNT; i++) {
                String name = "p-%05d".formatted(i);
                createdPerms.add(adminMgr.addPermission(Permission.builder()
                        .permName(name).resource("res").operation("op").effect("ALLOW").build()));
            }
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

        // -----------------------------------------------------------------------------------------
        // 方案 A 评测 (单 SQL JOIN 连表，无额外反向索引)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n--- [方案 A]: 单 SQL JOIN 连表 (当前默认配置，仅主键 (role_id, actor_id)) ---");
        benchmarkPlanA(tx);

        // -----------------------------------------------------------------------------------------
        // 方案 B 评测 (单 SQL JOIN 连表 + 中间表反向覆盖索引)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n--- [方案 B]: 单 SQL JOIN 连表 + 中间表反向覆盖索引 ---");
        jdbcTemplate.execute("CREATE INDEX idx_role_actor_actor_id ON t_role_actor_map (actor_id);");
        jdbcTemplate.execute("CREATE INDEX idx_role_perm_perm_id ON t_role_permission_map (permission_id);");
        jdbcTemplate.execute("ANALYZE t_role_actor_map;");
        jdbcTemplate.execute("ANALYZE t_role_permission_map;");
        benchmarkPlanB(tx);

        // -----------------------------------------------------------------------------------------
        // 方案 C 评测 (PostgreSQL 物化视图 Materialized View 单表索引直查)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n--- [方案 C]: PostgreSQL 物化视图 (Materialized View) 单表索引直查 ---");
        jdbcTemplate.execute("""
            CREATE MATERIALIZED VIEW mv_user_permission AS
            SELECT DISTINCT
                ra.actor_id AS user_id,
                p.id AS permission_id,
                p.perm_name,
                p.operation,
                p.resource,
                p.effect
            FROM t_role_actor_map ra
            JOIN t_role r ON ra.role_id = r.id AND r.delete_at IS NULL
            JOIN t_role_permission_map rp ON r.id = rp.role_id
            JOIN t_permission p ON rp.permission_id = p.id AND p.delete_at IS NULL
            WHERE (r.begin_date_time IS NULL OR r.begin_date_time <= CURRENT_TIMESTAMP)
              AND (r.end_date_time IS NULL OR r.end_date_time >= CURRENT_TIMESTAMP);
        """);
        jdbcTemplate.execute("CREATE UNIQUE INDEX idx_mv_user_perm ON mv_user_permission (user_id, permission_id);");
        jdbcTemplate.execute("CREATE INDEX idx_mv_user_perm_name ON mv_user_permission (user_id, perm_name);");
        jdbcTemplate.execute("ANALYZE mv_user_permission;");
        benchmarkPlanC(tx);

        System.out.println("==========================================================================================================\n");
    }

    private void benchmarkPlanA(TransactionTemplate tx) {
        // hasRole (1,000 次)
        long sRole = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 1000; i++) {
                reviewMgr.hasRole(createdUsers.get(i % USER_COUNT), createdRoles.get((i * 3) % ROLE_COUNT));
            }
            return null;
        });
        printMetric("A-1. hasRole (1,000次)", 1000, (System.nanoTime() - sRole) / 1_000_000);

        // actorPermissions (100 次全量)
        long sActorPerms = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 100; i++) {
                reviewMgr.actorPermissions(createdUsers.get(i % USER_COUNT));
            }
            return null;
        });
        printMetric("A-2. actorPermissions 全量聚合 (100次)", 100, (System.nanoTime() - sActorPerms) / 1_000_000);

        // hasPermission (1,000 次)
        long sPerm = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 1000; i++) {
                reviewMgr.hasPermission(createdUsers.get(i % USER_COUNT), createdPerms.get((i * 5) % PERMISSION_COUNT));
            }
            return null;
        });
        printMetric("A-3. hasPermission (1,000次)", 1000, (System.nanoTime() - sPerm) / 1_000_000);
    }

    private void benchmarkPlanB(TransactionTemplate tx) {
        // hasRole (1,000 次)
        long sRole = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 1000; i++) {
                reviewMgr.hasRole(createdUsers.get(i % USER_COUNT), createdRoles.get((i * 3) % ROLE_COUNT));
            }
            return null;
        });
        printMetric("B-1. hasRole [加索引] (1,000次)", 1000, (System.nanoTime() - sRole) / 1_000_000);

        // actorPermissions (100 次全量)
        long sActorPerms = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 100; i++) {
                reviewMgr.actorPermissions(createdUsers.get(i % USER_COUNT));
            }
            return null;
        });
        printMetric("B-2. actorPermissions 全量聚合 [加索引] (100次)", 100, (System.nanoTime() - sActorPerms) / 1_000_000);

        // hasPermission (1,000 次)
        long sPerm = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 1000; i++) {
                reviewMgr.hasPermission(createdUsers.get(i % USER_COUNT), createdPerms.get((i * 5) % PERMISSION_COUNT));
            }
            return null;
        });
        printMetric("B-3. hasPermission [加索引] (1,000次)", 1000, (System.nanoTime() - sPerm) / 1_000_000);
    }

    private void benchmarkPlanC(TransactionTemplate tx) {
        // C-1. hasPermission 物化视图单表直查 (1,000 次)
        long sPerm = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 1000; i++) {
                Long uid = createdUsers.get(i % USER_COUNT).getId();
                String pname = createdPerms.get((i * 5) % PERMISSION_COUNT).getPermName();
                Boolean exists = jdbcTemplate.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM mv_user_permission WHERE user_id = ? AND perm_name = ?)",
                        Boolean.class, uid, pname);
                assertTrue(exists != null);
            }
            return null;
        });
        printMetric("C-1. hasPermission [物化视图单表索引] (1,000次)", 1000, (System.nanoTime() - sPerm) / 1_000_000);

        // C-2. actorPermissions 物化视图单表直查 (100 次)
        long sActorPerms = System.nanoTime();
        tx.execute(status -> {
            for (int i = 0; i < 100; i++) {
                Long uid = createdUsers.get(i % USER_COUNT).getId();
                var list = jdbcTemplate.queryForList(
                        "SELECT permission_id, perm_name, operation, resource FROM mv_user_permission WHERE user_id = ?",
                        uid);
                assertTrue(!list.isEmpty());
            }
            return null;
        });
        printMetric("C-2. actorPermissions 全量聚合 [物化视图单表扫描] (100次)", 100, (System.nanoTime() - sActorPerms) / 1_000_000);
    }

    private void printMetric(String phase, int ops, long elapsedMs) {
        double avgMs = (double) elapsedMs / ops;
        double qps = elapsedMs > 0 ? (ops * 1000.0) / elapsedMs : 0;
        System.out.printf("| %-55s | 样本: %7d | 总耗时: %7d ms | 均值: %6.3f ms | 吞吐: %8.1f ops/s |\n",
                phase, ops, elapsedMs, avgMs, qps);
    }
}
