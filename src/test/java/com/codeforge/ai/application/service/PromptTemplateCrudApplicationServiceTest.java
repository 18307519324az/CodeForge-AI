package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.prompt.PromptTemplateCreateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateDetailResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUpdateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionCreateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionUpdateRequest;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.mockito.ArgumentCaptor;

class PromptTemplateCrudApplicationServiceTest {

    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private GenerationRecordEntityMapper generationRecordEntityMapper;
    private ModelCallLogEntityMapper modelCallLogEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private PromptTemplateApplicationService service;

    @BeforeEach
    void setUp() {
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        generationRecordEntityMapper = mock(GenerationRecordEntityMapper.class);
        modelCallLogEntityMapper = mock(ModelCallLogEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        service = new PromptTemplateApplicationService(
                promptTemplateEntityMapper,
                promptTemplateVersionEntityMapper,
                generationRecordEntityMapper,
                modelCallLogEntityMapper,
                workspaceAccessService,
                mock(com.codeforge.ai.infrastructure.audit.AuditLogWriter.class),
                new ObjectMapper());
    }

    @Test
    void adminCreatePromptTemplate() {
        PromptTemplateCreateRequest request = new PromptTemplateCreateRequest();
        request.setWorkspaceId(1001L);
        request.setTemplateName("客户后台模板");
        request.setTemplateScene("CODE_GEN");
        request.setSystemPrompt("你是助手");
        request.setUserPrompt("请生成 {{app_name}}");
        request.setRemark("通用模板");

        given(promptTemplateEntityMapper.findByWorkspaceIdAndTemplateName(1001L, "客户后台模板"))
                .willReturn(null);
        given(promptTemplateEntityMapper.insertTemplate(any(PromptTemplateEntity.class))).willAnswer(invocation -> {
            PromptTemplateEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        });
        given(promptTemplateVersionEntityMapper.insertVersion(any(PromptTemplateVersionEntity.class))).willReturn(1);

        PromptTemplateDetailResponse response = service.createTemplate(adminUser(), request);

        assertThat(response.templateName()).isEqualTo("客户后台模板");
        assertThat(response.status()).isEqualTo("DRAFT");
        ArgumentCaptor<PromptTemplateEntity> templateCaptor = ArgumentCaptor.forClass(PromptTemplateEntity.class);
        verify(promptTemplateEntityMapper).insertTemplate(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getCreatedAt()).isNotNull();
        assertThat(templateCaptor.getValue().getUpdatedAt()).isNotNull();
        verify(workspaceAccessService).requireEditorAccess(any(), eq(1001L));
    }

    @Test
    void adminArchivePublishedTemplate() {
        PromptTemplateEntity published = PromptTemplateEntity.builder()
                .id(1L)
                .workspaceId(1001L)
                .templateName("Vue 项目生成")
                .templateScene("CODE_GEN")
                .status(PromptTemplateStatus.PUBLISHED.name())
                .currentVersionNo(1)
                .build();
        PromptTemplateEntity archived = PromptTemplateEntity.builder()
                .id(1L)
                .workspaceId(1001L)
                .templateName("Vue 项目生成")
                .templateScene("CODE_GEN")
                .status(PromptTemplateStatus.ARCHIVED.name())
                .currentVersionNo(1)
                .build();
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(published, archived);
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(1L, 1)).willReturn(versionEntity(1L, 1, null));

        PromptTemplateDetailResponse response = service.archiveTemplate(adminUser(), 1L);

        assertThat(response.status()).isEqualTo("ARCHIVED");
        verify(promptTemplateEntityMapper).updateStatus(1L, PromptTemplateStatus.ARCHIVED.name(), 2001L);
    }

    @Test
    void adminDeleteUnusedDraftTemplate() {
        PromptTemplateEntity draft = draftTemplate(2L);
        given(promptTemplateEntityMapper.selectOneById(2L)).willReturn(draft);
        given(generationRecordEntityMapper.countByTemplateId(2L)).willReturn(0);
        given(promptTemplateVersionEntityMapper.findByTemplateId(2L)).willReturn(List.of(versionEntity(2L, 1, null)));
        given(generationRecordEntityMapper.countByPromptTemplateVersionId(21L)).willReturn(0);
        given(modelCallLogEntityMapper.countByPromptTemplateVersionId(21L)).willReturn(0);

        service.deleteTemplate(adminUser(), 2L);

        verify(promptTemplateVersionEntityMapper).deleteById(21L);
        verify(promptTemplateEntityMapper).deleteById(2L);
    }

    @Test
    void adminDeleteReferencedTemplateRejected() {
        PromptTemplateEntity draft = draftTemplate(3L);
        given(promptTemplateEntityMapper.selectOneById(3L)).willReturn(draft);
        given(generationRecordEntityMapper.countByTemplateId(3L)).willReturn(2);

        assertThatThrownBy(() -> service.deleteTemplate(adminUser(), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板已被使用");
        verify(promptTemplateEntityMapper, never()).deleteById(3L);
    }

    @Test
    void adminCreatePromptTemplateVersion() {
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(draftTemplate(1L));
        given(promptTemplateVersionEntityMapper.findMaxVersionNo(1L)).willReturn(1);
        given(promptTemplateVersionEntityMapper.insertVersion(any(PromptTemplateVersionEntity.class))).willReturn(1);

        PromptTemplateVersionCreateRequest request = new PromptTemplateVersionCreateRequest();
        request.setSystemPrompt("系统");
        request.setUserPrompt("用户");
        request.setVariablesJson("{\"app_name\":{\"type\":\"string\",\"required\":true}}");

        PromptTemplateVersionResponse response = service.createTemplateVersion(adminUser(), 1L, request);

        assertThat(response.versionNo()).isEqualTo(2);
    }

    @Test
    void adminUpdateDraftVersion() {
        PromptTemplateVersionEntity draftVersion = versionEntity(1L, 2, null);
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(draftTemplate(1L));
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(1L, 2)).willReturn(draftVersion);
        given(promptTemplateVersionEntityMapper.updateDraftVersion(any())).willReturn(1);
        given(promptTemplateVersionEntityMapper.selectOneById(12L)).willReturn(PromptTemplateVersionEntity.builder()
                .id(12L)
                .templateId(1L)
                .versionNo(2)
                .systemPrompt("系统")
                .userPrompt("更新后")
                .build());

        PromptTemplateVersionUpdateRequest request = new PromptTemplateVersionUpdateRequest();
        request.setSystemPrompt("系统");
        request.setUserPrompt("更新后");

        PromptTemplateVersionResponse response = service.updateTemplateVersion(adminUser(), 1L, 2, request);

        assertThat(response.userPrompt()).isEqualTo("更新后");
    }

    @Test
    void adminUpdatePublishedVersionRejected() {
        PromptTemplateVersionEntity publishedVersion = versionEntity(1L, 1, LocalDateTime.now());
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(draftTemplate(1L));
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(1L, 1)).willReturn(publishedVersion);

        PromptTemplateVersionUpdateRequest request = new PromptTemplateVersionUpdateRequest();
        request.setSystemPrompt("系统");
        request.setUserPrompt("更新后");

        assertThatThrownBy(() -> service.updateTemplateVersion(adminUser(), 1L, 1, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已发布版本不可修改");
    }

    @Test
    void adminDeleteDraftVersion() {
        PromptTemplateVersionEntity draftVersion = versionEntity(1L, 2, null);
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(draftTemplate(1L));
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(1L, 2)).willReturn(draftVersion);
        given(generationRecordEntityMapper.countByPromptTemplateVersionId(12L)).willReturn(0);
        given(modelCallLogEntityMapper.countByPromptTemplateVersionId(12L)).willReturn(0);
        given(promptTemplateVersionEntityMapper.countByTemplateId(1L)).willReturn(2);

        service.deleteTemplateVersion(adminUser(), 1L, 2);

        verify(promptTemplateVersionEntityMapper).deleteById(12L);
    }

    @Test
    void adminDeletePublishedVersionRejected() {
        PromptTemplateVersionEntity publishedVersion = versionEntity(1L, 1, LocalDateTime.now());
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(draftTemplate(1L));
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(1L, 1)).willReturn(publishedVersion);

        assertThatThrownBy(() -> service.deleteTemplateVersion(adminUser(), 1L, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已发布版本不可修改");
    }

    private static CurrentUser adminUser() {
        return new CurrentUser(2001L, "admin", List.of("USER", "PLATFORM_ADMIN"));
    }

    private static PromptTemplateEntity draftTemplate(Long id) {
        return PromptTemplateEntity.builder()
                .id(id)
                .workspaceId(1001L)
                .templateName("Vue 项目生成")
                .templateScene("CODE_GEN")
                .status(PromptTemplateStatus.DRAFT.name())
                .currentVersionNo(1)
                .remark("备注")
                .build();
    }

    private static PromptTemplateVersionEntity versionEntity(Long templateId, int versionNo, LocalDateTime publishedAt) {
        return PromptTemplateVersionEntity.builder()
                .id(templateId * 10L + versionNo)
                .templateId(templateId)
                .versionNo(versionNo)
                .systemPrompt("系统")
                .userPrompt("用户")
                .publishedAt(publishedAt)
                .build();
    }
}
