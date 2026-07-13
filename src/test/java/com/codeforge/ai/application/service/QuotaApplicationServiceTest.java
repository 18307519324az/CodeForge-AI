package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.QuotaAdjustRequest;
import com.codeforge.ai.application.dto.quota.QuotaUsageLogResponse;
import com.codeforge.ai.application.dto.quota.UserQuotaResponse;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.quota.entity.QuotaUsageLogEntity;
import com.codeforge.ai.domain.quota.entity.UserQuotaEntity;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.QuotaUsageLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserQuotaEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class QuotaApplicationServiceTest {

    private UserQuotaEntityMapper userQuotaEntityMapper;
    private QuotaUsageLogEntityMapper quotaUsageLogEntityMapper;
    private AuditLogWriter auditLogWriter;
    private UserEntityMapper userEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private QuotaApplicationService quotaApplicationService;

    @BeforeEach
    void setUp() {
        userQuotaEntityMapper = mock(UserQuotaEntityMapper.class);
        quotaUsageLogEntityMapper = mock(QuotaUsageLogEntityMapper.class);
        auditLogWriter = mock(AuditLogWriter.class);
        userEntityMapper = mock(UserEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        quotaApplicationService = new QuotaApplicationService(
                userQuotaEntityMapper,
                quotaUsageLogEntityMapper,
                auditLogWriter,
                userEntityMapper,
                workspaceAccessService,
                new ObjectMapper()
        );
    }

    @Test
    void shouldReturnMyQuotas() {
        UserQuotaEntity entity = UserQuotaEntity.builder()
                .id(5001L)
                .userId(2001L)
                .workspaceId(1001L)
                .dailyRequestLimit(10)
                .dailyTokenLimit(1000)
                .monthlyCostLimit(new BigDecimal("12.50"))
                .status("ACTIVE")
                .build();
        entity.setUpdatedAt(LocalDateTime.of(2026, 6, 24, 0, 20));
        given(userQuotaEntityMapper.findByUserId(2001L)).willReturn(List.of(entity));

        List<UserQuotaResponse> response = quotaApplicationService.getMyQuotas(
                new CurrentUser(2001L, "user", List.of("USER"))
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().dailyRequestLimit()).isEqualTo(10);
    }

    @Test
    void shouldAdjustQuotaAndWriteUsageLogAndAuditLog() {
        given(userEntityMapper.selectOneById(2002L)).willReturn(UserEntity.builder()
                .id(2002L)
                .account("user_b")
                .build());
        given(userQuotaEntityMapper.findByUserIdAndWorkspaceId(2002L, 1001L)).willReturn(null);
        AtomicLong quotaId = new AtomicLong(5002L);
        doAnswer(invocation -> {
            UserQuotaEntity entity = invocation.getArgument(0);
            entity.setId(quotaId.getAndIncrement());
            return 1;
        }).when(userQuotaEntityMapper).insert(any(UserQuotaEntity.class));

        QuotaAdjustRequest request = new QuotaAdjustRequest();
        request.setUserId(2002L);
        request.setWorkspaceId(1001L);
        request.setDailyRequestLimit(5);
        request.setDailyTokenLimit(500);
        request.setMonthlyCostLimit(new BigDecimal("9.99"));

        UserQuotaResponse response = quotaApplicationService.adjustQuota(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")),
                request
        );

        assertThat(response.id()).isEqualTo(5002L);
        assertThat(response.dailyRequestLimit()).isEqualTo(5);
        verify(quotaUsageLogEntityMapper).insert(any(QuotaUsageLogEntity.class));
        verify(auditLogWriter).insert(any(AuditLogEntity.class));
    }

    @Test
    void shouldListUsageLogsForPlatformAdmin() {
        given(quotaUsageLogEntityMapper.findLatestLogs()).willReturn(List.of(
                QuotaUsageLogEntity.builder()
                        .id(6001L)
                        .quotaId(5001L)
                        .taskId(7001L)
                        .usageType("GENERATION_TASK_CREATE")
                        .requestCount(1)
                        .tokenCount(0)
                        .costAmount(BigDecimal.ZERO)
                        .createdAt(LocalDateTime.of(2026, 6, 24, 0, 21))
                        .build()
        ));

        List<QuotaUsageLogResponse> response = quotaApplicationService.listQuotaUsageLogs(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN"))
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().usageType()).isEqualTo("GENERATION_TASK_CREATE");
    }

    @Test
    void shouldRejectWhenQuotaExceeded() {
        given(userQuotaEntityMapper.findByUserIdAndWorkspaceId(2001L, 1001L)).willReturn(UserQuotaEntity.builder()
                .id(5001L)
                .userId(2001L)
                .workspaceId(1001L)
                .dailyRequestLimit(1)
                .status("ACTIVE")
                .build());
        given(quotaUsageLogEntityMapper.findByQuotaIdSince(any(), any())).willReturn(List.of(
                QuotaUsageLogEntity.builder().requestCount(1).build()
        ));

        assertThatThrownBy(() -> quotaApplicationService.assertQuotaAvailable(2001L, 1001L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.QUOTA_NOT_ENOUGH.getMessage());
    }

    @Test
    void shouldRejectNonAdminAdjustment() {
        QuotaAdjustRequest request = new QuotaAdjustRequest();
        request.setUserId(2002L);
        request.setWorkspaceId(1001L);

        assertThatThrownBy(() -> quotaApplicationService.adjustQuota(
                new CurrentUser(2L, "user", List.of("USER")),
                request
        )).isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_FORBIDDEN.getMessage());

        verify(userQuotaEntityMapper, never()).insert(any(UserQuotaEntity.class));
    }
}
