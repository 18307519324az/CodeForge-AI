package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "mybatis-flex.configuration.map-underscore-to-camel-case=false"
})
class LocalProfilePromptTemplateMapsNameAndUpdatedAtTest {

    private static final Long PROBE_WORKSPACE_ID = 1L;
    private static final String PROBE_TEMPLATE_NAME = "Mapper Probe Template";

    @Autowired
    private PromptTemplateEntityMapper promptTemplateEntityMapper;

    @BeforeEach
    void seedProbeTemplate() {
        LocalDateTime now = LocalDateTime.now();
        PromptTemplateEntity entity = PromptTemplateEntity.builder()
                .workspaceId(PROBE_WORKSPACE_ID)
                .templateName(PROBE_TEMPLATE_NAME)
                .templateScene("CODE_GEN")
                .status("DRAFT")
                .currentVersionNo(1)
                .remark("probe")
                .build();
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        promptTemplateEntityMapper.insertTemplate(entity);
    }

    @Test
    void findAccessibleTemplatesShouldMapTemplateNameAndUpdatedAtWithoutGlobalCamelCaseConfig() {
        List<PromptTemplateEntity> templates = promptTemplateEntityMapper.findAccessibleTemplates(
                List.of(PROBE_WORKSPACE_ID), PROBE_TEMPLATE_NAME, null, null);

        assertThat(templates).isNotEmpty();
        PromptTemplateEntity template = templates.stream()
                .filter(item -> PROBE_TEMPLATE_NAME.equals(item.getTemplateName()))
                .findFirst()
                .orElseThrow();

        assertThat(template.getId()).isNotNull();
        assertThat(template.getTemplateName()).isEqualTo(PROBE_TEMPLATE_NAME);
        assertThat(template.getTemplateScene()).isEqualTo("CODE_GEN");
        assertThat(template.getWorkspaceId()).isEqualTo(PROBE_WORKSPACE_ID);
        assertThat(template.getCurrentVersionNo()).isEqualTo(1);
        assertThat(template.getUpdatedAt()).isNotNull();
    }
}
