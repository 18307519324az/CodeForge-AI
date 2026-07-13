package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.ModelCallPhase;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelGatewayInvoker;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.generation.validation.ArtifactValidationResult;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser.AiOutputParseException;
import com.codeforge.ai.domain.generation.progress.ModelGenerationProgressListener;
import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CodeGenerationAiService {
    private static final Logger log = LoggerFactory.getLogger(CodeGenerationAiService.class);
    private static final int MAX_ATTEMPTS = 2;

    private final ModelGatewayInvoker invoker;
    private final AiGeneratedProjectParser parser;
    private final GeneratedArtifactValidator artifactValidator;
    private final PromptResourceLoader promptLoader;

    @Value("${codeforge.ai.max-tokens:8192}")
    private int configuredMaxTokens;

    public GeneratedProject generate(GenerationContext context) {
        return generate(context, ModelGenerationProgressListener.NOOP);
    }

    public GeneratedProject generate(GenerationContext context, ModelGenerationProgressListener progressListener) {
        String systemPrompt = context.usesTemplatePrompt()
                ? context.systemPrompt()
                : promptLoader.load(AiCodegenPromptBuilder.resolvePromptFile(context.codeGenType()));
        List<ModelMessage> initialMessages = AiCodegenPromptBuilder.buildInitialMessages(systemPrompt, context);
        ModelChatResult firstResult = invokeAi(initialMessages, context, progressListener, 1, ModelCallPhase.INITIAL);

        if (isTruncated(firstResult)) {
            log.warn("AI output truncated on attempt #1 for task {}, finishReason={}, outputTokens={}",
                    context.taskId(), firstResult.finishReason(), firstResult.completionTokens());
            return completeAfterCompactRetry(systemPrompt, context, firstResult, progressListener);
        }

        try {
            return parseValidateAndRepair(firstResult, systemPrompt, context, true, progressListener, 1);
        } catch (AiOutputParseException parseException) {
            log.warn("AI output parse failed on attempt #1 for task {}, will retry once: {}",
                    context.taskId(), parseException.getMessage());
            List<ModelMessage> retryMessages = AiCodegenPromptBuilder.buildRetryMessages(systemPrompt, context);
            ModelChatResult retryResult = invokeAi(retryMessages, context, progressListener, 2, ModelCallPhase.PARSE_RETRY);
            if (isTruncated(retryResult)) {
                throw AiGenerationFailureException.truncated(buildTruncationMetadata(retryResult, context, 2));
            }
            try {
                return parseValidateAndRepair(retryResult, systemPrompt, context, true, progressListener, 2);
            } catch (AiOutputParseException retryParseException) {
                throw AiGenerationFailureException.invalidJson(
                        retryParseException.getMessage(),
                        buildAttemptMetadata(retryResult, context, 2));
            }
        }
    }

    private ModelChatResult invokeAi(List<ModelMessage> messages,
                                     GenerationContext context,
                                     ModelGenerationProgressListener progressListener,
                                     int attempt,
                                     ModelCallPhase phase) {
        return invoker.streamWithAiProvidersOnly(messages, context, progressListener, attempt, phase);
    }

    private GeneratedProject completeAfterCompactRetry(String systemPrompt,
                                                       GenerationContext context,
                                                       ModelChatResult truncatedResult,
                                                       ModelGenerationProgressListener progressListener) {
        List<ModelMessage> compactMessages = AiCodegenPromptBuilder.buildCompactMessages(systemPrompt, context);
        ModelChatResult compactResult = invokeAi(compactMessages, context, progressListener, 2, ModelCallPhase.COMPACT_RETRY);
        if (isTruncated(compactResult)) {
            Map<String, Object> metadata = buildTruncationMetadata(compactResult, context, 2);
            metadata.put("firstAttemptFinishReason", truncatedResult.finishReason());
            metadata.put("firstAttemptOutputTokens", truncatedResult.completionTokens());
            throw AiGenerationFailureException.truncated(metadata);
        }
        return parseValidateAndRepair(compactResult, systemPrompt, context, true, progressListener, 2);
    }

    private GeneratedProject parseValidateAndRepair(ModelChatResult result,
                                                      String systemPrompt,
                                                      GenerationContext context,
                                                      boolean allowArtifactRepair,
                                                      ModelGenerationProgressListener progressListener,
                                                      int attempt) {
        GeneratedProject project = parseResult(result, context);
        ArtifactValidationResult validation = artifactValidator.validate(project, context.codeGenType());
        if (validation.isValid()) {
            return project;
        }
        if (!allowArtifactRepair) {
            throw artifactInvalid(validation, buildAttemptMetadata(result, context, 1));
        }
        log.warn("Artifact validation failed for task {}, attempting one repair retry: {}",
                context.taskId(), validation.summary());
        List<ModelMessage> repairMessages = AiCodegenPromptBuilder.buildArtifactRepairMessages(
                systemPrompt, context, validation.summary());
        ModelChatResult repairResult = invokeAi(repairMessages, context, progressListener, attempt + 1, ModelCallPhase.REPAIR);
        if (isTruncated(repairResult)) {
            throw AiGenerationFailureException.truncated(buildTruncationMetadata(repairResult, context, 2));
        }
        GeneratedProject repairedProject = parseResult(repairResult, context);
        ArtifactValidationResult repairedValidation = artifactValidator.validate(repairedProject, context.codeGenType());
        if (!repairedValidation.isValid()) {
            Map<String, Object> metadata = buildAttemptMetadata(repairResult, context, 2);
            metadata.put("repairAttempted", true);
            throw artifactInvalid(repairedValidation, metadata);
        }
        return repairedProject;
    }

    private AiGenerationFailureException artifactInvalid(ArtifactValidationResult validation,
                                                         Map<String, Object> metadata) {
        return AiGenerationFailureException.artifactInvalid(
                validation.errorCode(),
                "AI 生成产物无法运行",
                validation.summary(),
                metadata);
    }

    private GeneratedProject parseResult(ModelChatResult result, GenerationContext context) {
        String content = result.content();
        if (content == null || content.isBlank()) {
            throw new AiOutputParseException("模型返回内容为空");
        }
        GeneratedProject parsed = parser.parse(content, context.codeGenType());
        return applyApplicationName(parsed, context);
    }

    private GeneratedProject applyApplicationName(GeneratedProject parsed, GenerationContext context) {
        String appName = context.appName();
        if (appName == null || appName.isBlank()) {
            return parsed;
        }
        String summary = parsed.summary();
        if (summary == null || summary.isBlank() || "AI 生成".equals(summary)) {
            summary = appName;
        }
        return new GeneratedProject(summary, appName, parsed.appType(), parsed.requirement(), parsed.files());
    }

    private boolean isTruncated(ModelChatResult result) {
        return result != null && "length".equalsIgnoreCase(result.finishReason());
    }

    private Map<String, Object> buildTruncationMetadata(ModelChatResult result,
                                                        GenerationContext context,
                                                        int attempt) {
        Map<String, Object> metadata = buildAttemptMetadata(result, context, attempt);
        metadata.put("finishReason", result.finishReason());
        return metadata;
    }

    private Map<String, Object> buildAttemptMetadata(ModelChatResult result,
                                                     GenerationContext context,
                                                     int attempt) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("attempt", attempt);
        metadata.put("taskId", context.taskId());
        metadata.put("provider", result.providerCode());
        metadata.put("model", result.modelName());
        metadata.put("finishReason", result.finishReason());
        metadata.put("outputTokens", result.completionTokens());
        metadata.put("configuredMaxTokens", configuredMaxTokens);
        return metadata;
    }

    String buildUserPrompt(GenerationContext context) {
        return AiCodegenPromptBuilder.buildUserPrompt(context);
    }
}
