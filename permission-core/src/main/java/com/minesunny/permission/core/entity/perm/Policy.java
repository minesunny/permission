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

@Entity
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@DiscriminatorColumn(name = "policy_type")
@Table(name = "t_policy")
@Setter
@Getter
@SQLDelete(sql = "update t_policy set delete_at = current_timestamp, version = ?2 where id in (?1) and delete_at is null")
public class Policy extends CachedIdEntity {

    @Column(name = "policy_name", unique = true, nullable = false)
    @Comment(value = "policyName")
    private String policyName;

    @Column(name = "description")
    @Comment(value = "description policy")
    private String description;

    @Column(name = "owner_id")
    @Comment(value = "owner id (username/actorName)")
    private String ownerId;

    @Override
    public int hashCode() {
        return Policy.class.hashCode();
    }
}
