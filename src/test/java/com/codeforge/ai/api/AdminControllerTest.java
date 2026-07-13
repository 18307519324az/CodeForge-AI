package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.admin.AdminUserListItemResponse;
import com.codeforge.ai.application.dto.admin.AuditLogResponse;
import com.codeforge.ai.application.dto.admin.MetricSummaryResponse;
import com.codeforge.ai.application.dto.admin.ModelCallLogResponse;
import com.codeforge.ai.application.dto.admin.ModelProviderResponse;
import com.codeforge.ai.application.dto.quota.QuotaUsageLogResponse;
import com.codeforge.ai.application.dto.quota.UserQuotaResponse;
import com.codeforge.ai.application.service.AdminAiRoutingApplicationService;
import com.codeforge.ai.application.service.AdminModelProviderApplicationService;
import com.codeforge.ai.application.service.AdminPromptTemplateApplicationService;
import com.codeforge.ai.application.service.release.AdminPromptRuntimeGateApplicationService;
import com.codeforge.ai.application.service.AdminMetricsApplicationService;
import com.codeforge.ai.application.service.AdminQueryApplicationService;
import com.codeforge.ai.application.service.QuotaApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.GlobalExceptionHandler;
import com.codeforge.ai.shared.response.PageResponse;
import com.codeforge.ai.shared.web.RequestIdFilter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest {

    private MockMvc mockMvc;
    private QuotaApplicationService quotaApplicationService;
    private AdminQueryApplicationService adminQueryApplicationService;
    private AdminMetricsApplicationService adminMetricsApplicationService;
    private AdminModelProviderApplicationService adminModelProviderApplicationService;
    private AdminAiRoutingApplicationService adminAiRoutingApplicationService;
    private AdminPromptTemplateApplicationService adminPromptTemplateApplicationService;
    private AdminPromptRuntimeGateApplicationService adminPromptRuntimeGateApplicationService;

    @BeforeEach
    void setUp() {
        quotaApplicationService = mock(QuotaApplicationService.class);
        adminQueryApplicationService = mock(AdminQueryApplicationService.class);
        adminMetricsApplicationService = mock(AdminMetricsApplicationService.class);
        adminModelProviderApplicationService = mock(AdminModelProviderApplicationService.class);
        adminAiRoutingApplicationService = mock(AdminAiRoutingApplicationService.class);
        adminPromptTemplateApplicationService = mock(AdminPromptTemplateApplicationService.class);
        adminPromptRuntimeGateApplicationService = mock(AdminPromptRuntimeGateApplicationService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(
                        quotaApplicationService,
                        adminQueryApplicationService,
                        adminMetricsApplicationService,
                        adminModelProviderApplicationService,
                        adminAiRoutingApplicationService,
                        adminPromptTemplateApplicationService,
                        adminPromptRuntimeGateApplicationService
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturnUserList() throws Exception {
        given(adminQueryApplicationService.listUsers(any(), any())).willReturn(PageResponse.<AdminUserListItemResponse>builder()
                .records(List.of(new AdminUserListItemResponse(
                        2001L,
                        "admin",
                        "Admin User",
                        "admin@example.com",
                        "ACTIVE",
                        List.of("PLATFORM_ADMIN"),
                        LocalDateTime.of(2026, 6, 24, 8, 0),
                        LocalDateTime.of(2026, 6, 24, 9, 0)
                )))
                .pageNo(1)
                .pageSize(10)
                .total(1)
                .build());

        mockMvc.perform(get("/v1/admin/users")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].account").value("admin"))
                .andExpect(jsonPath("$.data.records[0].platformRoles[0]").value("PLATFORM_ADMIN"));
    }

    @Test
    void shouldAdjustQuota() throws Exception {
        given(quotaApplicationService.adjustQuota(any(), any())).willReturn(
                new UserQuotaResponse(5001L, 2002L, 1001L, 5, 500, new BigDecimal("9.99"),
                        "ACTIVE", null, null, LocalDateTime.of(2026, 6, 24, 0, 31))
        );

        mockMvc.perform(post("/v1/admin/quotas/adjust")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 2002,
                                  "workspaceId": 1001,
                                  "dailyRequestLimit": 5,
                                  "dailyTokenLimit": 500,
                                  "monthlyCostLimit": 9.99
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dailyRequestLimit").value(5));
    }

    @Test
    void shouldValidateAdjustQuotaRequest() throws Exception {
        mockMvc.perform(post("/v1/admin/quotas/adjust")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": null,
                                  "workspaceId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void shouldReturnQuotaUsageLogs() throws Exception {
        given(quotaApplicationService.listQuotaUsageLogs(any())).willReturn(List.of(
                new QuotaUsageLogResponse(6001L, 5001L, 7001L, "GENERATION_TASK_CREATE", 1, 0,
                        BigDecimal.ZERO, LocalDateTime.of(2026, 6, 24, 0, 32))
        ));

        mockMvc.perform(get("/v1/admin/quotas/usage-logs")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].usageType").value("GENERATION_TASK_CREATE"));
    }

    @Test
    void shouldReturnAuditLogs() throws Exception {
        given(adminQueryApplicationService.listAuditLogs(any(), any())).willReturn(PageResponse.<AuditLogResponse>builder()
                .records(List.of(
                        new AuditLogResponse(9001L, 1001L, 1L, "QUOTA_ADJUST", "USER_QUOTA", "5001",
                                "req_test", LocalDateTime.of(2026, 6, 24, 9, 30))
                ))
                .pageNo(1)
                .pageSize(10)
                .total(1)
                .build());

        mockMvc.perform(get("/v1/admin/audit-logs")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].actionType").value("QUOTA_ADJUST"))
                .andExpect(jsonPath("$.data.pageNo").value(1));
    }

    private static MetricSummaryResponse sampleMetricSummary() {
        return new MetricSummaryResponse(
                LocalDate.of(2026, 6, 24),
                12L,
                10L,
                2L,
                new BigDecimal("0.8333"),
                100L,
                240L,
                850L,
                MetricSummaryResponse.MODEL_CALL_ALL_TIME_SCOPE,
                LocalDateTime.of(2026, 6, 24, 18, 0),
                LocalDateTime.of(2026, 6, 24, 19, 0),
                "FRESH",
                129600L
        );
    }

    @Test
    void shouldReturnMetricsSummary() throws Exception {
        given(adminQueryApplicationService.getMetricsSummary(any())).willReturn(sampleMetricSummary());

        mockMvc.perform(get("/v1/admin/metrics/summary")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestCount").value(12))
                .andExpect(jsonPath("$.data.successRate").value(0.8333));
    }

    @Test
    void shouldReturnModelProviders() throws Exception {
        given(adminQueryApplicationService.listModelProviders(any())).willReturn(List.of(
                new ModelProviderResponse(
                        3001L,
                        "openai-compatible",
                        "OpenAI Compatible",
                        "https://api.example.com",
                        "API_KEY",
                        "vault://provider/openai",
                        "ACTIVE",
                        10,
                        "gpt-4.1-mini",
                        "ENV",
                        true,
                        "****cdef",
                        LocalDateTime.of(2026, 6, 24, 9, 0),
                        LocalDateTime.of(2026, 6, 24, 9, 30)
                )
        ));

        mockMvc.perform(get("/v1/admin/model-providers")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].providerCode").value("openai-compatible"));
    }

    @Test
    void shouldCreateModelProvider() throws Exception {
        given(adminModelProviderApplicationService.createModelProvider(any(), any())).willReturn(
                new ModelProviderResponse(
                        3001L,
                        "deepseek",
                        "DeepSeek",
                        "https://api.deepseek.com",
                        "API_KEY",
                        null,
                        "ACTIVE",
                        20,
                        "deepseek-chat",
                        "ENV",
                        false,
                        null,
                        LocalDateTime.of(2026, 6, 24, 9, 0),
                        LocalDateTime.of(2026, 6, 24, 9, 0)
                )
        );

        mockMvc.perform(post("/v1/admin/model-providers")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerCode": "deepseek",
                                  "providerName": "DeepSeek",
                                  "baseUrl": "https://api.deepseek.com",
                                  "authMode": "API_KEY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerCode").value("deepseek"));
    }

    @Test
    void shouldValidateCreateModelProviderRequest() throws Exception {
        mockMvc.perform(post("/v1/admin/model-providers")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerCode": "",
                                  "providerName": "",
                                  "authMode": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void shouldUpdateModelProvider() throws Exception {
        given(adminModelProviderApplicationService.updateModelProvider(any(), any(), any())).willReturn(
                new ModelProviderResponse(
                        3001L,
                        "deepseek",
                        "DeepSeek V2",
                        "https://api.deepseek.com/v2",
                        "API_KEY",
                        null,
                        "ACTIVE",
                        5,
                        "deepseek-chat",
                        "ENV",
                        true,
                        "****cdef",
                        LocalDateTime.of(2026, 6, 24, 9, 0),
                        LocalDateTime.of(2026, 6, 24, 10, 0)
                )
        );

        mockMvc.perform(put("/v1/admin/model-providers/3001")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerCode": "deepseek",
                                  "providerName": "DeepSeek V2",
                                  "baseUrl": "https://api.deepseek.com/v2",
                                  "authMode": "API_KEY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerName").value("DeepSeek V2"));
    }

    @Test
    void shouldReturnModelCallLogs() throws Exception {
        given(adminQueryApplicationService.listModelCallLogs(any(), any())).willReturn(PageResponse.<ModelCallLogResponse>builder()
                .records(List.of(
                        new ModelCallLogResponse(
                                4001L,
                                5001L,
                                3001L,
                                3001L,
                                "openai",
                                "gpt-4.1",
                                "req_model_001",
                                "SUCCESS",
                                123,
                                456,
                                789L,
                                false,
                                "AI_DIRECT",
                                5001L,
                                "APP_PAGE_GEN",
                                2,
                                null,
                                LocalDateTime.of(2026, 6, 24, 11, 0)
                        )
                ))
                .pageNo(1)
                .pageSize(10)
                .total(1)
                .build());

        mockMvc.perform(get("/v1/admin/model-call-logs")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].taskId").value(5001))
                .andExpect(jsonPath("$.data.records[0].appId").value(3001))
                .andExpect(jsonPath("$.data.records[0].providerCode").value("openai"))
                .andExpect(jsonPath("$.data.records[0].modelName").value("gpt-4.1"))
                .andExpect(jsonPath("$.data.records[0].generationSource").value("AI_DIRECT"))
                .andExpect(jsonPath("$.data.records[0].fallbackUsed").value(false))
                .andExpect(jsonPath("$.data.records[0].status").value("SUCCESS"));
    }

    @Test
    void shouldIncludeRequestIdForMetricsSummary() throws Exception {
        given(adminQueryApplicationService.getMetricsSummary(any())).willReturn(sampleMetricSummary());

        mockMvc.perform(get("/v1/admin/metrics/summary")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private static final class TestingAuthenticationToken
            extends org.springframework.security.authentication.UsernamePasswordAuthenticationToken {

        private TestingAuthenticationToken(CurrentUser principal) {
            super(principal, null, List.of());
        }
    }
}
