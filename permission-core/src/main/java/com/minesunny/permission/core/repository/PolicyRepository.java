package com.minesunny.permission.core.repository;

import com.minesunny.permission.core.entity.perm.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long>, JpaSpecificationExecutor<Policy> {
    List<Policy> findByPolicyName(String policyName);

    Optional<Policy> findByPolicyNameAndOwnerIdNull(String policyName);

    Optional<Policy> findByPolicyNameAndOwnerId(String policyName, String ownerId);

    Optional<Policy> findByPermIdAndOwnerId(Long permId, String ownerId);

    Optional<Policy> findByPermIdAndOwnerIdNull(Long permId);
}
