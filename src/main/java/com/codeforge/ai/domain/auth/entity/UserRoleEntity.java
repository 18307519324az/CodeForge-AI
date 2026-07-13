package com.codeforge.ai.domain.auth.entity;

import com.codeforge.ai.domain.common.BaseEntity;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
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
@Table("user_role")
public class UserRoleEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Long userId;
    private String roleCode;
}
