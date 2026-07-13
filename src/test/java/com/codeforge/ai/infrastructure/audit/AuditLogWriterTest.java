package com.codeforge.ai.infrastructure.audit;

import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AuditLogEntityMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogWriterTest {

    @Test
    void auditInsertNeverProducesNullCreatedAtTest() {
        AuditLogEntityMapper mapper = mock(AuditLogEntityMapper.class);
        AuditLogWriter writer = new AuditLogWriter(mapper);
        AuditLogEntity entity = AuditLogEntity.builder()
                .actorUserId(1L)
                .actionCode("TEST_ACTION")
                .targetType("TEST")
                .targetId("1")
                .build();

        writer.insert(entity);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void auditInsertPreservesExplicitCreatedAtTest() {
        AuditLogEntityMapper mapper = mock(AuditLogEntityMapper.class);
        AuditLogWriter writer = new AuditLogWriter(mapper);
        LocalDateTime explicit = LocalDateTime.of(2026, 7, 10, 12, 0);
        AuditLogEntity entity = AuditLogEntity.builder()
                .actorUserId(1L)
                .actionCode("TEST_ACTION")
                .targetType("TEST")
                .targetId("1")
                .createdAt(explicit)
                .build();

        writer.insert(entity);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(explicit);
    }
}
