package com.minesunny.permission.core.entity;

import com.minesunny.permission.core.entity.actor.User;
import com.minesunny.permission.core.entity.perm.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 针对 NIST RBAC 新扩充实体字段（User enabled, Role 日内时间段/maxCardinality, Permission Policy）的单测。
 */
class NISTEntityAttributesTest {

    @Test
    @DisplayName("User: enabled 属性与 isValid 综合有效性测试")
    void testUserEnabledAndValidity() {
        User user = User.builder()
                .actorName("user-valid-test")
                .username("user-valid-test")
                .enabled(true)
                .build();

        assertTrue(user.isEnabled());
        assertTrue(user.isValid());

        // 测试被禁用 (Administrative Disabled)
        user.setEnabled(false);
        assertFalse(user.isEnabled());
        assertFalse(user.isValid());

        // 重新启用
        user.setEnabled(true);
        assertTrue(user.isValid());

        // 测试被锁定 (Temporal Locked)
        Date future = new Date(System.currentTimeMillis() + 60_000);
        user.setLockDate(future);
        assertTrue(user.isLocked());
        assertFalse(user.isValid()); // 即使 enabled 为 true，处于锁定期依然无效

        // 锁定期已过
        Date past = new Date(System.currentTimeMillis() - 1000);
        user.setLockDate(past);
        assertFalse(user.isLocked());
        assertTrue(user.isValid());
    }

    @Test
    @DisplayName("Role: 单日具体时间窗口 (beginTimeOfDay ~ endTimeOfDay) 判定测试")
    void testRoleTimeOfDayWindow() {
        Role role = Role.builder()
                .roleName("bank-teller-role")
                .beginTimeOfDay(LocalTime.of(9, 0, 0))
                .endTimeOfDay(LocalTime.of(17, 0, 0))
                .maxCardinality(10)
                .build();

        assertEquals(10, role.getMaxCardinality());

        // 早上 10:30 (在 09:00 ~ 17:00 之内)
        Calendar insideTime = Calendar.getInstance();
        insideTime.set(Calendar.HOUR_OF_DAY, 10);
        insideTime.set(Calendar.MINUTE, 30);
        insideTime.set(Calendar.SECOND, 0);
        assertTrue(role.activated(insideTime));

        // 晚上 20:00 (在 17:00 之后，应被拒绝)
        Calendar afterTime = Calendar.getInstance();
        afterTime.set(Calendar.HOUR_OF_DAY, 20);
        afterTime.set(Calendar.MINUTE, 0);
        afterTime.set(Calendar.SECOND, 0);
        assertFalse(role.activated(afterTime));

        // 早上 07:00 (在 09:00 之前，应被拒绝)
        Calendar beforeTime = Calendar.getInstance();
        beforeTime.set(Calendar.HOUR_OF_DAY, 7);
        beforeTime.set(Calendar.MINUTE, 0);
        beforeTime.set(Calendar.SECOND, 0);
        assertFalse(role.activated(beforeTime));
    }

    @Test
    @DisplayName("Permission: Policy 核心扩展属性 (effect, condition, purpose, obligation) 测试")
    void testPermissionPolicyAttributes() {
        Permission perm = Permission.builder()
                .permName("patient-record:view")
                .resource("patient-record")
                .operation("view")
                .permType("DATA")
                .effect("ALLOW")
                .condition("department == 'Cardiology'")
                .purpose("Treatment")
                .obligation("MASK_SSN;AUDIT_LOG")
                .build();

        assertEquals("patient-record:view", perm.getPermName());
        assertEquals("patient-record", perm.getResource());
        assertEquals("view", perm.getOperation());
        assertEquals("ALLOW", perm.getEffect());
        assertEquals("department == 'Cardiology'", perm.getCondition());
        assertEquals("Treatment", perm.getPurpose());
        assertEquals("MASK_SSN;AUDIT_LOG", perm.getObligation());
    }
}
