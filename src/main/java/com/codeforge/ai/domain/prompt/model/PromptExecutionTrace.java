package com.codeforge.ai.domain.prompt.model;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.application.generation.AiCodegenPromptBuilder;
import java.util.List;

/**
 * Immutable prompt trace captured from the exact payload sent (or about to be sent) to a provider.
 */
public record PromptExecutionTrace(
        Long promptTemplateVersionId,
        String promptTemplateCode,
        Integer promptTemplateVersionNo,
        String systemPromptSha256,
        String userPromptSha256,
        String combinedPromptFingerprint
) {

    public static PromptExecutionTrace empty() {
        return new PromptExecutionTrace(null, null, null, null, null, null);
    }

    public static PromptExecutionTrace fromProviderPayload(List<ModelMessage> messages,
                                                           GenerationContext context,
                                                           PromptTemplateTraceResolver traceResolver) {
        PromptFingerprintHasher.PromptFingerprint fingerprint = fingerprintFromProviderPayload(messages, context);
        PromptTemplateTrace templateTrace = resolveTemplateTrace(context, traceResolver);
        return new PromptExecutionTrace(
                templateTrace.promptTemplateVersionId(),
                templateTrace.promptTemplateCode(),
                templateTrace.promptTemplateVersionNo(),
                fingerprint.systemSha256(),
                fingerprint.userSha256(),
                fingerprint.combined());
    }

    public static PromptExecutionTrace noTemplateFromProviderPayload(List<ModelMessage> messages,
                                                                     GenerationContext context) {
        PromptFingerprintHasher.PromptFingerprint fingerprint = fingerprintFromProviderPayload(messages, context);
        return new PromptExecutionTrace(null, null, null,
                fingerprint.systemSha256(), fingerprint.userSha256(), fingerprint.combined());
    }

    public ModelCallLogEntity applyTo(ModelCallLogEntity entity) {
        entity.setPromptTemplateVersionId(promptTemplateVersionId);
        entity.setPromptTemplateCode(promptTemplateCode);
        entity.setPromptTemplateVersionNo(promptTemplateVersionNo);
        entity.setSystemPromptSha256(systemPromptSha256);
        entity.setUserPromptSha256(userPromptSha256);
        entity.setCombinedPromptFingerprint(combinedPromptFingerprint);
        return entity;
    }

    private static PromptFingerprintHasher.PromptFingerprint fingerprintFromProviderPayload(
            List<ModelMessage> messages,
            GenerationContext context) {
        if (messages != null && !messages.isEmpty()) {
            return PromptFingerprintHasher.fromMessages(messages);
        }
        if (context == null) {
            return PromptFingerprintHasher.hash("", "");
        }
        String userPrompt = context.renderedUserPrompt();
        if (userPrompt == null || userPrompt.isBlank()) {
            userPrompt = AiCodegenPromptBuilder.resolveUserMessage(context);
        }
        return PromptFingerprintHasher.hash(context.systemPrompt(), userPrompt);
    }

    private static PromptTemplateTrace resolveTemplateTrace(GenerationContext context,
                                                            PromptTemplateTraceResolver traceResolver) {
        if (context != null && context.promptTemplateVersionId() != null) {
            return new PromptTemplateTrace(
                    context.promptTemplateVersionId(),
                    context.promptTemplateCode(),
                    context.promptTemplateVersionNo());
        }
        if (context == null || traceResolver == null || context.taskId() == null) {
            return PromptTemplateTrace.empty();
        }
        return traceResolver.resolveByTaskId(context.taskId());
    }
}
