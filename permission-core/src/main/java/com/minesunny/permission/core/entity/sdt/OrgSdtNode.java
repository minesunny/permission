package com.minesunny.permission.core.entity.sdt;

import com.minesunny.jpa.entity.SdtNode;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;


@NoArgsConstructor
@Entity
@SQLDelete(sql = "update t_org_sdt set delete_at = current_timestamp,version = ?1 where id in (?2) and delete_at is null")
@Where(clause = "delete_at is null")
@Getter
@SuperBuilder
@DiscriminatorValue("ORG")
public class OrgSdtNode extends SdtNode {

}
