package com.codeforge.ai.infrastructure.persistence;

import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "codeforge.build.previewdir=target/test-model-call-log-previews")
@ActiveProfiles("test")
class ModelCallLogPersistenceTest {

    private static final Long SEEDED_TASK_ID = 1L;
    private static final Long SEEDED_APP_ID = 1L;
    private static final Long SEEDED_PROVIDER_ID = 1L;

    @Autowired
    private ModelCallLogEntityMapper modelCallLogEntityMapper;

    @Test
    void shouldInsertAndFindDeepSeekSuccessLog() {
        ModelCallLogEntity entity = ModelCallLogEntity.builder()
                .taskId(SEEDED_TASK_ID)
                .appId(SEEDED_APP_ID)
                .providerId(SEEDED_PROVIDER_ID)
                .providerCode("deepseek")
                .modelName("deepseek-chat")
                .apiProtocol("OPENAI_COMPATIBLE")
                .status("SUCCESS")
                .inputTokens(903)
                .outputTokens(3423)
                .durationMs(21743L)
                .fallbackUsed(false)
                .generationSource("AI_DIRECT")
                .promptTemplateVersionId(null)
                .promptTemplateCode(null)
                .promptTemplateVersionNo(null)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .build();

        int inserted = modelCallLogEntityMapper.insertCallLog(entity);
        assertThat(inserted).isEqualTo(1);
        assertThat(entity.getId()).isNotNull();

        List<ModelCallLogEntity> logs = modelCallLogEntityMapper.findByTaskId(SEEDED_TASK_ID);
        assertThat(logs).isNotEmpty();

        ModelCallLogEntity latestLog = logs.getFirst();
        assertThat(latestLog.getStatus()).isEqualTo("SUCCESS");
        assertThat(latestLog.getProviderCode()).isEqualTo("deepseek");
        assertThat(latestLog.getModelName()).isEqualTo("deepseek-chat");
        assertThat(latestLog.getFallbackUsed()).isFalse();
        assertThat(latestLog.getPromptTemplateVersionId()).isNull();
        assertThat(latestLog.getInputTokens()).isNotNull().isPositive();
        assertThat(latestLog.getOutputTokens()).isNotNull().isPositive();
        assertThat(latestLog.getDurationMs()).isNotNull().isPositive();
    }
}
