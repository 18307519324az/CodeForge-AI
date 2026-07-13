package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.AiCodegenPromptBuilder;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.prompt.model.PromptExecutionTrace;
import com.codeforge.ai.domain.prompt.model.PromptFingerprintHasher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptRuntimeBindingIntegrationTest {

    @Test
    void pinnedTemplateFingerprintMatchesRenderedPrompt() {
        GenerationContext context = RuntimeBindingFixtures.templateContext();
        var messages = AiCodegenPromptBuilder.buildInitialMessages(context.systemPrompt(), context);
        var trace = PromptExecutionTrace.fromProviderPayload(messages, context, null);

        assertThat(trace.promptTemplateVersionId()).isEqualTo(5001L);
        assertThat(trace.promptTemplateVersionNo()).isEqualTo(1);
        assertThat(trace.combinedPromptFingerprint()).isEqualTo(
                PromptFingerprintHasher.hash(context.systemPrompt(), context.renderedUserPrompt()).combined());
    }

    @Test
    void outgoingMessagesDoNotContainV2MarkersForPinnedV1() {
        GenerationContext context = RuntimeBindingFixtures.templateContext();
        String combined = AiCodegenPromptBuilder.buildInitialMessages(context.systemPrompt(), context).stream()
                .map(ModelMessage::content)
                .reduce("", String::concat);
        assertThat(combined).doesNotContain("CF_RUNTIME_TEMPLATE_SYSTEM_V2");
        assertThat(combined).doesNotContain("CF_RUNTIME_TEMPLATE_USER_V2_");
    }
}
