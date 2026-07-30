package com.minesunny.permission.core.repository;

import com.minesunny.permission.core.entity.perm.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long>, JpaSpecificationExecutor<Policy> {
    Optional<Policy> findByPolicyName(String policyName);

    @Query(value = "select policy_id from t_permission_policy_map where permission_id = :permId and user_id = :userId", nativeQuery = true)
    Optional<Long> findPolicyIdByPermissionIdAndUserId(@Param("permId") Long permId, @Param("userId") Long userId);

    @Query(value = "select policy_id from t_permission_policy_map where permission_id = :permId and user_id is null", nativeQuery = true)
    Optional<Long> findTemplatePolicyIdByPermissionId(@Param("permId") Long permId);
}
