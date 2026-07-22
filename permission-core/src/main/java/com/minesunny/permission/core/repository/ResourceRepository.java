package com.minesunny.permission.core.repository;

import com.minesunny.permission.core.entity.perm.Resource;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import java.util.Optional;

public interface ResourceRepository extends JpaRepositoryImplementation<Resource, Long> {
    Optional<Resource> findByResourceName(String resourceName);
}
