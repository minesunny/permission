package com.minesunny.permission.core.entity.perm;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "t_permission_policy_map")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermissionPolicyMap {

    @Id
    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "policy_id")
    private Long policyId;
}
