package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.prompt.PublishedPromptTemplateQueryRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUserListItemResponse;
import com.codeforge.ai.application.dto.task.GenerationTaskCreateRequest;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.model.PromptTemplateRenderer;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTraceResolver;
import com.codeforge.ai.domain.task.entity.GenerationRecordEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PublishedPromptTemplateListTest {

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
    void shouldListOnlyPublishedTemplatesForUser() {
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
        published.setUpdatedAt(LocalDateTime.of(2026, 7, 6, 10, 0));
        given(promptTemplateEntityMapper.findAccessibleTemplates(
                eq(List.of(1001L)), eq(null), eq(null), eq("PUBLISHED")))
                .willReturn(List.of(published));
        given(promptTemplateVersionEntityMapper.findPublishedVersionsByTemplateIds(List.of(1L)))
                .willReturn(List.of(PromptTemplateVersionEntity.builder()
                        .id(11L)
                        .templateId(1L)
                        .versionNo(1)
                        .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                        .build()));

        PublishedPromptTemplateQueryRequest request = new PublishedPromptTemplateQueryRequest();
        request.setPageNo(1);
        request.setPageSize(10);

        PageResponse<PromptTemplateUserListItemResponse> response = promptTemplateApplicationService.listPublishedTemplates(
                new CurrentUser(2001L, "reader", List.of("USER")), request);

        assertThat(response.records()).hasSize(1);
        assertThat(response.records().getFirst().templateName()).isEqualTo("Vue 项目生成");
        assertThat(response.records().getFirst().publishedVersionId()).isEqualTo(11L);
    }
}

class DraftTemplateHiddenFromUserTest {

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
    void shouldHideDraftTemplateFromPublishedList() {
        given(workspaceAccessService.listReadableWorkspaceIds(any())).willReturn(List.of(1001L));
        given(promptTemplateEntityMapper.findAccessibleTemplates(
                eq(List.of(1001L)), eq(null), eq(null), eq("PUBLISHED")))
                .willReturn(List.of());

        PublishedPromptTemplateQueryRequest request = new PublishedPromptTemplateQueryRequest();
        request.setPageNo(1);
        request.setPageSize(10);

        PageResponse<PromptTemplateUserListItemResponse> response = promptTemplateApplicationService.listPublishedTemplates(
                new CurrentUser(2001L, "reader", List.of("USER")), request);

        assertThat(response.records()).isEmpty();
    }

    @Test
    void shouldRejectDraftTemplateDetailForUser() {
        given(promptTemplateEntityMapper.selectOneById(2L)).willReturn(PromptTemplateEntity.builder()
                .id(2L)
                .workspaceId(1001L)
                .templateName("API 接口生成")
                .status("DRAFT")
                .currentVersionNo(1)
                .build());

        assertThatThrownBy(() -> promptTemplateApplicationService.getPublishedTemplate(
                new CurrentUser(2001L, "reader", List.of("USER")), 2L))
                .isInstanceOf(BusinessException.class);
    }
}

class PromptTemplateVersionPinnedTest {

    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private GenerationTaskApplicationService generationTaskApplicationService;

    @BeforeEach
    void setUp() {
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        generationTaskApplicationService = new GenerationTaskApplicationService(
                mock(com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper.class),
                promptTemplateEntityMapper,
                promptTemplateVersionEntityMapper,
                mock(com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper.class),
                mock(com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEventEntityMapper.class),
                mock(GenerationRecordEntityMapper.class),
                workspaceAccessService,
                mock(QuotaApplicationService.class),
                new GenerationTaskStreamRegistry(new PublicGenerationStreamEventMapper(new com.fasterxml.jackson.databind.ObjectMapper())),
                new PublicGenerationStreamEventMapper(new com.fasterxml.jackson.databind.ObjectMapper()),
                mock(GenerationTaskExecutionDispatcher.class),
                mock(com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper.class),
                mock(com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper.class),
                mock(AiDirectGenerationApplicationService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(org.springframework.transaction.PlatformTransactionManager.class));
    }

    @Test
    void shouldPinPromptTemplateVersionByVersionId() {
        PromptTemplateVersionEntity pinnedVersion = PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .systemPrompt("system")
                .userPrompt("user {{app_name}}")
                .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build();
        given(promptTemplateVersionEntityMapper.selectOneById(5001L)).willReturn(pinnedVersion);
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status("PUBLISHED")
                .currentVersionNo(2)
                .build());

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setPromptTemplateId(4001L);
        request.setPromptTemplateVersionId(5001L);
        request.setTemplateVariables(Map.of("app_name", "客户管理后台"));

        Object resolved = ReflectionTestUtils.invokeMethod(
                generationTaskApplicationService,
                "resolvePromptTemplateVersion",
                new CurrentUser(2001L, "editor", List.of("USER")),
                1001L,
                request);

        assertThat(resolved).isInstanceOf(PromptTemplateVersionEntity.class);
        assertThat(((PromptTemplateVersionEntity) resolved).getId()).isEqualTo(5001L);
        assertThat(((PromptTemplateVersionEntity) resolved).getVersionNo()).isEqualTo(1);
    }
}

class PromptTemplateVariableValidationTest {

    @Test
    void shouldRejectMissingRequiredTemplateVariable() {
        PromptTemplateVersionEntity versionEntity = PromptTemplateVersionEntity.builder()
                .systemPrompt("system")
                .userPrompt("生成 {{app_name}} 应用")
                .build();

        assertThatThrownBy(() -> PromptTemplateRenderer.validateRequiredVariables(
                versionEntity.getSystemPrompt(), versionEntity.getUserPrompt(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("app_name");
    }
}

class GenerationTaskPromptTemplateTraceTest {

    private GenerationRecordEntityMapper generationRecordEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private PromptTemplateTraceResolver promptTemplateTraceResolver;

    @BeforeEach
    void setUp() {
        generationRecordEntityMapper = mock(GenerationRecordEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateTraceResolver = new PromptTemplateTraceResolver(
                generationRecordEntityMapper,
                mock(com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper.class),
                promptTemplateVersionEntityMapper,
                promptTemplateEntityMapper);
    }

    @Test
    void shouldResolvePromptTemplateTraceFromGenerationRecord() {
        given(generationRecordEntityMapper.findLatestByTaskId(66L)).willReturn(GenerationRecordEntity.builder()
                .taskId(66L)
                .promptTemplateVersionId(5001L)
                .build());
        given(promptTemplateVersionEntityMapper.selectOneById(5001L)).willReturn(PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .build());
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .templateName("Vue 项目生成")
                .build());

        var trace = promptTemplateTraceResolver.resolveByTaskId(66L);

        assertThat(trace.promptTemplateVersionId()).isEqualTo(5001L);
        assertThat(trace.promptTemplateCode()).isEqualTo("Vue 项目生成");
        assertThat(trace.promptTemplateVersionNo()).isEqualTo(1);
    }
}
