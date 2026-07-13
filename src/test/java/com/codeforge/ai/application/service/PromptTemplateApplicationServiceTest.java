package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.prompt.PromptTemplateCreateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateDetailResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateQueryRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionCreateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionResponse;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PromptTemplateApplicationServiceTest {

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
    void shouldCreateTemplateWithInitialVersion() {
        doAnswer(invocation -> {
            PromptTemplateEntity entity = invocation.getArgument(0);
            entity.setId(4001L);
            return 1;
        }).when(promptTemplateEntityMapper).insertTemplate(any(PromptTemplateEntity.class));

        doAnswer(invocation -> {
            PromptTemplateVersionEntity entity = invocation.getArgument(0);
            entity.setId(5001L);
            return 1;
        }).when(promptTemplateVersionEntityMapper).insertVersion(any(PromptTemplateVersionEntity.class));

        PromptTemplateCreateRequest request = new PromptTemplateCreateRequest();
        request.setWorkspaceId(1001L);
        request.setTemplateName("landing-page");
        request.setTemplateScene("PAGE_GENERATION");
        request.setSystemPrompt("system");
        request.setUserPrompt("user");

        PromptTemplateDetailResponse response = promptTemplateApplicationService.createTemplate(
                new CurrentUser(2001L, "editor", List.of("USER")),
                request
        );

        ArgumentCaptor<PromptTemplateEntity> templateCaptor = ArgumentCaptor.forClass(PromptTemplateEntity.class);
        ArgumentCaptor<PromptTemplateVersionEntity> versionCaptor = ArgumentCaptor.forClass(PromptTemplateVersionEntity.class);
        verify(promptTemplateEntityMapper).insertTemplate(templateCaptor.capture());
        verify(promptTemplateVersionEntityMapper).insertVersion(versionCaptor.capture());

        assertThat(templateCaptor.getValue().getCurrentVersionNo()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(1);
        assertThat(response.currentVersion()).isNotNull();
        assertThat(response.currentVersion().versionNo()).isEqualTo(1);
    }

    @Test
    void shouldPublishTemplateVersion() {
        PromptTemplateEntity templateEntity = PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .templateName("landing-page")
                .templateScene("PAGE_GENERATION")
                .status("DRAFT")
                .currentVersionNo(1)
                .build();
        PromptTemplateVersionEntity versionEntity = PromptTemplateVersionEntity.builder()
                .id(5002L)
                .templateId(4001L)
                .versionNo(2)
                .systemPrompt("system-v2")
                .userPrompt("user-v2")
                .build();
        PromptTemplateEntity publishedTemplate = PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .templateName("landing-page")
                .templateScene("PAGE_GENERATION")
                .status("PUBLISHED")
                .currentVersionNo(2)
                .build();
        PromptTemplateVersionEntity publishedVersion = PromptTemplateVersionEntity.builder()
                .id(5002L)
                .templateId(4001L)
                .versionNo(2)
                .systemPrompt("system-v2")
                .userPrompt("user-v2")
                .publishedBy(2001L)
                .publishedAt(LocalDateTime.of(2026, 6, 22, 16, 30))
                .build();

        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(templateEntity, publishedTemplate);
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(4001L, 2)).willReturn(versionEntity, publishedVersion);
        given(promptTemplateVersionEntityMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        PromptTemplateDetailResponse response = promptTemplateApplicationService.publishTemplateVersion(
                new CurrentUser(2001L, "admin", List.of("USER")),
                4001L,
                2
        );

        verify(promptTemplateVersionEntityMapper).markPublished(any(), any(), any(), any());
        verify(promptTemplateEntityMapper).updatePublishedVersion(4001L, "PUBLISHED", 2, 2001L);
        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.currentVersionNo()).isEqualTo(2);
    }

    @Test
    void shouldListTemplatesForReadableWorkspace() {
        given(workspaceAccessService.listReadableWorkspaceIds(any())).willReturn(List.of(1001L));
        PromptTemplateEntity entity = PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .templateName("landing-page")
                .templateScene("PAGE_GENERATION")
                .status("DRAFT")
                .currentVersionNo(1)
                .build();
        entity.setUpdatedAt(LocalDateTime.of(2026, 6, 22, 16, 0));
        given(promptTemplateEntityMapper.findAccessibleTemplates(
                eq(List.of(1001L)), eq("landing"), eq("PAGE_GENERATION"), eq("DRAFT")))
                .willReturn(List.of(entity));

        PromptTemplateQueryRequest request = new PromptTemplateQueryRequest();
        request.setKeyword("landing");
        request.setTemplateScene("PAGE_GENERATION");
        request.setStatus("DRAFT");
        request.setPageNo(1);
        request.setPageSize(10);

        PageResponse<?> response = promptTemplateApplicationService.listTemplates(
                new CurrentUser(2001L, "reader", List.of("USER")), request);

        assertThat(response.records()).hasSize(1);
        assertThat(response.total()).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateTemplateNameOnCreate() {
        given(promptTemplateEntityMapper.findByWorkspaceIdAndTemplateName(1001L, "landing-page"))
                .willReturn(PromptTemplateEntity.builder().id(4002L).build());

        PromptTemplateCreateRequest request = new PromptTemplateCreateRequest();
        request.setWorkspaceId(1001L);
        request.setTemplateName("landing-page");
        request.setTemplateScene("PAGE_GENERATION");
        request.setSystemPrompt("system");
        request.setUserPrompt("user");

        assertThatThrownBy(() -> promptTemplateApplicationService.createTemplate(
                new CurrentUser(2001L, "editor", List.of("USER")), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("templateName");
    }

    @Test
    void shouldCreateNextTemplateVersion() {
        PromptTemplateEntity templateEntity = PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .templateName("landing-page")
                .templateScene("PAGE_GENERATION")
                .status("DRAFT")
                .currentVersionNo(1)
                .build();
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(templateEntity);
        given(promptTemplateVersionEntityMapper.findMaxVersionNo(4001L)).willReturn(2);
        doAnswer(invocation -> {
            PromptTemplateVersionEntity entity = invocation.getArgument(0);
            entity.setId(5003L);
            return 1;
        }).when(promptTemplateVersionEntityMapper).insertVersion(any(PromptTemplateVersionEntity.class));

        PromptTemplateVersionCreateRequest request = new PromptTemplateVersionCreateRequest();
        request.setSystemPrompt("system-v3");
        request.setUserPrompt("user-v3");
        request.setVariablesJson("{\"name\":\"string\"}");
        request.setModelStrategyJson("{\"provider\":\"openai\"}");

        PromptTemplateVersionResponse response = promptTemplateApplicationService.createTemplateVersion(
                new CurrentUser(2001L, "editor", List.of("USER")), 4001L, request);

        assertThat(response.versionNo()).isEqualTo(3);
        assertThat(response.systemPrompt()).isEqualTo("system-v3");
    }
}
