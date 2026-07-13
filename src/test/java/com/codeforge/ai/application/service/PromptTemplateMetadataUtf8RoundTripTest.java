package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.prompt.PromptTemplateDetailResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUserDetailResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUserListItemResponse;
import com.codeforge.ai.application.dto.prompt.PublishedPromptTemplateQueryRequest;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PromptTemplateMetadataUtf8RoundTripTest {

    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private PromptTemplateApplicationService promptTemplateApplicationService;

    @BeforeEach
    void setUp() {
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        GenerationRecordEntityMapper generationRecordEntityMapper = mock(GenerationRecordEntityMapper.class);
        ModelCallLogEntityMapper modelCallLogEntityMapper = mock(ModelCallLogEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        promptTemplateApplicationService = new PromptTemplateApplicationService(
                promptTemplateEntityMapper,
                promptTemplateVersionEntityMapper,
                generationRecordEntityMapper,
                modelCallLogEntityMapper,
                workspaceAccessService,
                mock(com.codeforge.ai.infrastructure.audit.AuditLogWriter.class),
                new ObjectMapper());
    }

    @Test
    void publishedTemplateMetadataPreservesUtf8Bytes() {
        given(workspaceAccessService.listReadableWorkspaceIds(any())).willReturn(List.of(1001L));
        PromptTemplateEntity published = templateEntity();
        given(promptTemplateEntityMapper.findAccessibleTemplates(
                eq(List.of(1001L)), eq(null), eq(null), eq("PUBLISHED")))
                .willReturn(List.of(published));
        given(promptTemplateVersionEntityMapper.findPublishedVersionsByTemplateIds(List.of(1L)))
                .willReturn(List.of(versionEntity()));

        PublishedPromptTemplateQueryRequest request = new PublishedPromptTemplateQueryRequest();
        request.setPageNo(1);
        request.setPageSize(10);

        var response = promptTemplateApplicationService.listPublishedTemplates(
                new CurrentUser(2001L, "reader", List.of("USER")), request);

        PromptTemplateUserListItemResponse item = response.records().getFirst();
        assertUtf8Metadata(item.templateName(), "Vue 项目生成");
        assertUtf8Metadata(item.description(), "通用 Vue 项目模板");
    }

    @Test
    void adminTemplateDetailMetadataPreservesUtf8Bytes() {
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(templateEntity());
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(1L, 1))
                .willReturn(versionEntity());

        PromptTemplateDetailResponse detail = promptTemplateApplicationService.getTemplate(
                new CurrentUser(2001L, "admin", List.of("USER", "PLATFORM_ADMIN")), 1L);

        assertUtf8Metadata(detail.templateName(), "Vue 项目生成");
        assertUtf8Metadata(detail.remark(), "通用 Vue 项目模板");
    }

    @Test
    void userTemplateDetailDoesNotExposeSystemPrompt() {
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(templateEntity());
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(1L, 1))
                .willReturn(versionEntity());

        PromptTemplateUserDetailResponse detail = promptTemplateApplicationService.getPublishedTemplate(
                new CurrentUser(2001L, "reader", List.of("USER")), 1L);

        assertUtf8Metadata(detail.templateName(), "Vue 项目生成");
        assertUtf8Metadata(detail.description(), "通用 Vue 项目模板");
        assertThat(detail.exampleRequirement()).contains("请生成一个");
        assertThat(detail.variables()).isNotEmpty();
        assertThat(detail.variables().getFirst().description()).isEqualTo("应用名称");
    }

    @Test
    void versionBodyRegressionAfterMetadataRepair() {
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(templateEntity());
        given(promptTemplateVersionEntityMapper.findByTemplateId(1L))
                .willReturn(List.of(versionEntity()));

        var versions = promptTemplateApplicationService.listTemplateVersions(
                new CurrentUser(2001L, "admin", List.of("USER", "PLATFORM_ADMIN")), 1L);

        assertThat(versions.getFirst().systemPrompt()).contains("Vue 代码生成助手");
        assertThat(versions.getFirst().userPrompt()).isEqualTo("请生成一个 {{app_name}} Vue 项目");
    }

    private static PromptTemplateEntity templateEntity() {
        PromptTemplateEntity entity = PromptTemplateEntity.builder()
                .id(1L)
                .workspaceId(1001L)
                .templateName("Vue 项目生成")
                .templateScene("CODE_GEN")
                .status("PUBLISHED")
                .currentVersionNo(1)
                .remark("通用 Vue 项目模板")
                .build();
        entity.setUpdatedAt(LocalDateTime.of(2026, 7, 7, 12, 0));
        return entity;
    }

    private static PromptTemplateVersionEntity versionEntity() {
        return PromptTemplateVersionEntity.builder()
                .id(11L)
                .templateId(1L)
                .versionNo(1)
                .systemPrompt("你是专业的 Vue 代码生成助手。请根据用户需求生成结构清晰、可直接运行的 Vue 项目。")
                .userPrompt("请生成一个 {{app_name}} Vue 项目")
                .variablesJson("{\"app_name\":{\"type\":\"string\",\"required\":true,\"description\":\"应用名称\"}}")
                .publishedAt(LocalDateTime.of(2026, 7, 7, 11, 43))
                .build();
    }

    private static void assertUtf8Metadata(String actual, String expected) {
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.getBytes(StandardCharsets.UTF_8)).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
        assertThat(actual).doesNotContain("?");
        assertThat(actual).doesNotContain("椤");
    }
}
