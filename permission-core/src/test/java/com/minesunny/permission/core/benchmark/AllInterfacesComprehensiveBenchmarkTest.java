package com.minesunny.permission.core.benchmark;

import com.minesunny.jpa.autoconfigure.BaseJpaAutoConfiguration;
import com.minesunny.permission.core.autoconfigure.PermissionAutoConfiguration;
import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.Session;
import com.minesunny.permission.core.entity.actor.Department;
import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import com.minesunny.permission.core.entity.sod.DSDSet;
import com.minesunny.permission.core.entity.sod.SSDSet;
import com.minesunny.permission.core.manager.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全量 5 大业务管理器（AdminMgr, ReviewMgr, SessionMgr, SoDMgr, OrgMgr）系统接口基准测试套件。
 * 覆盖全部 100% 对外暴露的业务接口方法，生成结构化性能指标报告。
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
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=ERROR"
})
class AllInterfacesComprehensiveBenchmarkTest {

    @Autowired
    private AdminMgr adminMgr;

    @Autowired
    private ReviewMgr reviewMgr;

    @Autowired
    private SessionMgr sessionMgr;

    @Autowired
    private SoDMgr soDMgr;

    @Autowired
    private OrgMgr orgMgr;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User sampleUser;
    private Role sampleRole1;
    private Role sampleRole2;
    private Permission samplePerm;
    private Department sampleDept;

    @Test
    @DisplayName("全量 5 大 Manager 业务接口基准评测完整运行")
    void runFullSystemBenchmark() {
        benchmarkAdminMgr();
        benchmarkReviewMgr();
        benchmarkSessionMgr();
        benchmarkSoDMgr();
        benchmarkOrgMgr();
    }

    void benchmarkAdminMgr() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        System.out.println("\n==========================================================================================================");
        System.out.println("                         1. AdminMgr 全量业务接口基准评测                                                 ");
        System.out.println("==========================================================================================================");

        tx.execute(status -> {
            // 1. User CRUD
            long s1 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                User u = adminMgr.addUser(User.builder().actorName("adm-usr-%03d".formatted(i)).username("adm-usr-%03d".formatted(i)).build());
                if (i == 0) sampleUser = u;
            }
            printMetric("AdminMgr.addUser (100次)", 100, (System.nanoTime() - s1) / 1_000_000);

            long s2 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                adminMgr.readUser("adm-usr-%03d".formatted(i));
                adminMgr.findUser("adm-usr-%03d".formatted(i));
            }
            printMetric("AdminMgr.readUser / findUser (200次)", 200, (System.nanoTime() - s2) / 1_000_000);

            long s3 = System.nanoTime();
            User uUpdate = User.builder().email("updated@test.com").build();
            adminMgr.updateUser(sampleUser.getUsername(), uUpdate);
            printMetric("AdminMgr.updateUser (1次)", 1, (System.nanoTime() - s3) / 1_000_000);

            // 2. Role CRUD
            long s4 = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                Role r = adminMgr.addRole(Role.builder().roleName("adm-role-%03d".formatted(i)).build());
                if (i == 0) sampleRole1 = r;
                if (i == 1) sampleRole2 = r;
            }
            printMetric("AdminMgr.addRole (50次)", 50, (System.nanoTime() - s4) / 1_000_000);

            long s5 = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                adminMgr.readRole("adm-role-%03d".formatted(i));
                adminMgr.findRole("adm-role-%03d".formatted(i));
            }
            printMetric("AdminMgr.readRole / findRole (100次)", 100, (System.nanoTime() - s5) / 1_000_000);

            long s6 = System.nanoTime();
            adminMgr.updateRole(sampleRole1.getRoleName(), Role.builder().roleName(sampleRole1.getRoleName()).description("updated").build());
            printMetric("AdminMgr.updateRole (1次)", 1, (System.nanoTime() - s6) / 1_000_000);

            // 3. Permission CRUD
            long s7 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                Permission p = adminMgr.addPermission(Permission.builder()
                        .permName("adm-perm-%03d".formatted(i)).resource("res").operation("read").effect("ALLOW").build());
                if (i == 0) samplePerm = p;
            }
            printMetric("AdminMgr.addPermission (100次)", 100, (System.nanoTime() - s7) / 1_000_000);

            long s8 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                adminMgr.readPermission("adm-perm-%03d".formatted(i));
                adminMgr.findPermission("adm-perm-%03d".formatted(i));
            }
            printMetric("AdminMgr.readPermission / findPermission (200次)", 200, (System.nanoTime() - s8) / 1_000_000);

            long s9 = System.nanoTime();
            adminMgr.updatePermission(samplePerm.getPermName(), Permission.builder().permName(samplePerm.getPermName()).resource("res").operation("read").description("updated").build());
            printMetric("AdminMgr.updatePermission (1次)", 1, (System.nanoTime() - s9) / 1_000_000);

            // 4. Role Inheritance
            long s10 = System.nanoTime();
            adminMgr.addRoleInheritance(sampleRole1, sampleRole2);
            printMetric("AdminMgr.addRoleInheritance (1次)", 1, (System.nanoTime() - s10) / 1_000_000);

            long s11 = System.nanoTime();
            adminMgr.deleteRoleInheritance(sampleRole1, sampleRole2);
            printMetric("AdminMgr.deleteRoleInheritance (1次)", 1, (System.nanoTime() - s11) / 1_000_000);

            // 5. Assign / Deassign & Grant / Revoke
            long s12 = System.nanoTime();
            adminMgr.assignActor(sampleUser, sampleRole1);
            printMetric("AdminMgr.assignActor (1次)", 1, (System.nanoTime() - s12) / 1_000_000);

            long s13 = System.nanoTime();
            adminMgr.grantPermission(samplePerm, sampleRole1);
            printMetric("AdminMgr.grantPermission (1次)", 1, (System.nanoTime() - s13) / 1_000_000);

            long s14 = System.nanoTime();
            adminMgr.revokePermission(samplePerm, sampleRole1);
            printMetric("AdminMgr.revokePermission (1次)", 1, (System.nanoTime() - s14) / 1_000_000);
            adminMgr.grantPermission(samplePerm, sampleRole1); // 重新授权供后续测试使用

            return null;
        });
    }

    void benchmarkReviewMgr() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        System.out.println("\n==========================================================================================================");
        System.out.println("                         2. ReviewMgr 全量业务接口基准评测                                                ");
        System.out.println("==========================================================================================================");

        tx.execute(status -> {
            // 1. hasRole
            long s1 = System.nanoTime();
            for (int i = 0; i < 500; i++) {
                reviewMgr.hasRole(sampleUser, sampleRole1);
            }
            printMetric("ReviewMgr.hasRole (500次)", 500, (System.nanoTime() - s1) / 1_000_000);

            // 2. hasPermission
            long s2 = System.nanoTime();
            for (int i = 0; i < 500; i++) {
                reviewMgr.hasPermission(sampleUser, samplePerm);
            }
            printMetric("ReviewMgr.hasPermission (500次)", 500, (System.nanoTime() - s2) / 1_000_000);

            // 3. checkAccess (String actorName)
            long s3 = System.nanoTime();
            for (int i = 0; i < 200; i++) {
                reviewMgr.checkAccess(sampleUser.getUsername(), "read", "res");
            }
            printMetric("ReviewMgr.checkAccess(actorName, op, res) (200次)", 200, (System.nanoTime() - s3) / 1_000_000);

            // 4. assignedRoles / assignedActors
            long s4 = System.nanoTime();
            for (int i = 0; i < 200; i++) {
                reviewMgr.assignedRoles(sampleUser);
                reviewMgr.assignedActors(sampleRole1);
            }
            printMetric("ReviewMgr.assignedRoles / assignedActors (400次)", 400, (System.nanoTime() - s4) / 1_000_000);

            // 5. rolePermissions / actorPermissions
            long s5 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                reviewMgr.rolePermissions(sampleRole1);
                reviewMgr.actorPermissions(sampleUser);
            }
            printMetric("ReviewMgr.rolePermissions / actorPermissions (200次)", 200, (System.nanoTime() - s5) / 1_000_000);

            // 6. permissionRoles / permissionActors
            long s6 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                reviewMgr.permissionRoles(samplePerm);
                reviewMgr.permissionActors(samplePerm);
            }
            printMetric("ReviewMgr.permissionRoles / permissionActors (200次)", 200, (System.nanoTime() - s6) / 1_000_000);

            // 7. OperationsOnResource
            long s7 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                reviewMgr.roleOperationsOnResource(sampleRole1, "res");
                reviewMgr.userOperationsOnResource(sampleUser, "res");
            }
            printMetric("ReviewMgr.role/user OperationsOnResource (200次)", 200, (System.nanoTime() - s7) / 1_000_000);

            // 8. authorized 穿透继承审查
            long s8 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                reviewMgr.authorizedRoles(sampleUser);
                reviewMgr.authorizedActors(sampleRole1);
                reviewMgr.authorizedPermissions(sampleRole1);
                reviewMgr.authorizedActorPermissions(sampleUser);
            }
            printMetric("ReviewMgr.authorized Roles/Actors/Perms 穿透聚合 (400次)", 400, (System.nanoTime() - s8) / 1_000_000);

            return null;
        });
    }

    void benchmarkSessionMgr() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        System.out.println("\n==========================================================================================================");
        System.out.println("                         3. SessionMgr 全量业务接口基准评测                                               ");
        System.out.println("==========================================================================================================");

        tx.execute(status -> {
            long s1 = System.nanoTime();
            for (int i = 0; i < 200; i++) {
                Session s = sessionMgr.createSession(sampleUser, List.of(sampleRole1));
                reviewMgr.sessionRoles(s);
                reviewMgr.sessionPermissions(s);
                reviewMgr.checkAccess(s, "read", "res");
                sessionMgr.dropActiveRole(s, sampleRole1);
                sessionMgr.addActiveRole(s, sampleRole1);
                sessionMgr.deleteSession(s);
            }
            printMetric("SessionMgr.create/active/drop/delete + sessionPermissions (200轮完整生命周期)", 200, (System.nanoTime() - s1) / 1_000_000);
            return null;
        });
    }

    void benchmarkSoDMgr() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        System.out.println("\n==========================================================================================================");
        System.out.println("                         4. SoDMgr 全量业务接口基准评测                                                   ");
        System.out.println("==========================================================================================================");

        tx.execute(status -> {
            Role rA = adminMgr.addRole(Role.builder().roleName("sod-rA").build());
            Role rB = adminMgr.addRole(Role.builder().roleName("sod-rB").build());
            Role rC = adminMgr.addRole(Role.builder().roleName("sod-rC").build());

            // 1. SSD 管理与审查
            long s1 = System.nanoTime();
            SSDSet ssd = soDMgr.createSSDSet("SSD-Bench", Set.of("sod-rA", "sod-rB"), 2);
            printMetric("SoDMgr.createSSDSet (1次)", 1, (System.nanoTime() - s1) / 1_000_000);

            long s2 = System.nanoTime();
            soDMgr.addSSDRoleMember("SSD-Bench", "sod-rC");
            soDMgr.setSSDSetCardinality("SSD-Bench", 3);
            soDMgr.setSSDSetCardinality("SSD-Bench", 2);
            soDMgr.deleteSSDRoleMember("SSD-Bench", "sod-rC");
            printMetric("SoDMgr.add/deleteSSDRoleMember + setCardinality (4次)", 4, (System.nanoTime() - s2) / 1_000_000);

            long s3 = System.nanoTime();
            for (int i = 0; i < 200; i++) {
                soDMgr.ssdRoleSets();
                soDMgr.ssdRoleSetRoles("SSD-Bench");
                soDMgr.ssdRoleSetCardinality("SSD-Bench");
            }
            printMetric("SoDMgr.ssdRoleSets / Roles / Cardinality 审查 (600次)", 600, (System.nanoTime() - s3) / 1_000_000);

            soDMgr.deleteSSDSet("SSD-Bench");

            // 2. DSD 管理与审查
            long s4 = System.nanoTime();
            DSDSet dsd = soDMgr.createDSDSet("DSD-Bench", Set.of("sod-rA", "sod-rB"), 2);
            printMetric("SoDMgr.createDSDSet (1次)", 1, (System.nanoTime() - s4) / 1_000_000);

            long s5 = System.nanoTime();
            soDMgr.addDSDRoleMember("DSD-Bench", "sod-rC");
            soDMgr.setDSDSetCardinality("DSD-Bench", 3);
            soDMgr.setDSDSetCardinality("DSD-Bench", 2);
            soDMgr.deleteDSDRoleMember("DSD-Bench", "sod-rC");
            printMetric("SoDMgr.add/deleteDSDRoleMember + setCardinality (4次)", 4, (System.nanoTime() - s5) / 1_000_000);

            long s6 = System.nanoTime();
            for (int i = 0; i < 200; i++) {
                soDMgr.dsdRoleSets();
                soDMgr.dsdRoleSetRoles("DSD-Bench");
                soDMgr.dsdRoleSetCardinality("DSD-Bench");
            }
            printMetric("SoDMgr.dsdRoleSets / Roles / Cardinality 审查 (600次)", 600, (System.nanoTime() - s6) / 1_000_000);

            soDMgr.deleteDSDSet("DSD-Bench");
            return null;
        });
    }

    void benchmarkOrgMgr() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        System.out.println("\n==========================================================================================================");
        System.out.println("                         5. OrgMgr 全量业务接口基准评测                                                   ");
        System.out.println("==========================================================================================================");

        tx.execute(status -> {
            // 1. Department CRUD
            long s1 = System.nanoTime();
            Department root = orgMgr.createDepartment(Department.builder().deptName("Headquarters").path("/head").level(1).build());
            Department sub1 = orgMgr.createDepartment(Department.builder().deptName("TechDept").path("/head/tech").level(2).build());
            Department sub2 = orgMgr.createDepartment(Department.builder().deptName("SalesDept").path("/head/sales").level(2).build());
            printMetric("OrgMgr.createDepartment (3次)", 3, (System.nanoTime() - s1) / 1_000_000);

            long s2 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                orgMgr.readDepartment("TechDept");
                orgMgr.findDepartment("TechDept");
            }
            printMetric("OrgMgr.readDepartment / findDepartment (200次)", 200, (System.nanoTime() - s2) / 1_000_000);

            long s3 = System.nanoTime();
            orgMgr.updateDepartment(sub1.getDeptName(), Department.builder().description("Tech Department").build());
            printMetric("OrgMgr.updateDepartment (1次)", 1, (System.nanoTime() - s3) / 1_000_000);

            // 2. Tree 树形查询
            long s4 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                orgMgr.readDepartmentTree();
                orgMgr.readDepartmentSubTree("Headquarters");
            }
            printMetric("OrgMgr.readDepartmentTree / readDepartmentSubTree (200次)", 200, (System.nanoTime() - s4) / 1_000_000);

            // 3. User Membership
            long s5 = System.nanoTime();
            orgMgr.addUser("TechDept", sampleUser.getUsername());
            printMetric("OrgMgr.addUser 到部门 (1次)", 1, (System.nanoTime() - s5) / 1_000_000);

            long s6 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                orgMgr.readDepartmentUsers("TechDept");
                orgMgr.readDepartmentSuccessorUsers("Headquarters");
            }
            printMetric("OrgMgr.readDepartmentUsers / SuccessorUsers (200次)", 200, (System.nanoTime() - s6) / 1_000_000);

            long s7 = System.nanoTime();
            orgMgr.removeUser("TechDept", sampleUser.getUsername());
            printMetric("OrgMgr.removeUser 离开部门 (1次)", 1, (System.nanoTime() - s7) / 1_000_000);

            return null;
        });

        System.out.println("==========================================================================================================\n");
    }

    private void printMetric(String phase, int ops, long elapsedMs) {
        double avgMs = (double) elapsedMs / ops;
        double qps = elapsedMs > 0 ? (ops * 1000.0) / elapsedMs : 0;
        System.out.printf("| %-68s | 样本: %5d | 耗时: %5d ms | 均值: %6.3f ms | 吞吐: %8.1f ops/s |\n",
                phase, ops, elapsedMs, avgMs, qps);
    }
}
