package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
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
class LocalProfileModelCallLogMapsAllFieldsTest {

    private static final Long PROBE_TASK_ID = 1L;
    private static final Long PROBE_APP_ID = 1L;
    private static final Long PROBE_PROVIDER_ID = 1L;

    @Autowired
    private ModelCallLogEntityMapper modelCallLogEntityMapper;

    @BeforeEach
    void seedProbeLog() {
        ModelCallLogEntity entity = ModelCallLogEntity.builder()
                .taskId(PROBE_TASK_ID)
                .appId(PROBE_APP_ID)
                .providerId(PROBE_PROVIDER_ID)
                .providerCode("deepseek")
                .modelName("deepseek-v4-pro")
                .apiProtocol("OPENAI_COMPATIBLE")
                .status("SUCCESS")
                .inputTokens(120)
                .outputTokens(340)
                .durationMs(1500L)
                .fallbackUsed(false)
                .generationSource("AI_DIRECT_INITIAL")
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .build();
        modelCallLogEntityMapper.insertCallLog(entity);
    }

    @Test
    void findPageShouldMapAllSnakeCaseColumnsWithoutGlobalCamelCaseConfig() {
        List<ModelCallLogEntity> logs = modelCallLogEntityMapper.findPage(0L, 20L, null);
        assertThat(logs).isNotEmpty();

        ModelCallLogEntity latest = logs.getFirst();
        assertThat(latest.getId()).isNotNull();
        assertThat(latest.getTaskId()).isEqualTo(PROBE_TASK_ID);
        assertThat(latest.getAppId()).isEqualTo(PROBE_APP_ID);
        assertThat(latest.getProviderCode()).isEqualTo("deepseek");
        assertThat(latest.getModelName()).isEqualTo("deepseek-v4-pro");
        assertThat(latest.getGenerationSource()).isEqualTo("AI_DIRECT_INITIAL");
        assertThat(latest.getDurationMs()).isPositive();
        assertThat(latest.getInputTokens()).isPositive();
        assertThat(latest.getCreatedAt()).isNotNull();
    }

    @Test
    void findByTaskIdShouldMapAllSnakeCaseColumnsWithoutGlobalCamelCaseConfig() {
        List<ModelCallLogEntity> logs = modelCallLogEntityMapper.findByTaskId(PROBE_TASK_ID);
        assertThat(logs).isNotEmpty();

        ModelCallLogEntity latest = logs.getFirst();
        assertThat(latest.getTaskId()).isEqualTo(PROBE_TASK_ID);
        assertThat(latest.getProviderCode()).isNotNull();
        assertThat(latest.getModelName()).isNotNull();
        assertThat(latest.getGenerationSource()).isNotNull();
        assertThat(latest.getDurationMs()).isNotNull().isPositive();
    }
}
