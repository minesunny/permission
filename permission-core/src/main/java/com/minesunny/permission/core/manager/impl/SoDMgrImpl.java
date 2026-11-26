/*
 * Copyright 2023 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */
package com.minesunny.permission.core.manager.impl;

import com.minesunny.permission.core.entity.Role;
import com.minesunny.permission.core.entity.sod.DSDSet;
import com.minesunny.permission.core.entity.sod.SSDSet;
import com.minesunny.permission.core.exception.RBACConflictException;
import com.minesunny.permission.core.exception.RBACException;
import com.minesunny.permission.core.exception.RBACNotFoundException;
import com.minesunny.permission.core.exception.RBACValidationException;
import com.minesunny.permission.core.i18n.RBACMessageKeys;
import com.minesunny.permission.core.manager.ReviewMgr;
import com.minesunny.permission.core.manager.SoDMgr;
import com.minesunny.permission.core.repository.sod.DSDSetRepository;
import com.minesunny.permission.core.repository.sod.SSDSetRepository;
import com.minesunny.permission.core.util.NaturalKeys;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * NIST RBAC 职责分离（Separation of Duty, SoD）业务管理器实现类。
 * <p>
 * 严格遵循架构规范：
 * <ul>
 *     <li>除依赖注入字段外，严禁声明任何 private 方法。</li>
 *     <li>所有对外暴露的方法均必须是 {@code @Override public} 的接口方法。</li>
 *     <li>统一采用消息模板与可变参数抛出领域异常。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SoDMgrImpl implements SoDMgr {

    private final SSDSetRepository ssdSetRepository;
    private final DSDSetRepository dsdSetRepository;
    private final ReviewMgr reviewMgr;

    // -------------------------------------------------------------------------
    // NIST SSD 管理函数 (SSD Administrative Commands)
    // -------------------------------------------------------------------------

    @Override
    public @NotNull SSDSet createSSDSet(@NotNull String ssdName, @NotNull Set<String> roleNames, int cardinality) throws RBACException {
        NaturalKeys.require(ssdName, "ssdName");
        if (Objects.isNull(roleNames) || roleNames.isEmpty()) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_REQUIRED, "roleNames");
        }
        if (cardinality < 2 || cardinality > roleNames.size()) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_REQUIRED, "cardinality must satisfy 2 <= cardinality <= |roles|");
        }
        if (ssdSetRepository.existsByName(ssdName)) {
            throw new RBACConflictException(RBACMessageKeys.CONFLICT_SSD_SET_EXISTS, "createSSDSet", ssdName);
        }

        Set<Role> roles = new HashSet<>();
        for (String roleName : roleNames) {
            roles.add(reviewMgr.readRole(roleName));
        }

        SSDSet ssdSet = SSDSet.builder()
                .name(ssdName)
                .cardinality(cardinality)
                .roles(roles)
                .build();
        return ssdSetRepository.saveAndFlush(ssdSet);
    }

    @Override
    public void deleteSSDSet(@NotNull String ssdName) throws RBACException {
        NaturalKeys.require(ssdName, "ssdName");
        SSDSet ssdSet = ssdSetRepository.findByName(ssdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_SSD_SET, "deleteSSDSet", ssdName));
        ssdSetRepository.delete(ssdSet);
        ssdSetRepository.flush();
    }

    @Override
    public void addSSDRoleMember(@NotNull String ssdName, @NotNull String roleName) throws RBACException {
        NaturalKeys.require(ssdName, "ssdName");
        NaturalKeys.require(roleName, "roleName");
        SSDSet ssdSet = ssdSetRepository.findByName(ssdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_SSD_SET, "addSSDRoleMember", ssdName));
        Role role = reviewMgr.readRole(roleName);

        Set<Role> roles = ssdSet.getRoles();
        if (roles.stream().anyMatch(r -> roleName.equals(r.getRoleName()))) {
            throw new RBACConflictException(RBACMessageKeys.CONFLICT_SOD_ROLE_ALREADY_MEMBER, "addSSDRoleMember", roleName, ssdName);
        }
        roles.add(role);
        ssdSetRepository.saveAndFlush(ssdSet);
    }

    @Override
    public void deleteSSDRoleMember(@NotNull String ssdName, @NotNull String roleName) throws RBACException {
        NaturalKeys.require(ssdName, "ssdName");
        NaturalKeys.require(roleName, "roleName");
        SSDSet ssdSet = ssdSetRepository.findByName(ssdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_SSD_SET, "deleteSSDRoleMember", ssdName));

        Set<Role> roles = ssdSet.getRoles();
        boolean removed = roles.removeIf(r -> roleName.equals(r.getRoleName()));
        if (!removed) {
            throw new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_SOD_ROLE, "deleteSSDRoleMember", roleName, ssdName);
        }
        if (roles.size() < ssdSet.getCardinality()) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_REQUIRED, "Cannot remove role: remaining roles count < cardinality limit");
        }
        ssdSetRepository.saveAndFlush(ssdSet);
    }

    @Override
    public void setSSDSetCardinality(@NotNull String ssdName, int cardinality) throws RBACException {
        NaturalKeys.require(ssdName, "ssdName");
        SSDSet ssdSet = ssdSetRepository.findByName(ssdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_SSD_SET, "setSSDSetCardinality", ssdName));

        if (cardinality < 2 || cardinality > ssdSet.getRoles().size()) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_REQUIRED, "cardinality must satisfy 2 <= cardinality <= |roles|");
        }
        ssdSet.setCardinality(cardinality);
        ssdSetRepository.saveAndFlush(ssdSet);
    }

    // -------------------------------------------------------------------------
    // NIST SSD 审查函数 (SSD Supporting Review Functions)
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<SSDSet> ssdRoleSets() {
        return ssdSetRepository.findAll();
    }

    @Override
    public @NotNull Set<Role> ssdRoleSetRoles(@NotNull String ssdName) throws RBACException {
        NaturalKeys.require(ssdName, "ssdName");
        SSDSet ssdSet = ssdSetRepository.findByName(ssdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_SSD_SET, "ssdRoleSetRoles", ssdName));
        return new HashSet<>(ssdSet.getRoles());
    }

    @Override
    public int ssdRoleSetCardinality(@NotNull String ssdName) throws RBACException {
        NaturalKeys.require(ssdName, "ssdName");
        SSDSet ssdSet = ssdSetRepository.findByName(ssdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_SSD_SET, "ssdRoleSetCardinality", ssdName));
        return ssdSet.getCardinality();
    }

    // -------------------------------------------------------------------------
    // NIST DSD 管理函数 (DSD Administrative Commands)
    // -------------------------------------------------------------------------

    @Override
    public @NotNull DSDSet createDSDSet(@NotNull String dsdName, @NotNull Set<String> roleNames, int cardinality) throws RBACException {
        NaturalKeys.require(dsdName, "dsdName");
        if (Objects.isNull(roleNames) || roleNames.isEmpty()) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_REQUIRED, "roleNames");
        }
        if (cardinality < 2 || cardinality > roleNames.size()) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_REQUIRED, "cardinality must satisfy 2 <= cardinality <= |roles|");
        }
        if (dsdSetRepository.existsByName(dsdName)) {
            throw new RBACConflictException(RBACMessageKeys.CONFLICT_DSD_SET_EXISTS, "createDSDSet", dsdName);
        }

        Set<Role> roles = new HashSet<>();
        for (String roleName : roleNames) {
            roles.add(reviewMgr.readRole(roleName));
        }

        DSDSet dsdSet = DSDSet.builder()
                .name(dsdName)
                .cardinality(cardinality)
                .roles(roles)
                .build();
        return dsdSetRepository.saveAndFlush(dsdSet);
    }

    @Override
    public void deleteDSDSet(@NotNull String dsdName) throws RBACException {
        NaturalKeys.require(dsdName, "dsdName");
        DSDSet dsdSet = dsdSetRepository.findByName(dsdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_DSD_SET, "deleteDSDSet", dsdName));
        dsdSetRepository.delete(dsdSet);
        dsdSetRepository.flush();
    }

    @Override
    public void addDSDRoleMember(@NotNull String dsdName, @NotNull String roleName) throws RBACException {
        NaturalKeys.require(dsdName, "dsdName");
        NaturalKeys.require(roleName, "roleName");
        DSDSet dsdSet = dsdSetRepository.findByName(dsdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_DSD_SET, "addDSDRoleMember", dsdName));
        Role role = reviewMgr.readRole(roleName);

        Set<Role> roles = dsdSet.getRoles();
        if (roles.stream().anyMatch(r -> roleName.equals(r.getRoleName()))) {
            throw new RBACConflictException(RBACMessageKeys.CONFLICT_SOD_ROLE_ALREADY_MEMBER, "addDSDRoleMember", roleName, dsdName);
        }
        roles.add(role);
        dsdSetRepository.saveAndFlush(dsdSet);
    }

    @Override
    public void deleteDSDRoleMember(@NotNull String dsdName, @NotNull String roleName) throws RBACException {
        NaturalKeys.require(dsdName, "dsdName");
        NaturalKeys.require(roleName, "roleName");
        DSDSet dsdSet = dsdSetRepository.findByName(dsdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_DSD_SET, "deleteDSDRoleMember", dsdName));

        Set<Role> roles = dsdSet.getRoles();
        boolean removed = roles.removeIf(r -> roleName.equals(r.getRoleName()));
        if (!removed) {
            throw new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_SOD_ROLE, "deleteDSDRoleMember", roleName, dsdName);
        }
        if (roles.size() < dsdSet.getCardinality()) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_REQUIRED, "Cannot remove role: remaining roles count < cardinality limit");
        }
        dsdSetRepository.saveAndFlush(dsdSet);
    }

    @Override
    public void setDSDSetCardinality(@NotNull String dsdName, int cardinality) throws RBACException {
        NaturalKeys.require(dsdName, "dsdName");
        DSDSet dsdSet = dsdSetRepository.findByName(dsdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_DSD_SET, "setDSDSetCardinality", dsdName));

        if (cardinality < 2 || cardinality > dsdSet.getRoles().size()) {
            throw new RBACValidationException(RBACMessageKeys.VALIDATION_REQUIRED, "cardinality must satisfy 2 <= cardinality <= |roles|");
        }
        dsdSet.setCardinality(cardinality);
        dsdSetRepository.saveAndFlush(dsdSet);
    }

    // -------------------------------------------------------------------------
    // NIST DSD 审查函数 (DSD Supporting Review Functions)
    // -------------------------------------------------------------------------

    @Override
    public @NotNull List<DSDSet> dsdRoleSets() {
        return dsdSetRepository.findAll();
    }

    @Override
    public @NotNull Set<Role> dsdRoleSetRoles(@NotNull String dsdName) throws RBACException {
        NaturalKeys.require(dsdName, "dsdName");
        DSDSet dsdSet = dsdSetRepository.findByName(dsdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_DSD_SET, "dsdRoleSetRoles", dsdName));
        return new HashSet<>(dsdSet.getRoles());
    }

    @Override
    public int dsdRoleSetCardinality(@NotNull String dsdName) throws RBACException {
        NaturalKeys.require(dsdName, "dsdName");
        DSDSet dsdSet = dsdSetRepository.findByName(dsdName)
                .orElseThrow(() -> new RBACNotFoundException(RBACMessageKeys.NOT_FOUND_DSD_SET, "dsdRoleSetCardinality", dsdName));
        return dsdSet.getCardinality();
    }
}
