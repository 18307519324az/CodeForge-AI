package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.export.ExportPackageCreateResponse;
import com.codeforge.ai.application.dto.export.ExportPackageListItemResponse;
import com.codeforge.ai.application.service.ExportPackageApplicationService;
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

class ExportControllerTest {

    private MockMvc mockMvc;
    private ExportPackageApplicationService exportPackageApplicationService;

    @BeforeEach
    void setUp() {
        exportPackageApplicationService = mock(ExportPackageApplicationService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ExportController(exportPackageApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturnUnifiedCreateExportPackageResponse() throws Exception {
        given(exportPackageApplicationService.createExportPackage(any(), any())).willReturn(new ExportPackageCreateResponse(
                9001L,
                3001L,
                7001L,
                1,
                "ZIP",
                "READY",
                "zip_v1_20260711120000.zip",
                LocalDateTime.of(2026, 6, 24, 0, 0)
        ));

        mockMvc.perform(post("/v1/export-packages")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": 3001,
                                  "appVersionId": 7001,
                                  "packageType": "ZIP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.id").value(9001))
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    void shouldValidateCreateExportPackageRequest() throws Exception {
        mockMvc.perform(post("/v1/export-packages")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": null,
                                  "appVersionId": null,
                                  "packageType": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldReturnExportPackageList() throws Exception {
        given(exportPackageApplicationService.listExportPackages(any(), any())).willReturn(List.of(
                new ExportPackageListItemResponse(
                        9001L,
                        3001L,
                        7001L,
                        "ZIP",
                        "READY",
                        "zip_v1_20260711120000.zip",
                        LocalDateTime.of(2026, 6, 24, 0, 1)
                )
        ));

        mockMvc.perform(get("/v1/apps/3001/export-packages")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "viewer", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].packageType").value("ZIP"))
                .andExpect(jsonPath("$.data[0].status").value("READY"));
    }

    private static final class TestingAuthenticationToken
            extends org.springframework.security.authentication.UsernamePasswordAuthenticationToken {

        private TestingAuthenticationToken(CurrentUser principal) {
            super(principal, null, List.of());
        }
    }
}
