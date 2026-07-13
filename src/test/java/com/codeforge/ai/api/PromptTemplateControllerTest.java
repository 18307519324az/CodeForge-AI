package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.prompt.PromptTemplateDetailResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateListItemResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUserDetailResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUserListItemResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionResponse;
import com.codeforge.ai.application.service.PromptTemplateApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.GlobalExceptionHandler;
import com.codeforge.ai.shared.response.PageResponse;
import com.codeforge.ai.shared.web.RequestIdFilter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PromptTemplateControllerTest {

    private MockMvc mockMvc;
    private PromptTemplateApplicationService promptTemplateApplicationService;

    @BeforeEach
    void setUp() {
        promptTemplateApplicationService = mock(PromptTemplateApplicationService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new PromptTemplateController(promptTemplateApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturnUnifiedCreateTemplateResponse() throws Exception {
        given(promptTemplateApplicationService.createTemplate(any(), any())).willReturn(new PromptTemplateDetailResponse(
                4001L, 1001L, "landing-page", "PAGE_GENERATION", "DRAFT", 1, null,
                new PromptTemplateVersionResponse(5001L, 4001L, 1, "DRAFT", "system", "user", null, null, null, null)
        ));

        mockMvc.perform(post("/v1/prompt-templates")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": 1001,
                                  "templateName": "landing-page",
                                  "templateScene": "PAGE_GENERATION",
                                  "systemPrompt": "system",
                                  "userPrompt": "user"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.id").value(4001))
                .andExpect(jsonPath("$.data.currentVersion.versionNo").value(1));
    }

    @Test
    void shouldValidateCreateTemplateRequest() throws Exception {
        mockMvc.perform(post("/v1/prompt-templates")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": null,
                                  "templateName": "",
                                  "templateScene": "",
                                  "systemPrompt": "",
                                  "userPrompt": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldReturnPagedTemplates() throws Exception {
        given(promptTemplateApplicationService.listTemplates(any(), any())).willReturn(PageResponse.<PromptTemplateListItemResponse>builder()
                .records(List.of(new PromptTemplateListItemResponse(
                        4001L, 1001L, "landing-page", "PAGE_GENERATION", "DRAFT", 1, 1, null
                )))
                .pageNo(1)
                .pageSize(10)
                .total(1)
                .build());

        mockMvc.perform(get("/v1/prompt-templates")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .queryParam("workspaceId", "1001")
                        .queryParam("pageNo", "1")
                        .queryParam("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].id").value(4001))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldReturnTemplateDetail() throws Exception {
        given(promptTemplateApplicationService.getTemplate(any(), any())).willReturn(new PromptTemplateDetailResponse(
                4001L, 1001L, "landing-page", "PAGE_GENERATION", "DRAFT", 1, null,
                new PromptTemplateVersionResponse(5001L, 4001L, 1, "DRAFT", "system", "user", null, null, null, null)
        ));

        mockMvc.perform(get("/v1/prompt-templates/4001")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(4001))
                .andExpect(jsonPath("$.data.currentVersion.versionNo").value(1));
    }

    @Test
    void shouldReturnPublishedTemplatesWithChineseMetadata() throws Exception {
        given(promptTemplateApplicationService.listPublishedTemplates(any(), any()))
                .willReturn(PageResponse.<PromptTemplateUserListItemResponse>builder()
                        .records(List.of(new PromptTemplateUserListItemResponse(
                                1L,
                                "Vue 项目生成",
                                "通用 Vue 项目模板",
                                "CODE_GEN",
                                "代码生成",
                                "WEB_APP",
                                1,
                                11L,
                                java.time.LocalDateTime.of(2026, 7, 7, 12, 0)
                        )))
                        .pageNo(1)
                        .pageSize(10)
                        .total(1)
                        .build());

        mockMvc.perform(get("/v1/prompt-templates/published")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "reader", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].templateName").value("Vue 项目生成"))
                .andExpect(jsonPath("$.data.records[0].description").value("通用 Vue 项目模板"));
    }

    @Test
    void shouldReturnPublishedTemplateDetailWithoutSystemPrompt() throws Exception {
        given(promptTemplateApplicationService.getPublishedTemplate(any(), eq(1L)))
                .willReturn(new PromptTemplateUserDetailResponse(
                        1L,
                        "Vue 项目生成",
                        "通用 Vue 项目模板",
                        "CODE_GEN",
                        "代码生成",
                        "WEB_APP",
                        "请生成一个 {{app_name}} Vue 项目",
                        List.of(new com.codeforge.ai.application.dto.prompt.PromptTemplateVariableItemResponse(
                                "app_name", "string", true, "应用名称")),
                        new com.codeforge.ai.application.dto.prompt.PromptTemplatePublishedVersionResponse(
                                11L, 1, java.time.LocalDateTime.of(2026, 7, 7, 12, 0))
                ));

        mockMvc.perform(get("/v1/prompt-templates/published/1")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "reader", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateName").value("Vue 项目生成"))
                .andExpect(jsonPath("$.data.description").value("通用 Vue 项目模板"))
                .andExpect(jsonPath("$.data.exampleRequirement").value("请生成一个 {{app_name}} Vue 项目"))
                .andExpect(jsonPath("$.data.variables[0].description").value("应用名称"))
                .andExpect(jsonPath("$.data.systemPrompt").doesNotExist());
    }

    @Test
    void shouldListTemplateVersions() throws Exception {
        given(promptTemplateApplicationService.listTemplateVersions(any(), eq(4001L)))
                .willReturn(List.of(new PromptTemplateVersionResponse(
                        5001L, 4001L, 1, "DRAFT", "系统提示词", "请生成一个 Vue 项目", null, null, null, null)));

        mockMvc.perform(get("/v1/prompt-templates/4001/versions")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userPrompt").value("请生成一个 Vue 项目"));
    }

    @Test
    void shouldPublishTemplateVersion() throws Exception {
        given(promptTemplateApplicationService.publishTemplateVersion(any(), eq(4001L), eq(1)))
                .willReturn(new PromptTemplateDetailResponse(
                        4001L, 1001L, "Vue 项目生成", "CODE_GEN", "PUBLISHED", 1, "通用 Vue 项目模板",
                        new PromptTemplateVersionResponse(5001L, 4001L, 1, "PUBLISHED", "system", "user", null, null, 2001L,
                                java.time.LocalDateTime.of(2026, 7, 7, 12, 0))
                ));

        mockMvc.perform(post("/v1/prompt-templates/4001/versions/1/publish")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "admin", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.templateName").value("Vue 项目生成"));
    }

    @Test
    void shouldUpdateTemplate() throws Exception {
        given(promptTemplateApplicationService.updateTemplate(any(), any(), any())).willReturn(new PromptTemplateDetailResponse(
                4001L, 1001L, "landing-page-v2", "PAGE_GENERATION", "DRAFT", 1, "remark",
                new PromptTemplateVersionResponse(5001L, 4001L, 1, "DRAFT", "system", "user", null, null, null, null)
        ));

        mockMvc.perform(put("/v1/prompt-templates/4001")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateName": "landing-page-v2",
                                  "templateScene": "PAGE_GENERATION",
                                  "remark": "remark"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.templateName").value("landing-page-v2"))
                .andExpect(jsonPath("$.data.remark").value("remark"));
    }

    private static final class TestingAuthenticationToken
            extends org.springframework.security.authentication.UsernamePasswordAuthenticationToken {

        private TestingAuthenticationToken(CurrentUser principal) {
            super(principal, null, List.of());
        }
    }
}
