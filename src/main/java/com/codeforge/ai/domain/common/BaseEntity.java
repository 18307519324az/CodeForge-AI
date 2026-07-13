package com.codeforge.ai.domain.common;

import com.mybatisflex.annotation.Column;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column("created_by")
    private Long createdBy;

    @Column("updated_by")
    private Long updatedBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column(value = "is_deleted", isLogicDelete = true)
    private Integer isDeleted;
}
