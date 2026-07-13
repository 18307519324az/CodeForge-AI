package com.codeforge.ai.domain.auth.entity;

import com.codeforge.ai.domain.common.BaseEntity;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("user")
public class UserEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String account;
    private String passwordHash;
    private String displayName;
    private String avatarUrl;
    private String email;
    private String phone;
    private String status;
    private LocalDateTime lastLoginAt;
}
