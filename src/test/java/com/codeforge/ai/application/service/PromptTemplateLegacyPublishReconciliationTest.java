package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.prompt.PromptTemplateDetailResponse;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateStatus;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateVersionStatus;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.codeforge.ai.application.service.PromptTemplateLegacyPublishReconciliationSupport.admin;
import static com.codeforge.ai.application.service.PromptTemplateLegacyPublishReconciliationSupport.canonicalVersion;
import static com.codeforge.ai.application.service.PromptTemplateLegacyPublishReconciliationSupport.draftVersion;
import static com.codeforge.ai.application.service.PromptTemplateLegacyPublishReconciliationSupport.legacyVersion;
import static com.codeforge.ai.application.service.PromptTemplateLegacyPublishReconciliationSupport.publishedTemplate;
import static com.codeforge.ai.application.service.PromptTemplateLegacyPublishReconciliationSupport.service;
import static com.codeforge.ai.application.service.PromptTemplateLegacyPublishReconciliationSupport.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LegacyDraftWithPublishedAtRepublishPromotesStatusTest {

    private PromptTemplateApplicationService applicationService;
    private PromptTemplateVersionEntityMapper versionMapper;
    private PromptTemplateEntityMapper templateMapper;

    @BeforeEach
    void setUp() {
        versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        templateMapper = mock(PromptTemplateEntityMapper.class);
        applicationService = service(versionMapper, templateMapper, mock(AuditLogWriter.class));
    }

    @Test
    void legacyDraftWithPublishedAtRepublishPromotesStatus() {
        LocalDateTime legacyPublishedAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(1), publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1))
                .willReturn(legacyVersion(1, legacyPublishedAt, 3001L), canonicalVersion(1, legacyPublishedAt, 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        PromptTemplateDetailResponse response = applicationService.publishTemplateVersion(admin(), 4001L, 1);

        verify(versionMapper).markPublished(eq(5001L), eq(3001L), eq(legacyPublishedAt), eq(2001L));
        assertThat(response.currentVersion().versionStatus()).isEqualTo("PUBLISHED");
    }
}

class LegacyDraftWithPublishedAtRepublishUpdatesPointerTest {

    @Test
    void legacyDraftWithPublishedAtRepublishUpdatesPointer() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, mock(AuditLogWriter.class));
        LocalDateTime legacyPublishedAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(1), publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1))
                .willReturn(legacyVersion(1, legacyPublishedAt, 3001L), canonicalVersion(1, legacyPublishedAt, 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        applicationService.publishTemplateVersion(admin(), 4001L, 1);

        verify(templateMapper).updatePublishedVersion(4001L, PromptTemplateStatus.PUBLISHED.name(), 2, 2001L);
    }
}

class LegacyPublishReconciliationPreservesPublishedAtTest {

    @Test
    void preservesPublishedAt() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, mock(AuditLogWriter.class));
        LocalDateTime legacyPublishedAt = LocalDateTime.of(2026, 6, 15, 8, 30);
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(1), publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1))
                .willReturn(legacyVersion(1, legacyPublishedAt, 3001L), canonicalVersion(1, legacyPublishedAt, 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        applicationService.publishTemplateVersion(admin(), 4001L, 1);

        verify(versionMapper).markPublished(eq(5001L), any(), eq(legacyPublishedAt), any());
    }
}

class LegacyPublishReconciliationPreservesPublishedByTest {

    @Test
    void preservesPublishedBy() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, mock(AuditLogWriter.class));
        LocalDateTime legacyPublishedAt = LocalDateTime.of(2026, 6, 15, 8, 30);
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(1), publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1))
                .willReturn(legacyVersion(1, legacyPublishedAt, 3001L), canonicalVersion(1, legacyPublishedAt, 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        applicationService.publishTemplateVersion(admin(), 4001L, 1);

        verify(versionMapper).markPublished(eq(5001L), eq(3001L), eq(legacyPublishedAt), eq(2001L));
    }
}

class LegacyPublishReconciliationFillsMissingPublishedByTest {

    @Test
    void fillsMissingPublishedBy() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, mock(AuditLogWriter.class));
        LocalDateTime legacyPublishedAt = LocalDateTime.of(2026, 6, 15, 8, 30);
        PromptTemplateVersionEntity legacy = PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .status(PromptTemplateVersionStatus.DRAFT.name())
                .publishedAt(legacyPublishedAt)
                .build();
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(1), publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1))
                .willReturn(legacy, canonicalVersion(1, legacyPublishedAt, 2001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        applicationService.publishTemplateVersion(admin(), 4001L, 1);

        verify(versionMapper).markPublished(eq(5001L), eq(2001L), eq(legacyPublishedAt), eq(2001L));
    }
}

class RepublishV1DoesNotDowngradeLatestV2Test {

    @Test
    void republishV1DoesNotDowngradeLatestV2() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, auditLogWriter);
        LocalDateTime v1PublishedAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1)).willReturn(canonicalVersion(1, v1PublishedAt, 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        applicationService.publishTemplateVersion(admin(), 4001L, 1);

        verify(versionMapper, never()).markPublished(any(), any(), any(), any());
        verify(templateMapper, never()).updatePublishedVersion(any(), any(), any(), any());
        verify(auditLogWriter, never()).insert(any());
    }
}

class CanonicallyPublishedVersionIsIdempotentTest {

    @Test
    void canonicallyPublishedVersionIsIdempotent() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, auditLogWriter);
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 2)).willReturn(canonicalVersion(2, LocalDateTime.now(), 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        applicationService.publishTemplateVersion(admin(), 4001L, 2);

        verify(versionMapper, never()).markPublished(any(), any(), any(), any());
        verify(templateMapper, never()).updatePublishedVersion(any(), any(), any(), any());
        verify(auditLogWriter, never()).insert(any());
    }
}

class PublishedVersionWithWrongPointerRepairsPointerTest {

    @Test
    void publishedVersionWithWrongPointerRepairsPointer() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, auditLogWriter);
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(1), publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 2)).willReturn(canonicalVersion(2, LocalDateTime.now(), 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        applicationService.publishTemplateVersion(admin(), 4001L, 2);

        verify(versionMapper, never()).markPublished(any(), any(), any(), any());
        verify(templateMapper).updatePublishedVersion(4001L, PromptTemplateStatus.PUBLISHED.name(), 2, 2001L);
        verify(auditLogWriter, never()).insert(any());
    }
}

class DraftWithoutPublishedAtPublishesNormallyTest {

    @Test
    void draftWithoutPublishedAtPublishesNormally() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, auditLogWriter);
        given(templateMapper.selectOneById(4001L)).willReturn(template(1), publishedTemplate(1));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1))
                .willReturn(draftVersion(1), canonicalVersion(1, LocalDateTime.now(), 2001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(1);

        applicationService.publishTemplateVersion(admin(), 4001L, 1);

        verify(versionMapper).markPublished(eq(5001L), eq(2001L), any(), eq(2001L));
        verify(templateMapper).updatePublishedVersion(4001L, PromptTemplateStatus.PUBLISHED.name(), 1, 2001L);
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogWriter).insert(captor.capture());
        assertThat(captor.getValue().getActionCode()).isEqualTo("PROMPT_TEMPLATE_VERSION_PUBLISH");
    }
}

class PublishV2KeepsV1PublishedTest {

    @Test
    void publishV2KeepsV1Published() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, mock(AuditLogWriter.class));
        given(templateMapper.selectOneById(4001L)).willReturn(template(1), publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 2))
                .willReturn(draftVersion(2), canonicalVersion(2, LocalDateTime.now(), 2001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        applicationService.publishTemplateVersion(admin(), 4001L, 2);

        verify(versionMapper).markPublished(eq(5002L), any(), any(), any());
        verify(versionMapper, never()).markPublished(eq(5001L), any(), any(), any());
    }
}

class LegacyPublishReconciliationWritesAuditTest {

    @Test
    void legacyPublishReconciliationWritesAudit() throws Exception {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, auditLogWriter);
        LocalDateTime legacyPublishedAt = LocalDateTime.of(2026, 6, 15, 8, 30);
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(1), publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1))
                .willReturn(legacyVersion(1, legacyPublishedAt, 3001L), canonicalVersion(1, legacyPublishedAt, 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        applicationService.publishTemplateVersion(admin(), 4001L, 1);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogWriter).insert(captor.capture());
        assertThat(captor.getValue().getActionCode()).isEqualTo("PROMPT_TEMPLATE_VERSION_RECONCILED");
        JsonNode detail = new ObjectMapper().readTree(captor.getValue().getDetailJson());
        assertThat(detail.get("legacyStateReconciled").asBoolean()).isTrue();
        assertThat(detail.get("versionNo").asInt()).isEqualTo(1);
    }
}

class ConsistentRepublishDoesNotDuplicateAuditTest {

    @Test
    void consistentRepublishDoesNotDuplicateAudit() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, auditLogWriter);
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1)).willReturn(canonicalVersion(1, LocalDateTime.now(), 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        applicationService.publishTemplateVersion(admin(), 4001L, 1);

        verify(auditLogWriter, never()).insert(any());
    }
}

class ReconciliationAuditDoesNotContainPromptTest {

    @Test
    void reconciliationAuditDoesNotContainPrompt() throws Exception {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, auditLogWriter);
        LocalDateTime legacyPublishedAt = LocalDateTime.of(2026, 6, 15, 8, 30);
        PromptTemplateVersionEntity legacy = legacyVersion(1, legacyPublishedAt, 3001L);
        legacy.setSystemPrompt("secret system");
        legacy.setUserPrompt("secret user");
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(1), publishedTemplate(2));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1))
                .willReturn(legacy, canonicalVersion(1, legacyPublishedAt, 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);

        applicationService.publishTemplateVersion(admin(), 4001L, 1);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogWriter).insert(captor.capture());
        String detailJson = captor.getValue().getDetailJson();
        assertThat(detailJson).doesNotContain("secret system");
        assertThat(detailJson).doesNotContain("secret user");
        assertThat(detailJson).doesNotContain("systemPrompt");
        assertThat(detailJson).doesNotContain("userPrompt");
    }
}

class ReconciliationAuditIsAtomicTest {

    @Test
    void reconciliationAuditIsAtomic() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, auditLogWriter);
        LocalDateTime legacyPublishedAt = LocalDateTime.of(2026, 6, 15, 8, 30);
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(1));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1)).willReturn(legacyVersion(1, legacyPublishedAt, 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);
        doThrow(new RuntimeException("audit failed")).when(auditLogWriter).insert(any());

        assertThatThrownBy(() -> applicationService.publishTemplateVersion(admin(), 4001L, 1))
                .isInstanceOf(RuntimeException.class);
    }
}

class ReconciliationFailureRollsBackStatusPointerAndAuditTest {

    @Test
    void reconciliationFailureRollsBackStatusPointerAndAudit() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PromptTemplateApplicationService applicationService = service(versionMapper, templateMapper, auditLogWriter);
        LocalDateTime legacyPublishedAt = LocalDateTime.of(2026, 6, 15, 8, 30);
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(1));
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 1)).willReturn(legacyVersion(1, legacyPublishedAt, 3001L));
        given(versionMapper.findMaxEffectivelyPublishedVersionNo(4001L)).willReturn(2);
        doThrow(new RuntimeException("audit failed")).when(auditLogWriter).insert(any());

        assertThatThrownBy(() -> applicationService.publishTemplateVersion(admin(), 4001L, 1))
                .isInstanceOf(RuntimeException.class);
    }
}

class UserCanReadLegacyEffectivePublishedVersionTest {

    @Test
    void userCanReadLegacyEffectivePublishedVersion() {
        PromptTemplateVersionEntity legacy = PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .status(PromptTemplateVersionStatus.DRAFT.name())
                .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build();

        assertThat(legacy.isEffectivelyPublished()).isTrue();
        assertThat(legacy.isCanonicallyPublished()).isFalse();
    }
}

class UserCannotReadRealDraftVersionTest {

    @Test
    void userCannotReadRealDraftVersion() {
        PromptTemplateVersionEntity draft = PromptTemplateVersionEntity.builder()
                .id(5002L)
                .templateId(4001L)
                .versionNo(2)
                .status(PromptTemplateVersionStatus.DRAFT.name())
                .build();

        assertThat(draft.isEffectivelyPublished()).isFalse();
    }

    @Test
    void requirePublishedVersionForUserRejectsRealDraft() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        WorkspaceAccessService workspaceAccessService = mock(WorkspaceAccessService.class);
        PromptTemplateApplicationService applicationService = new PromptTemplateApplicationService(
                templateMapper,
                versionMapper,
                mock(GenerationRecordEntityMapper.class),
                mock(ModelCallLogEntityMapper.class),
                workspaceAccessService,
                mock(AuditLogWriter.class),
                new ObjectMapper());
        given(templateMapper.selectOneById(4001L)).willReturn(publishedTemplate(2));
        given(versionMapper.selectOneById(5002L)).willReturn(draftVersion(2));

        assertThatThrownBy(() -> applicationService.requirePublishedVersionForUser(admin(), 4001L, 5002L))
                .isInstanceOf(BusinessException.class);
    }
}
