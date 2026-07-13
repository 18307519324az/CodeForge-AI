package com.codeforge.ai.infrastructure.audit;

import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AuditLogEntityMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 统一审计写入契约：所有 audit_log insert 必须经过此组件，确保 created_at 永不为 null。
 */
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogEntityMapper auditLogEntityMapper;

    public void insert(AuditLogEntity entity) {
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        auditLogEntityMapper.insert(entity);
    }
}
