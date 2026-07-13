package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.admin.MetricSummaryResponse;
import com.codeforge.ai.application.service.AdminAiRoutingApplicationService;
import com.codeforge.ai.application.service.AdminModelProviderApplicationService;
import com.codeforge.ai.application.service.AdminPromptTemplateApplicationService;
import com.codeforge.ai.application.service.release.AdminPromptRuntimeGateApplicationService;
import com.codeforge.ai.application.service.AdminMetricsApplicationService;
import com.codeforge.ai.application.service.AdminQueryApplicationService;
import com.codeforge.ai.application.service.QuotaApplicationService;
import com.codeforge.ai.application.service.UserApplicationService;
import com.codeforge.ai.application.service.WorkspaceApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.infrastructure.security.JwtAuthenticationFilter;
import com.codeforge.ai.infrastructure.security.JwtProperties;
import com.codeforge.ai.infrastructure.security.JwtTokenProvider;
import com.codeforge.ai.infrastructure.security.SecurityConfig;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.exception.GlobalExceptionHandler;
import com.codeforge.ai.shared.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityIntegrationTest.TestConfig.class)
class SecurityIntegrationTest {

    private static final String JWT_SECRET = "change-this-jwt-secret-change-this-jwt-secret";

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({
            SecurityConfig.class,
            GlobalExceptionHandler.class,
            RequestIdFilter.class
    })
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            return objectMapper;
        }

        @Bean
        HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new HandlerMappingIntrospector();
        }

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties("codeforge-ai", JWT_SECRET, 7200);
        }

        @Bean
        JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
            return new JwtTokenProvider(jwtProperties);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
            return new JwtAuthenticationFilter(jwtTokenProvider, objectMapper);
        }

        @Bean
        UserApplicationService userApplicationService() {
            return mock(UserApplicationService.class);
        }

        @Bean
        WorkspaceApplicationService workspaceApplicationService() {
            return mock(WorkspaceApplicationService.class);
        }

        @Bean
        QuotaApplicationService quotaApplicationService() {
            return mock(QuotaApplicationService.class);
        }

        @Bean
        AdminMetricsApplicationService adminMetricsApplicationService() {
            return mock(AdminMetricsApplicationService.class);
        }

        @Bean
        AdminQueryApplicationService adminQueryApplicationService() {
            return mock(AdminQueryApplicationService.class);
        }

        @Bean
        AdminModelProviderApplicationService adminModelProviderApplicationService() {
            return mock(AdminModelProviderApplicationService.class);
        }

        @Bean
        AdminAiRoutingApplicationService adminAiRoutingApplicationService() {
            return mock(AdminAiRoutingApplicationService.class);
        }

        @Bean
        AdminPromptTemplateApplicationService adminPromptTemplateApplicationService() {
            return mock(AdminPromptTemplateApplicationService.class);
        }

        @Bean
        AdminPromptRuntimeGateApplicationService adminPromptRuntimeGateApplicationService() {
            return mock(AdminPromptRuntimeGateApplicationService.class);
        }

        @Bean
        UserController userController(UserApplicationService userApplicationService) {
            return new UserController(userApplicationService);
        }

        @Bean
        WorkspaceController workspaceController(WorkspaceApplicationService workspaceApplicationService) {
            return new WorkspaceController(workspaceApplicationService);
        }

        @Bean
        AdminController adminController(QuotaApplicationService quotaApplicationService,
                                        AdminQueryApplicationService adminQueryApplicationService,
                                        AdminMetricsApplicationService adminMetricsApplicationService,
                                        AdminModelProviderApplicationService adminModelProviderApplicationService,
                                        AdminAiRoutingApplicationService adminAiRoutingApplicationService,
                                        AdminPromptTemplateApplicationService adminPromptTemplateApplicationService,
                                        AdminPromptRuntimeGateApplicationService adminPromptRuntimeGateApplicationService) {
            return new AdminController(
                    quotaApplicationService,
                    adminQueryApplicationService,
                    adminMetricsApplicationService,
                    adminModelProviderApplicationService,
                    adminAiRoutingApplicationService,
                    adminPromptTemplateApplicationService,
                    adminPromptRuntimeGateApplicationService);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private WebApplicationContext webApplicationContext;

    @org.springframework.beans.factory.annotation.Autowired
    private JwtTokenProvider jwtTokenProvider;

    @org.springframework.beans.factory.annotation.Autowired
    private WorkspaceApplicationService workspaceApplicationService;

    @org.springframework.beans.factory.annotation.Autowired
    private AdminQueryApplicationService adminQueryApplicationService;

    @org.springframework.beans.factory.annotation.Autowired
    private RequestIdFilter requestIdFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .addFilters(requestIdFilter)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldRejectInvalidJwtToken() throws Exception {
        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldRejectExpiredJwtToken() throws Exception {
        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer " + createExpiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldRejectAdminEndpointForNonAdminUser() throws Exception {
        given(adminQueryApplicationService.getMetricsSummary(any())).willReturn(
                new MetricSummaryResponse(
                        LocalDate.of(2026, 6, 24),
                        1L,
                        1L,
                        0L,
                        BigDecimal.ONE,
                        0L,
                        0L,
                        1L,
                        MetricSummaryResponse.MODEL_CALL_ALL_TIME_SCOPE,
                        LocalDateTime.of(2026, 6, 24, 12, 0),
                        LocalDateTime.of(2026, 6, 24, 12, 1),
                        "FRESH",
                        129600L)
        );

        mockMvc.perform(get("/v1/admin/metrics/summary")
                        .header("Authorization", "Bearer " + createAccessToken(List.of("USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldReturnForbiddenWhenWorkspaceMemberCheckFails() throws Exception {
        given(workspaceApplicationService.getWorkspace(any(), eq(1001L)))
                .willThrow(new BusinessException(ErrorCode.RESOURCE_FORBIDDEN));

        mockMvc.perform(get("/v1/workspaces/1001")
                        .header("Authorization", "Bearer " + createAccessToken(List.of("USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private String createAccessToken(List<String> roles) {
        return jwtTokenProvider.createAccessToken(new CurrentUser(1001L, "tester", roles));
    }

    private String createExpiredToken() {
        SecretKey secretKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("codeforge-ai")
                .subject("1001")
                .claim("uid", 1001L)
                .claim("account", "tester")
                .claim("roles", List.of("USER"))
                .issuedAt(Date.from(now.minusSeconds(3600)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(secretKey)
                .compact();
    }
}
