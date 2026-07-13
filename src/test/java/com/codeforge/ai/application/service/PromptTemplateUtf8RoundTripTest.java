package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionResponse;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PromptTemplateUtf8RoundTripTest {

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
    void publishedTemplateVisibleToUserWithChineseFields() {
        given(workspaceAccessService.listReadableWorkspaceIds(any())).willReturn(List.of(1001L));
        PromptTemplateEntity published = PromptTemplateEntity.builder()
                .id(1L)
                .workspaceId(1001L)
                .templateName("Vue 项目生成")
                .templateScene("CODE_GEN")
                .status("PUBLISHED")
                .currentVersionNo(1)
                .remark("通用 Vue 项目模板")
                .build();
        published.setUpdatedAt(LocalDateTime.of(2026, 7, 7, 12, 0));
        given(promptTemplateEntityMapper.findAccessibleTemplates(
                eq(List.of(1001L)), eq(null), eq(null), eq("PUBLISHED")))
                .willReturn(List.of(published));
        given(promptTemplateVersionEntityMapper.findPublishedVersionsByTemplateIds(List.of(1L)))
                .willReturn(List.of(PromptTemplateVersionEntity.builder()
                        .id(11L)
                        .templateId(1L)
                        .versionNo(1)
                        .userPrompt("请生成一个 {{app_name}} Vue 项目")
                        .publishedAt(LocalDateTime.of(2026, 7, 7, 11, 43))
                        .build()));

        var request = new com.codeforge.ai.application.dto.prompt.PublishedPromptTemplateQueryRequest();
        request.setPageNo(1);
        request.setPageSize(10);

        var response = promptTemplateApplicationService.listPublishedTemplates(
                new CurrentUser(2001L, "reader", List.of("USER")), request);

        assertThat(response.records()).hasSize(1);
        assertThat(response.records().getFirst().templateName()).isEqualTo("Vue 项目生成");
        assertThat(response.records().getFirst().description()).isEqualTo("通用 Vue 项目模板");
    }

    @Test
    void listTemplateVersionsReturnsChinesePromptText() {
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(PromptTemplateEntity.builder()
                .id(1L)
                .workspaceId(1001L)
                .templateName("Vue 项目生成")
                .templateScene("CODE_GEN")
                .status("PUBLISHED")
                .currentVersionNo(1)
                .build());
        given(promptTemplateVersionEntityMapper.findByTemplateId(1L))
                .willReturn(List.of(PromptTemplateVersionEntity.builder()
                        .id(11L)
                        .templateId(1L)
                        .versionNo(1)
                        .systemPrompt("你是专业的 Vue 代码生成助手。")
                        .userPrompt("请生成一个 {{app_name}} Vue 项目")
                        .publishedAt(LocalDateTime.of(2026, 7, 7, 11, 43))
                        .build()));

        List<PromptTemplateVersionResponse> versions = promptTemplateApplicationService.listTemplateVersions(
                new CurrentUser(2001L, "admin", List.of("USER", "PLATFORM_ADMIN")), 1L);

        assertThat(versions).hasSize(1);
        assertThat(versions.getFirst().userPrompt()).contains("请生成一个");
        assertThat(versions.getFirst().systemPrompt()).contains("Vue 代码生成助手");
    }
}
