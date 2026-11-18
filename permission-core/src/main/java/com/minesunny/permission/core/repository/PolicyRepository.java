package com.minesunny.permission.core.repository;

import com.minesunny.permission.core.entity.perm.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long>, JpaSpecificationExecutor<Policy> {
    List<Policy> findByPolicyName(String policyName);

    Optional<Policy> findByPolicyNameAndOwnerNameNull(String policyName);

    Optional<Policy> findByPolicyNameAndOwnerName(String policyName, String ownerName);

    List<Policy> findByPermNameAndOwnerName(String permName, String ownerName);

    List<Policy> findByPermNameAndOwnerNameNull(String permName);

    Optional<Policy> findByPolicyNameAndPermNameAndOwnerNameNull(String policyName, String permName);

    Optional<Policy> findByPolicyNameAndPermNameAndOwnerName(String policyName, String permName, String ownerName);
}
