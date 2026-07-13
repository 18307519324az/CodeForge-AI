package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateStatus;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateVersionStatus;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.mockito.Mockito;

final class PromptTemplateLegacyPublishReconciliationSupport {

    private PromptTemplateLegacyPublishReconciliationSupport() {
    }

    static PromptTemplateApplicationService service(
            PromptTemplateVersionEntityMapper versionMapper,
            PromptTemplateEntityMapper templateMapper,
            AuditLogWriter auditLogWriter) {
        return new PromptTemplateApplicationService(
                templateMapper,
                versionMapper,
                Mockito.mock(GenerationRecordEntityMapper.class),
                Mockito.mock(ModelCallLogEntityMapper.class),
                Mockito.mock(WorkspaceAccessService.class),
                auditLogWriter,
                new ObjectMapper());
    }

    static CurrentUser admin() {
        return new CurrentUser(2001L, "admin", List.of("PLATFORM_ADMIN"));
    }

    static PromptTemplateEntity template(int currentVersionNo) {
        return PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status(PromptTemplateStatus.DRAFT.name())
                .currentVersionNo(currentVersionNo)
                .build();
    }

    static PromptTemplateEntity publishedTemplate(int currentVersionNo) {
        return PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status(PromptTemplateStatus.PUBLISHED.name())
                .currentVersionNo(currentVersionNo)
                .build();
    }

    static PromptTemplateVersionEntity legacyVersion(int versionNo, LocalDateTime publishedAt, Long publishedBy) {
        return PromptTemplateVersionEntity.builder()
                .id(versionNo == 1 ? 5001L : 5002L)
                .templateId(4001L)
                .versionNo(versionNo)
                .status(PromptTemplateVersionStatus.DRAFT.name())
                .publishedAt(publishedAt)
                .publishedBy(publishedBy)
                .build();
    }

    static PromptTemplateVersionEntity canonicalVersion(int versionNo, LocalDateTime publishedAt, Long publishedBy) {
        return PromptTemplateVersionEntity.builder()
                .id(versionNo == 1 ? 5001L : 5002L)
                .templateId(4001L)
                .versionNo(versionNo)
                .status(PromptTemplateVersionStatus.PUBLISHED.name())
                .publishedAt(publishedAt)
                .publishedBy(publishedBy)
                .build();
    }

    static PromptTemplateVersionEntity draftVersion(int versionNo) {
        return PromptTemplateVersionEntity.builder()
                .id(versionNo == 1 ? 5001L : 5002L)
                .templateId(4001L)
                .versionNo(versionNo)
                .status(PromptTemplateVersionStatus.DRAFT.name())
                .build();
    }
}
