package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.metrics.entity.MetricDailyAggEntity;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
class CrossProfileMappingContractTest {

    @Autowired
    private ModelCallLogEntityMapper modelCallLogEntityMapper;

    @Autowired
    private PromptTemplateEntityMapper promptTemplateEntityMapper;

    @Autowired
    private MetricDailyAggEntityMapper metricDailyAggEntityMapper;

    @Test
    void modelCallLogShouldMapFieldsWhenCamelCaseDisabled() {
        ModelCallLogEntity entity = ModelCallLogEntity.builder()
                .taskId(1L)
                .appId(1L)
                .providerId(1L)
                .providerCode("deepseek")
                .modelName("deepseek-v4-pro")
                .status("SUCCESS")
                .inputTokens(10)
                .outputTokens(20)
                .durationMs(100L)
                .fallbackUsed(false)
                .generationSource("AI_DIRECT_INITIAL")
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .build();
        modelCallLogEntityMapper.insertCallLog(entity);

        ModelCallLogEntity mapped = modelCallLogEntityMapper.findPage(0L, 1L, null).getFirst();
        assertThat(mapped.getTaskId()).isEqualTo(1L);
        assertThat(mapped.getModelName()).isEqualTo("deepseek-v4-pro");
        assertThat(mapped.getGenerationSource()).isEqualTo("AI_DIRECT_INITIAL");
        assertThat(mapped.getDurationMs()).isEqualTo(100L);
    }

    @Test
    void promptTemplateShouldMapNameWhenCamelCaseDisabled() {
        LocalDateTime now = LocalDateTime.now();
        PromptTemplateEntity entity = PromptTemplateEntity.builder()
                .workspaceId(1L)
                .templateName("Cross Profile Template")
                .templateScene("CODE_GEN")
                .status("DRAFT")
                .currentVersionNo(1)
                .build();
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        promptTemplateEntityMapper.insertTemplate(entity);

        PromptTemplateEntity mapped = promptTemplateEntityMapper.findAccessibleTemplates(
                List.of(1L), "Cross Profile Template", null, null).getFirst();
        assertThat(mapped.getTemplateName()).isEqualTo("Cross Profile Template");
        assertThat(mapped.getUpdatedAt()).isNotNull();
    }

    @Test
    void metricSummaryShouldMapMetricKeyWhenCamelCaseDisabled() {
        LocalDate statDate = LocalDate.of(2099, 12, 30);
        metricDailyAggEntityMapper.upsertMetricValue(statDate, "requestCount", BigDecimal.valueOf(7));
        metricDailyAggEntityMapper.upsertMetricValue(statDate, "successCount", BigDecimal.valueOf(6));
        metricDailyAggEntityMapper.upsertMetricValue(statDate, "failedCount", BigDecimal.valueOf(1));

        List<MetricDailyAggEntity> metrics = metricDailyAggEntityMapper.findByStatDateForUpdate(statDate);
        assertThat(metrics).isNotEmpty();
        assertThat(metrics.stream()
                .anyMatch(item -> "requestCount".equals(item.getMetricKey()) && item.getMetricValue().intValue() == 7))
                .isTrue();
        assertThat(metrics.stream()
                .anyMatch(item -> "successCount".equals(item.getMetricKey()) && item.getMetricValue().intValue() == 6))
                .isTrue();
        assertThat(metrics.stream()
                .anyMatch(item -> "failedCount".equals(item.getMetricKey()) && item.getMetricValue().intValue() == 1))
                .isTrue();
        assertThat(metrics.stream().map(MetricDailyAggEntity::getStatDate).anyMatch(statDate::equals)).isTrue();
    }
}

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@ActiveProfiles("test")
class CrossProfileMappingContractWithCamelCaseEnabledTest {

    @Autowired
    private ModelCallLogEntityMapper modelCallLogEntityMapper;

    @Test
    void modelCallLogShouldMapFieldsWhenCamelCaseEnabled() {
        ModelCallLogEntity entity = ModelCallLogEntity.builder()
                .taskId(1L)
                .appId(1L)
                .providerId(1L)
                .providerCode("deepseek")
                .modelName("deepseek-v4-pro")
                .status("SUCCESS")
                .inputTokens(10)
                .outputTokens(20)
                .durationMs(100L)
                .fallbackUsed(false)
                .generationSource("AI_DIRECT_INITIAL")
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .build();
        modelCallLogEntityMapper.insertCallLog(entity);

        ModelCallLogEntity mapped = modelCallLogEntityMapper.findPage(0L, 1L, null).getFirst();
        assertThat(mapped.getTaskId()).isEqualTo(1L);
        assertThat(mapped.getModelName()).isEqualTo("deepseek-v4-pro");
        assertThat(mapped.getGenerationSource()).isEqualTo("AI_DIRECT_INITIAL");
        assertThat(mapped.getDurationMs()).isEqualTo(100L);
    }
}
