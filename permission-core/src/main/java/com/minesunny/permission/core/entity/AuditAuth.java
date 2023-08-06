//package com.minesunny.query.permission.core.entity;
//
//import com.minesunny.jpa.entity.BaseEntity;
//import com.minesunny.query.permission.core.type.AuthType;
//import jakarta.persistence.*;
//import lombok.AllArgsConstructor;
//import lombok.NoArgsConstructor;
//import lombok.experimental.SuperBuilder;
//import org.hibernate.annotations.Comment;
//import org.hibernate.annotations.SQLDelete;
//import org.hibernate.annotations.Where;
//
//@SuperBuilder
//@NoArgsConstructor
//@AllArgsConstructor
//@SQLDelete(sql = "update t_audit_auth set delete_at = current_timestamp where delete_at is null")
//@Where(clause = "delete_at is null")
//@Entity
//@Table(name = "t_audit_auth")
//public class AuditAuth extends BaseEntity {
//    @Id
//    @GeneratedValue(strategy = GenerationType.AUTO)
//    @Column(name = "id", nullable = false)
//    private Long id;
//
//    @Column(name = "user_id")
//	@Comment(value = "userId")
//	private Long userId;
//
//
//	@Column(name = "auth_type")
//	@Comment(value = "authType")
//	private AuthType authType;
//
//
//	@Column(name = "auth_device_info")
//	@Comment(value = "device info of user auth system(web,browser)")
//	private String authDeviceInfo;
//
//
//	/**
//	 * 录失败信息，为空登录成功
//	 */
//	@Column(name = "auth_failed_msg")
//	@Comment(value = "auth failed message)")
//	private String authFailedMsg;
//
//    public Long getId() {
//        return id;
//    }
//
//    public void setId(Long id) {
//        this.id = id;
//    }
//}
