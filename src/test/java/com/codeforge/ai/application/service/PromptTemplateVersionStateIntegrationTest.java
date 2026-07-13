package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateVersionStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "codeforge.build.previewdir=target/test-prompt-version-state")
@ActiveProfiles("test")
class PromptTemplateVersionStateIntegrationTest {

    @Autowired
    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    @Autowired
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;

    private Long templateId;
    private Long publishedVersionId;

    @BeforeEach
    void seedTemplateVersions() {
        LocalDateTime now = LocalDateTime.now();
        PromptTemplateEntity template = PromptTemplateEntity.builder()
                .workspaceId(1L)
                .templateName("Version State Probe " + System.nanoTime())
                .templateScene("CODE_GEN")
                .status("PUBLISHED")
                .currentVersionNo(2)
                .build();
        template.setCreatedBy(1L);
        template.setUpdatedBy(1L);
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        template.setIsDeleted(0);
        promptTemplateEntityMapper.insertTemplate(template);
        Long seededTemplateId = template.getId();

        PromptTemplateVersionEntity v1 = version(seededTemplateId, 1, PromptTemplateVersionStatus.PUBLISHED.name(), now);
        promptTemplateVersionEntityMapper.insertVersion(v1);
        publishedVersionId = v1.getId();
        templateId = seededTemplateId;

        PromptTemplateVersionEntity v2 = version(seededTemplateId, 2, PromptTemplateVersionStatus.DRAFT.name(), null);
        promptTemplateVersionEntityMapper.insertVersion(v2);
    }

    @Test
    void publishedVersionCannotBeEdited() {
        PromptTemplateVersionEntity published = promptTemplateVersionEntityMapper.selectOneById(publishedVersionId);
        published.setSystemPrompt("mutated");
        int updated = promptTemplateVersionEntityMapper.updateDraftVersion(published);
        assertThat(updated).isZero();
    }

    @Test
    void schemaSupportsVersionStatusColumn() {
        PromptTemplateVersionEntity version = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(templateId, 1);
        assertThat(version.getStatus()).isEqualTo(PromptTemplateVersionStatus.PUBLISHED.name());
    }

    private PromptTemplateVersionEntity version(Long seededTemplateId, int versionNo, String status, LocalDateTime publishedAt) {
        LocalDateTime now = LocalDateTime.now();
        PromptTemplateVersionEntity entity = PromptTemplateVersionEntity.builder()
                .templateId(seededTemplateId)
                .versionNo(versionNo)
                .systemPrompt("system-" + versionNo)
                .userPrompt("user-" + versionNo)
                .status(status)
                .publishedAt(publishedAt)
                .publishedBy(publishedAt == null ? null : 1L)
                .build();
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        return entity;
    }
}
