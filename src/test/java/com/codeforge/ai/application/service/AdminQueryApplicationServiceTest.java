package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.MetricSummaryResponse;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.auth.entity.UserRoleEntity;
import com.codeforge.ai.domain.generation.model.ModelProviderRoutingConfigService;
import com.codeforge.ai.domain.generation.model.ProviderCredentialResolver;
import com.codeforge.ai.domain.metrics.entity.MetricDailyAggEntity;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AuditLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.MetricDailyAggEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserRoleEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.request.PageRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AdminQueryApplicationServiceTest {

    private AuditLogEntityMapper auditLogEntityMapper;
    private AiAppEntityMapper aiAppEntityMapper;
    private MetricDailyAggEntityMapper metricDailyAggEntityMapper;
    private UserEntityMapper userEntityMapper;
    private UserRoleEntityMapper userRoleEntityMapper;
    private ModelProviderEntityMapper modelProviderEntityMapper;
    private ModelCallLogEntityMapper modelCallLogEntityMapper;
    private ModelProviderRoutingConfigService routingConfigService;
    private ProviderCredentialResolver credentialResolver;
    private AdminMetricsApplicationService adminMetricsApplicationService;
    private AdminQueryApplicationService adminQueryApplicationService;

    @BeforeEach
    void setUp() {
        auditLogEntityMapper = mock(AuditLogEntityMapper.class);
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        metricDailyAggEntityMapper = mock(MetricDailyAggEntityMapper.class);
        userEntityMapper = mock(UserEntityMapper.class);
        userRoleEntityMapper = mock(UserRoleEntityMapper.class);
        modelProviderEntityMapper = mock(ModelProviderEntityMapper.class);
        modelCallLogEntityMapper = mock(ModelCallLogEntityMapper.class);
        routingConfigService = mock(ModelProviderRoutingConfigService.class);
        credentialResolver = mock(ProviderCredentialResolver.class);
        adminMetricsApplicationService = mock(AdminMetricsApplicationService.class);
        adminQueryApplicationService = new AdminQueryApplicationService(
                auditLogEntityMapper,
                aiAppEntityMapper,
                metricDailyAggEntityMapper,
                adminMetricsApplicationService,
                userEntityMapper,
                userRoleEntityMapper,
                modelProviderEntityMapper,
                modelCallLogEntityMapper,
                routingConfigService,
                credentialResolver
        );
    }

    @Test
    void shouldReturnUsersForPlatformAdmin() {
        given(userEntityMapper.countAllUsers()).willReturn(1L);
        UserEntity userEntity = UserEntity.builder()
                .id(2001L)
                .account("admin")
                .displayName("Admin User")
                .email("admin@example.com")
                .status("ACTIVE")
                .lastLoginAt(LocalDateTime.of(2026, 6, 24, 9, 0))
                .build();
        userEntity.setCreatedAt(LocalDateTime.of(2026, 6, 24, 8, 0));
        given(userEntityMapper.findPage(0L, 10L, null)).willReturn(List.of(userEntity));
        given(userRoleEntityMapper.findByUserIds(List.of(2001L))).willReturn(List.of(
                UserRoleEntity.builder().id(1L).userId(2001L).roleCode("PLATFORM_ADMIN").build()
        ));

        var response = adminQueryApplicationService.listUsers(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")),
                new PageRequest()
        );

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).hasSize(1);
        assertThat(response.records().getFirst().account()).isEqualTo("admin");
        assertThat(response.records().getFirst().platformRoles()).containsExactly("PLATFORM_ADMIN");
    }

    @Test
    void shouldReturnAuditLogsForPlatformAdmin() {
        given(auditLogEntityMapper.countAll()).willReturn(1L);
        given(auditLogEntityMapper.findPage(0L, 10L)).willReturn(List.of(
                AuditLogEntity.builder()
                        .id(9001L)
                        .workspaceId(1001L)
                        .actorUserId(1L)
                        .actionCode("QUOTA_ADJUST")
                        .targetType("USER_QUOTA")
                        .targetId("5001")
                        .requestId("req_test")
                        .detailJson("{\"quotaId\":5001}")
                        .ipAddress("127.0.0.1")
                        .createdAt(LocalDateTime.of(2026, 6, 24, 9, 30))
                        .build()
        ));

        var response = adminQueryApplicationService.listAuditLogs(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")),
                new PageRequest()
        );

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).hasSize(1);
        assertThat(response.records().getFirst().actionType()).isEqualTo("QUOTA_ADJUST");
        assertThat(response.records().getFirst().operatorUserId()).isEqualTo(1L);
    }

    @Test
    void shouldReturnModelProvidersForPlatformAdmin() {
        given(modelProviderEntityMapper.findAllProviders()).willReturn(List.of(
                ModelProviderEntity.builder()
                        .id(3001L)
                        .providerCode("deepseek")
                        .providerName("DeepSeek")
                        .baseUrl("https://api.deepseek.com")
                        .authMode("API_KEY")
                        .secretRef("vault://provider/deepseek")
                        .status("ACTIVE")
                        .build()
        ));

        var response = adminQueryApplicationService.listModelProviders(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN"))
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().providerCode()).isEqualTo("deepseek");
    }

    @Test
    void shouldReturnModelCallLogsForPlatformAdmin() {
        given(modelCallLogEntityMapper.countAllLogs()).willReturn(1L);
        given(modelCallLogEntityMapper.findPage(0L, 10L, null)).willReturn(List.of(
                ModelCallLogEntity.builder()
                        .id(4001L)
                        .taskId(5001L)
                        .providerId(3001L)
                        .modelName("gpt-4.1")
                        .requestId("req_model_001")
                        .status("SUCCESS")
                        .inputTokens(100)
                        .outputTokens(200)
                        .durationMs(800L)
                        .createdAt(LocalDateTime.of(2026, 6, 24, 10, 0))
                        .build()
        ));

        var response = adminQueryApplicationService.listModelCallLogs(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")),
                new PageRequest()
        );

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).hasSize(1);
        assertThat(response.records().getFirst().modelName()).isEqualTo("gpt-4.1");
    }

    @Test
    void shouldReturnMetricSummaryForPlatformAdmin() {
        MetricSummaryResponse expected = metricSummary(
                LocalDate.of(2026, 6, 24), 12L, 10L, 2L, new BigDecimal("0.8333"), 100L, 240L, 850L);
        given(adminMetricsApplicationService.getMetricsSummary(any())).willReturn(expected);

        var response = adminQueryApplicationService.getMetricsSummary(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")));

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void shouldRejectNonAdminUserQuery() {
        assertThatThrownBy(() -> adminQueryApplicationService.listUsers(
                new CurrentUser(2L, "user", List.of("USER")),
                new PageRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectNonAdminAuditQuery() {
        assertThatThrownBy(() -> adminQueryApplicationService.listAuditLogs(
                new CurrentUser(2L, "user", List.of("USER")),
                new PageRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectNonAdminMetricsQuery() {
        given(adminMetricsApplicationService.getMetricsSummary(any()))
                .willThrow(new BusinessException(com.codeforge.ai.shared.exception.ErrorCode.RESOURCE_FORBIDDEN));

        assertThatThrownBy(() -> adminQueryApplicationService.getMetricsSummary(new CurrentUser(2L, "user", List.of("USER"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectOversizedAuditLogPageRequest() {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPageSize(201);

        assertThatThrownBy(() -> adminQueryApplicationService.listAuditLogs(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")),
                pageRequest))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldReturnZeroMetricsWhenSummaryRowsMissing() {
        given(adminMetricsApplicationService.getMetricsSummary(any())).willReturn(metricSummary(
                null, 0L, 0L, 0L, BigDecimal.ZERO, 0L, 0L, 0L));

        var response = adminQueryApplicationService.getMetricsSummary(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")));

        assertThat(response.statDate()).isNull();
        assertThat(response.requestCount()).isZero();
        assertThat(response.freshnessStatus()).isEqualTo("EMPTY");
    }

    private MetricSummaryResponse metricSummary(LocalDate statDate,
                                                Long requestCount,
                                                Long successCount,
                                                Long failedCount,
                                                BigDecimal successRate,
                                                Long tokenInput,
                                                Long tokenOutput,
                                                Long avgDurationMs) {
        return new MetricSummaryResponse(
                statDate,
                requestCount,
                successCount,
                failedCount,
                successRate,
                tokenInput,
                tokenOutput,
                avgDurationMs,
                MetricSummaryResponse.MODEL_CALL_ALL_TIME_SCOPE,
                null,
                LocalDateTime.of(2026, 6, 24, 12, 0),
                requestCount == null || requestCount == 0 ? "EMPTY" : "FRESH",
                129600L
        );
    }
}

