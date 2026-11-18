package com.minesunny.permission.core.entity.sdt;

import com.minesunny.jpa.entity.SdtNode;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;


@NoArgsConstructor
@Entity
@SQLDelete(sql = "update t_org_sdt set delete_at = current_timestamp where id = ? and version = ? and delete_at is null")
@SQLRestriction("delete_at is null")
@Getter
@SuperBuilder
@DiscriminatorValue("ORG")
public class OrgSdtNode extends SdtNode {

}
