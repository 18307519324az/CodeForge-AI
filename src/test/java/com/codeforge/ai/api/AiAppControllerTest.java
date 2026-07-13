package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.app.AiAppDetailResponse;
import com.codeforge.ai.application.dto.app.AiAppListItemResponse;
import com.codeforge.ai.application.service.AiAppApplicationService;
import com.codeforge.ai.application.service.GenerationTaskApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.GlobalExceptionHandler;
import com.codeforge.ai.shared.response.PageResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiAppControllerTest {

    private MockMvc mockMvc;
    private AiAppApplicationService aiAppApplicationService;
    private GenerationTaskApplicationService generationTaskApplicationService;

    @BeforeEach
    void setUp() {
        aiAppApplicationService = mock(AiAppApplicationService.class);
        generationTaskApplicationService = mock(GenerationTaskApplicationService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AiAppController(
                        aiAppApplicationService,
                        generationTaskApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturnUnifiedCreateAppResponse() throws Exception {
        given(aiAppApplicationService.createApp(any(), any())).willReturn(new AiAppDetailResponse(
                3001L, 1001L, "App A", "demo", null, "WEB_APP", "DRAFT", "PRIVATE",
                null, null, LocalDateTime.of(2026, 6, 22, 16, 0), LocalDateTime.of(2026, 6, 22, 16, 0),
                null, null, null, null, null, null, null, null
        ));

        mockMvc.perform(post("/v1/apps")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": 1001,
                                  "name": "App A",
                                  "description": "demo",
                                  "appType": "WEB_APP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.id").value(3001))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void shouldValidateCreateAppRequest() throws Exception {
        mockMvc.perform(post("/v1/apps")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": null,
                                  "name": "",
                                  "appType": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldReturnPagedApps() throws Exception {
        given(aiAppApplicationService.listApps(any(), any())).willReturn(PageResponse.<AiAppListItemResponse>builder()
                .records(List.of(new AiAppListItemResponse(
                        3001L, 1001L, "App A", "demo", null, "WEB_APP", "DRAFT", "PRIVATE",
                        null, null, LocalDateTime.of(2026, 6, 22, 16, 0), LocalDateTime.of(2026, 6, 22, 16, 0),
                        null, null, null, null, null, null, null, null
                )))
                .pageNo(1)
                .pageSize(10)
                .total(1)
                .build());

        mockMvc.perform(get("/v1/apps")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .queryParam("workspaceId", "1001")
                        .queryParam("pageNo", "1")
                        .queryParam("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].id").value(3001))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldReturnAppDetail() throws Exception {
        given(aiAppApplicationService.getApp(any(), any())).willReturn(new AiAppDetailResponse(
                3001L, 1001L, "App A", "demo", null, "WEB_APP", "DRAFT", "PRIVATE",
                null, null, LocalDateTime.of(2026, 6, 22, 16, 0), LocalDateTime.of(2026, 6, 22, 16, 0),
                null, null, null, null, null, null, null, null
        ));

        mockMvc.perform(get("/v1/apps/3001")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(3001))
                .andExpect(jsonPath("$.data.name").value("App A"));
    }

    @Test
    void shouldUpdateApp() throws Exception {
        given(aiAppApplicationService.updateApp(any(), any(), any())).willReturn(new AiAppDetailResponse(
                3001L, 1001L, "App B", "updated", null, "WEB_APP", "DRAFT", "PUBLIC",
                null, null, LocalDateTime.of(2026, 6, 22, 16, 0), LocalDateTime.of(2026, 6, 22, 17, 0),
                null, null, null, null, null, null, null, null
        ));

        mockMvc.perform(put("/v1/apps/3001")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "App B",
                                  "description": "updated",
                                  "visibility": "PUBLIC"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("App B"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"));
    }

    private static final class TestingAuthenticationToken
            extends org.springframework.security.authentication.UsernamePasswordAuthenticationToken {

        private TestingAuthenticationToken(CurrentUser principal) {
            super(principal, null, List.of());
        }
    }
}
