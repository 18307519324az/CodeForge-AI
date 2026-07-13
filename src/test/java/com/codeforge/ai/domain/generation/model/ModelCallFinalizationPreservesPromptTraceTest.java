package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCallFinalizationPreservesPromptTraceTest {

    @Test
    void finalizeMapperMustNotExposePromptTraceUpdates() {
        Method[] methods = ModelCallLogEntityMapper.class.getDeclaredMethods();
        boolean hasTraceMutatingUpdate = Arrays.stream(methods)
                .filter(method -> method.getName().startsWith("update"))
                .map(Method::toString)
                .anyMatch(signature -> signature.contains("promptTemplateVersionId")
                        || signature.contains("systemPromptSha256")
                        || signature.contains("combinedPromptFingerprint"));
        assertThat(hasTraceMutatingUpdate).isFalse();
    }

    @Test
    void insertedTraceFieldsRemainOnEntityAfterApply() {
        ModelCallLogEntity entity = ModelCallLogEntity.builder()
                .taskId(1L)
                .status("SUCCESS")
                .build();
        entity.setPromptTemplateVersionId(5001L);
        entity.setPromptTemplateCode("code");
        entity.setPromptTemplateVersionNo(1);
        entity.setSystemPromptSha256("sys");
        entity.setUserPromptSha256("user");
        entity.setCombinedPromptFingerprint("combined");

        entity.setStatus("FAILED");
        entity.setDurationMs(99L);
        entity.setErrorMessage("provider failed");

        assertThat(entity.getPromptTemplateVersionId()).isEqualTo(5001L);
        assertThat(entity.getPromptTemplateCode()).isEqualTo("code");
        assertThat(entity.getPromptTemplateVersionNo()).isEqualTo(1);
        assertThat(entity.getSystemPromptSha256()).isEqualTo("sys");
        assertThat(entity.getUserPromptSha256()).isEqualTo("user");
        assertThat(entity.getCombinedPromptFingerprint()).isEqualTo("combined");
    }
}
