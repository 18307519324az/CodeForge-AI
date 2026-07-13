package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.deploy.DeploymentCreateResponse;
import com.codeforge.ai.application.dto.deploy.DeploymentDetailResponse;
import com.codeforge.ai.application.dto.deploy.DeploymentLogResponse;
import com.codeforge.ai.application.service.DeploymentApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.GlobalExceptionHandler;
import com.codeforge.ai.shared.web.RequestIdFilter;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeploymentControllerTest {

    private MockMvc mockMvc;
    private DeploymentApplicationService deploymentApplicationService;

    @BeforeEach
    void setUp() {
        deploymentApplicationService = mock(DeploymentApplicationService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new DeploymentController(deploymentApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturnUnifiedCreateDeploymentResponse() throws Exception {
        given(deploymentApplicationService.createDeployment(any(), any())).willReturn(new DeploymentCreateResponse(
                9101L,
                3001L,
                7001L,
                "prod",
                "docker",
                "QUEUED",
                "req_demo",
                LocalDateTime.of(2026, 6, 24, 0, 10)
        ));

        mockMvc.perform(post("/v1/deployments")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": 3001,
                                  "appVersionId": 7001,
                                  "environmentCode": "prod",
                                  "deployTarget": "docker",
                                  "runtimeConfigJson": "{\\\"replicas\\\":1}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(9101))
                .andExpect(jsonPath("$.data.deployStatus").value("QUEUED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldValidateCreateDeploymentRequest() throws Exception {
        mockMvc.perform(post("/v1/deployments")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": null,
                                  "appVersionId": null,
                                  "environmentCode": "",
                                  "deployTarget": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldReturnDeploymentDetail() throws Exception {
        given(deploymentApplicationService.getDeployment(any(), any())).willReturn(new DeploymentDetailResponse(
                9101L,
                3001L,
                7001L,
                "prod",
                "docker",
                "QUEUED",
                "{\"replicas\":1}",
                "req_demo",
                null,
                null,
                LocalDateTime.of(2026, 6, 24, 0, 10),
                LocalDateTime.of(2026, 6, 24, 0, 10)
        ));

        mockMvc.perform(get("/v1/deployments/9101")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "viewer", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9101))
                .andExpect(jsonPath("$.data.deployTarget").value("docker"));
    }

    @Test
    void shouldReturnDeploymentLogs() throws Exception {
        given(deploymentApplicationService.getDeploymentLogs(any(), any())).willReturn(List.of(
                new DeploymentLogResponse(9201L, 9101L, "INFO", "Deployment job queued",
                        LocalDateTime.of(2026, 6, 24, 0, 11))
        ));

        mockMvc.perform(get("/v1/deployments/9101/logs")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "viewer", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].logLevel").value("INFO"))
                .andExpect(jsonPath("$.data[0].logMessage").value("Deployment job queued"));
    }

    private static final class TestingAuthenticationToken
            extends org.springframework.security.authentication.UsernamePasswordAuthenticationToken {

        private TestingAuthenticationToken(CurrentUser principal) {
            super(principal, null, List.of());
        }
    }
}
