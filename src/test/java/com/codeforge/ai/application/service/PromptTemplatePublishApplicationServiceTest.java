package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.prompt.PromptTemplateDetailResponse;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateVersionStatus;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PublishChangesVersionStatusToPublishedTest {

    private PromptTemplateApplicationService service;
    private PromptTemplateVersionEntityMapper versionMapper;
    private PromptTemplateEntityMapper templateMapper;
    private AuditLogWriter auditLogWriter;

    @BeforeEach
    void setUp() {
        versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        templateMapper = mock(PromptTemplateEntityMapper.class);
        auditLogWriter = mock(AuditLogWriter.class);
        service = new PromptTemplateApplicationService(
                templateMapper,
                versionMapper,
                mock(GenerationRecordEntityMapper.class),
                mock(ModelCallLogEntityMapper.class),
                mock(WorkspaceAccessService.class),
                auditLogWriter,
                new ObjectMapper());
    }

    @Test
    void publishChangesVersionStatusToPublished() {
        stubDraftPublishFlow(2);
        PromptTemplateDetailResponse response = service.publishTemplateVersion(admin(), 4001L, 2);
        verify(versionMapper).markPublished(eq(5002L), eq(2001L), any(), eq(2001L));
        verify(templateMapper).updatePublishedVersion(4001L, "PUBLISHED", 2, 2001L);
        assertThat(response.currentVersion().versionStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void publishUpdatesLatestPublishedVersion() {
        stubDraftPublishFlow(2);
        service.publishTemplateVersion(admin(), 4001L, 2);
        verify(templateMapper).updatePublishedVersion(4001L, "PUBLISHED", 2, 2001L);
    }

    @Test
    void publishV2KeepsV1Published() {
        stubDraftPublishFlow(2);
        service.publishTemplateVersion(admin(), 4001L, 2);
        verify(versionMapper).markPublished(eq(5002L), any(), any(), any());
        verify(versionMapper, never()).markPublished(eq(5001L), any(), any(), any());
    }

    @Test
    void repeatedPublishIsIdempotent() {
        PromptTemplateVersionEntity published = PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .status(PromptTemplateVersionStatus.PUBLISHED.name())
                .publishedAt(LocalDateTime.now())
                .build();
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(1));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1)).willReturn(published);
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(1);

        service.publishTemplateVersion(admin(), 4001L, 1);

        verify(versionMapper, never()).markPublished(any(), any(), any(), any());
        verify(templateMapper, never()).updatePublishedVersion(any(), any(), any(), any());
        verify(auditLogWriter, never()).insert(any());
    }

    @Test
    void crossTemplateVersionPublishIsRejected() {
        given(templateMapper.selectOneById(4001L)).willReturn(template(1));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 9)).willReturn(null);

        assertThatThrownBy(() -> service.publishTemplateVersion(admin(), 4001L, 9))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void publishAuditIsAtomic() {
        stubDraftPublishFlow(1);
        service.publishTemplateVersion(admin(), 4001L, 1);
        ArgumentCaptor<com.codeforge.ai.domain.audit.entity.AuditLogEntity> captor =
                ArgumentCaptor.forClass(com.codeforge.ai.domain.audit.entity.AuditLogEntity.class);
        verify(auditLogWriter).insert(captor.capture());
        assertThat(captor.getValue().getActionCode()).isEqualTo("PROMPT_TEMPLATE_VERSION_PUBLISH");
    }

    @Test
    void publishFailureRollsBackTemplateAndVersion() {
        given(templateMapper.selectOneById(4001L)).willReturn(template(1));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1)).willReturn(draftVersion(1, 5001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(1);
        doThrow(new RuntimeException("audit failed")).when(auditLogWriter).insert(any());

        assertThatThrownBy(() -> service.publishTemplateVersion(admin(), 4001L, 1))
                .isInstanceOf(RuntimeException.class);
    }

    private PromptTemplateEntity publishedTemplate(int currentVersionNo) {
        return PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status("PUBLISHED")
                .currentVersionNo(currentVersionNo)
                .build();
    }

    private void stubDraftPublishFlow(int versionNo) {
        long versionId = versionNo == 1 ? 5001L : 5002L;
        PromptTemplateVersionEntity draft = draftVersion(versionNo, versionId);
        PromptTemplateVersionEntity published = PromptTemplateVersionEntity.builder()
                .id(versionId)
                .templateId(4001L)
                .versionNo(versionNo)
                .status(PromptTemplateVersionStatus.PUBLISHED.name())
                .publishedAt(LocalDateTime.now())
                .build();
        given(templateMapper.selectOneById(4001L)).willReturn(template(versionNo), publishedTemplate(versionNo));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, versionNo)).willReturn(draft, published);
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(versionNo);
    }

    private PromptTemplateEntity template(int currentVersionNo) {
        return PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status("DRAFT")
                .currentVersionNo(currentVersionNo)
                .build();
    }

    private PromptTemplateVersionEntity draftVersion(int versionNo, long id) {
        return PromptTemplateVersionEntity.builder()
                .id(id)
                .templateId(4001L)
                .versionNo(versionNo)
                .status(PromptTemplateVersionStatus.DRAFT.name())
                .build();
    }

    private CurrentUser admin() {
        return new CurrentUser(2001L, "admin", List.of("PLATFORM_ADMIN"));
    }
}
