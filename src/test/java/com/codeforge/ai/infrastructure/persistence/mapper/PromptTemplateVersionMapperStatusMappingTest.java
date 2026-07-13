package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateVersionStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "codeforge.build.previewdir=target/test-prompt-version-mapper-status")
@ActiveProfiles("test")
class PromptTemplateVersionMapperStatusMappingTest {

    @Autowired
    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    @Autowired
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;

    private Long templateId;

    @BeforeEach
    void seedPublishedVersion() {
        LocalDateTime now = LocalDateTime.now();
        PromptTemplateEntity template = PromptTemplateEntity.builder()
                .workspaceId(1L)
                .templateName("Mapper Status Probe " + System.nanoTime())
                .templateScene("CODE_GEN")
                .status("PUBLISHED")
                .currentVersionNo(1)
                .build();
        template.setCreatedBy(1L);
        template.setUpdatedBy(1L);
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        template.setIsDeleted(0);
        promptTemplateEntityMapper.insertTemplate(template);
        templateId = template.getId();

        PromptTemplateVersionEntity version = PromptTemplateVersionEntity.builder()
                .templateId(templateId)
                .versionNo(1)
                .systemPrompt("system")
                .userPrompt("user")
                .status(PromptTemplateVersionStatus.PUBLISHED.name())
                .publishedAt(now)
                .publishedBy(1L)
                .build();
        version.setCreatedBy(1L);
        version.setUpdatedBy(1L);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        version.setIsDeleted(0);
        promptTemplateVersionEntityMapper.insertVersion(version);
        promptTemplateVersionEntityMapper.markPublished(version.getId(), 1L, now, 1L);
    }

    @Test
    void mapperMapsPublishedStatus() {
        PromptTemplateVersionEntity loaded = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(templateId, 1);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(PromptTemplateVersionStatus.PUBLISHED.name());
        assertThat(loaded.getPublishedAt()).isNotNull();
        assertThat(loaded.getPublishedBy()).isEqualTo(1L);
        assertThat(loaded.getVersionNo()).isEqualTo(1);
        assertThat(loaded.getTemplateId()).isEqualTo(templateId);
        assertThat(loaded.isCanonicallyPublished()).isTrue();
    }

    @Test
    void findMaxEffectivelyPublishedVersionNoReturnsHighestPublishedVersion() {
        LocalDateTime now = LocalDateTime.now();
        PromptTemplateVersionEntity v2 = PromptTemplateVersionEntity.builder()
                .templateId(templateId)
                .versionNo(2)
                .systemPrompt("system-2")
                .userPrompt("user-2")
                .status(PromptTemplateVersionStatus.DRAFT.name())
                .build();
        v2.setCreatedBy(1L);
        v2.setUpdatedBy(1L);
        v2.setCreatedAt(now);
        v2.setUpdatedAt(now);
        v2.setIsDeleted(0);
        promptTemplateVersionEntityMapper.insertVersion(v2);
        promptTemplateVersionEntityMapper.markPublished(v2.getId(), 1L, now, 1L);

        Integer maxVersion = promptTemplateVersionEntityMapper.findMaxEffectivelyPublishedVersionNo(templateId);

        assertThat(maxVersion).isEqualTo(2);
    }
}
