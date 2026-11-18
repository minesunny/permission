package com.minesunny.permission.core.entity.perm;

import com.minesunny.jpa.entity.CachedIdEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@DiscriminatorColumn(name = "policy_type")
@Table(name = "t_policy")
@Setter
@Getter
@SQLDelete(sql = "update t_policy set delete_at = current_timestamp where id = ? and version = ? and delete_at is null")
@SQLRestriction("delete_at is null")
public class Policy extends CachedIdEntity {

    @Column(name = "policy_name", nullable = false)
    @Comment(value = "policyName")
    private String policyName;

    @Column(name = "description")
    @Comment(value = "description policy")
    private String description;

    @Column(name = "owner_name")
    @Comment(value = "owner name (username/actorName)")
    private String ownerName;

    @Column(name = "perm_name")
    @Comment(value = "permission name")
    private String permName;

    @Override
    public int hashCode() {
        return Policy.class.hashCode();
    }
}
