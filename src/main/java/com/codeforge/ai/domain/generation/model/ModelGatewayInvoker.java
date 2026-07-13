package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GenerationSource;
import com.codeforge.ai.domain.generation.ModelCallPhase;
import com.codeforge.ai.domain.generation.ModelGateway;
import com.codeforge.ai.domain.generation.ModelStreamHandler;
import com.codeforge.ai.domain.generation.StreamingModelGateway;
import com.codeforge.ai.domain.prompt.model.PromptExecutionTrace;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTraceResolver;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.domain.generation.progress.ModelGenerationProgressListener;
import com.codeforge.ai.domain.generation.progress.ModelStreamProgressState;
import com.codeforge.ai.domain.generation.progress.ModelStreamProgressThrottler;
import com.codeforge.ai.infrastructure.ai.OpenAiCompatibleModelGateway;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ModelGatewayInvoker {
    private static final Logger log = LoggerFactory.getLogger(ModelGatewayInvoker.class);

    private final ModelProviderSelector selector;
    private final ModelGatewayFactory factory;
    private final ModelCallLogEntityMapper callLogMapper;
    private final PromptTemplateTraceResolver promptTemplateTraceResolver;
    private final ProviderCredentialResolver credentialResolver;

    @Value("${codeforge.ai.max-tokens:8192}")
    private int configuredMaxTokens;

    @Value("${codeforge.ai.temperature:0.2}")
    private double configuredTemperature;

    public ModelChatResult chatWithFallback(List<ModelMessage> messages, GenerationContext context) {
        List<ModelProviderEntity> providers = selector.selectAvailable();
        if (providers.isEmpty()) {
            throw new RuntimeException("没有可用的模型供应商");
        }

        Exception lastError = null;
        boolean aiAttempted = false;
        for (ModelProviderEntity provider : providers) {
            if (isRuleProvider(provider)) {
                if (!aiAttempted && lastError == null) {
                    return generateWithRuleProvider(provider, context, GenerationSource.RULE_ONLY, false, null);
                }
                return generateWithRuleProvider(provider, context, GenerationSource.RULE_FALLBACK, true,
                        lastError != null ? lastError.getMessage() : "AI 调用失败");
            }

            aiAttempted = true;
            try {
                return invokeProvider(provider, messages, context, GenerationSource.AI_DIRECT, false);
            } catch (Exception exception) {
                lastError = exception;
                recordFailure(provider, context, messages, exception, context.userId(), context.taskId(),
                        GenerationSource.AI_DIRECT.code(), false);
                log.warn("Model provider {} failed: {}", provider.getProviderCode(), exception.getMessage());
            }
        }

        throw new RuntimeException(buildProviderFailureSummary(providers.size(),
                lastError != null ? lastError.getMessage() : null));
    }

    public ModelChatResult chatWithAiProvidersOnly(List<ModelMessage> messages, GenerationContext context) {
        return streamWithAiProvidersOnly(messages, context, ModelGenerationProgressListener.NOOP, 1, ModelCallPhase.INITIAL);
    }

    public ModelChatResult streamWithAiProvidersOnly(List<ModelMessage> messages,
                                                     GenerationContext context,
                                                     ModelGenerationProgressListener progressListener,
                                                     int startingAttempt) {
        return streamWithAiProvidersOnly(messages, context, progressListener, startingAttempt, ModelCallPhase.INITIAL);
    }

    public ModelChatResult streamWithAiProvidersOnly(List<ModelMessage> messages,
                                                     GenerationContext context,
                                                     ModelGenerationProgressListener progressListener,
                                                     int startingAttempt,
                                                     ModelCallPhase phase) {
        List<ModelProviderEntity> providers = selector.selectAiProviders();
        if (providers.isEmpty()) {
            throw new NoAiProviderAvailableException("未配置可用的 AI 模型供应商或 API Key");
        }

        Exception lastError = null;
        int attempt = startingAttempt;
        String generationSource = phase != null ? phase.generationSourceCode() : ModelCallPhase.INITIAL.generationSourceCode();
        ModelStreamProgressThrottler throttler = new ModelStreamProgressThrottler(progressListener);
        for (ModelProviderEntity provider : providers) {
            throttler.resetForNewAttempt();
            try {
                return streamInvokeProvider(provider, messages, context, throttler, attempt, generationSource);
            } catch (Exception exception) {
                lastError = exception;
                recordFailure(provider, context, messages, exception, context.userId(), context.taskId(),
                        generationSource, false);
                log.warn("AI provider {} failed on {}: {}", provider.getProviderCode(), generationSource,
                        exception.getMessage());
                attempt++;
            }
        }
        throw new RuntimeException(buildProviderFailureSummary(providers.size(),
                lastError != null ? lastError.getMessage() : null));
    }

    public ModelChatResult generateWithRuleProvider(ModelProviderEntity ruleProvider,
                                                    GenerationContext context,
                                                    GenerationSource generationSource,
                                                    boolean fallbackUsed,
                                                    String fallbackReason) {
        if (ruleProvider == null) {
            throw new RuntimeException("规则生成器不可用");
        }
        try {
            ModelGateway gateway = factory.getGateway(ruleProvider);
            long start = System.currentTimeMillis();
            ModelChatRequest request = buildRequest(ruleProvider, List.of(), context);
            String content = gateway.generate(new GenerationContext(
                    context.requirement(),
                    context.appName(),
                    context.appType(),
                    context.codeGenType(),
                    context.appId(),
                    context.userId(),
                    context.taskId(),
                    context.sessionId(),
                    ruleProvider.getProviderCode(),
                    ruleProvider.getDefaultModel(),
                    ruleProvider.getBaseUrl(),
                    request.apiKey(),
                    context.systemPrompt()
            ));
            long latency = System.currentTimeMillis() - start;
            recordSuccess(ruleProvider, context, latency, context.userId(), context.taskId(),
                    generationSource.code(), fallbackUsed, fallbackReason);
            return ModelChatResult.success(content, "stop", 0L, 0L, 0L, latency,
                    ruleProvider.getProviderCode(), ruleProvider.getDefaultModel());
        } catch (Exception exception) {
            recordFailure(ruleProvider, context, List.of(), exception, context.userId(), context.taskId(),
                    generationSource.code(), fallbackUsed);
            throw new RuntimeException("规则生成失败: " + ProviderErrorSanitizer.sanitize(exception.getMessage()));
        }
    }

    private String buildProviderFailureSummary(int providerCount, String lastErrorMessage) {
        return ProviderErrorSanitizer.buildProviderFailureSummary(providerCount, lastErrorMessage);
    }

    private ModelChatResult streamInvokeProvider(ModelProviderEntity provider,
                                                 List<ModelMessage> messages,
                                                 GenerationContext context,
                                                 ModelStreamProgressThrottler throttler,
                                                 int attempt,
                                                 String generationSource) {
        ModelGateway gateway = factory.getGateway(provider);
        ModelChatRequest request = buildRequest(provider, messages, context);
        if (gateway instanceof OpenAiCompatibleModelGateway openAiGateway) {
            ModelStreamProgressState progressState = new ModelStreamProgressState(attempt);
            AtomicReference<ModelChatResult> completedResult = new AtomicReference<>();
            AtomicReference<RuntimeException> streamError = new AtomicReference<>();

            openAiGateway.streamChatRequest(request, new ModelStreamHandler() {
                @Override
                public void onStart() {
                }

                @Override
                public void onDelta(String delta) {
                    progressState.recordNonEmptyDelta(delta);
                    throttler.onNonEmptyDelta(progressState);
                }

                @Override
                public void onError(Throwable error) {
                    streamError.set(new RuntimeException(
                            error != null ? error.getMessage() : "流式调用失败", error));
                }

                @Override
                public void onComplete(ModelChatResult modelChatResult) {
                    progressState.syncReceivedCharsFromFullContent(modelChatResult.content());
                    throttler.finalFlush(progressState);
                    completedResult.set(modelChatResult);
                }
            });

            if (streamError.get() != null) {
                throw streamError.get();
            }
            ModelChatResult chatResult = completedResult.get();
            if (chatResult == null) {
                throw new RuntimeException("流式调用未返回结果");
            }
            recordSuccess(provider, context, messages, chatResult.latencyMs(), context.userId(), context.taskId(),
                    generationSource, false, chatResult);
            return chatResult;
        }

        ModelChatResult chatResult = invokeProvider(provider, messages, context, GenerationSource.AI_DIRECT, false);
        ModelStreamProgressState progressState = new ModelStreamProgressState(attempt);
        if (chatResult.content() != null && !chatResult.content().isEmpty()) {
            progressState.recordNonEmptyDelta(chatResult.content());
            throttler.onNonEmptyDelta(progressState);
            throttler.finalFlush(progressState);
        }
        return chatResult;
    }

    private ModelChatResult invokeProvider(ModelProviderEntity provider,
                                           List<ModelMessage> messages,
                                           GenerationContext context,
                                           GenerationSource generationSource,
                                           boolean fallbackUsed) {
        ModelGateway gateway = factory.getGateway(provider);
        ModelChatRequest request = buildRequest(provider, messages, context);
        if (gateway instanceof OpenAiCompatibleModelGateway openAiGateway) {
            ModelChatResult chatResult = openAiGateway.chat(request);
            recordSuccess(provider, context, messages, chatResult.latencyMs(), context.userId(), context.taskId(),
                    generationSource.code(), fallbackUsed, chatResult);
            return chatResult;
        }

        long start = System.currentTimeMillis();
        String content = gateway.generate(new GenerationContext(
                context.requirement(),
                context.appName(),
                context.appType(),
                context.codeGenType(),
                context.appId(),
                context.userId(),
                context.taskId(),
                context.sessionId(),
                provider.getProviderCode(),
                provider.getDefaultModel(),
                provider.getBaseUrl(),
                request.apiKey(),
                context.systemPrompt()
        ));
        long latency = System.currentTimeMillis() - start;
        recordSuccess(provider, context, messages, latency, context.userId(), context.taskId(),
                generationSource.code(), fallbackUsed);
        return ModelChatResult.success(content, "stop", 0L, 0L, 0L, latency,
                provider.getProviderCode(), provider.getDefaultModel());
    }

    public void streamWithFallback(GenerationContext context, ModelStreamHandler handler) {
        List<ModelProviderEntity> providers = selector.selectAvailable();
        if (providers.isEmpty()) {
            handler.onError(new RuntimeException("没有可用的模型供应商"));
            return;
        }

        Throwable lastError = null;
        for (ModelProviderEntity provider : providers) {
            if (Thread.currentThread().isInterrupted()) {
                handler.onError(new RuntimeException("流式调用已被取消"));
                return;
            }

            if (isRuleProvider(provider)) {
                continue;
            }

            try {
                StreamingModelGateway gateway = factory.getStreamingGateway(provider);
                String apiKey = credentialResolver.resolveApiKey(provider);
                GenerationContext streamingContext = new GenerationContext(
                        context.requirement(),
                        context.appName(),
                        context.appType(),
                        context.codeGenType(),
                        context.appId(),
                        context.userId(),
                        context.taskId(),
                        context.sessionId(),
                        provider.getProviderCode(),
                        provider.getDefaultModel(),
                        provider.getBaseUrl(),
                        apiKey,
                        context.systemPrompt()
                );

                StreamTracker tracker = new StreamTracker(handler);
                gateway.streamChat(streamingContext, tracker);
                if (tracker.isCompleted()) {
                    recordStreamResult(provider, context, tracker.getResult(), "SUCCESS",
                            context.userId(), context.taskId(), "AI_DIRECT", false);
                    return;
                }

                lastError = tracker.getError() != null ? tracker.getError() : new RuntimeException("未知流式错误");
                recordStreamResult(provider, context, null, "FAILED",
                        context.userId(), context.taskId(), "AI_DIRECT", false);
            } catch (Exception exception) {
                lastError = exception;
                recordStreamResult(provider, context, null, "FAILED",
                        context.userId(), context.taskId(), "AI_DIRECT", false);
            }
        }

        simulateStreamingFromRule(context, handler, lastError);
    }

    private void simulateStreamingFromRule(GenerationContext context, ModelStreamHandler handler, Throwable previousError) {
        ModelProviderEntity ruleProvider = selector.selectAvailable().stream()
                .filter(this::isRuleProvider)
                .findFirst()
                .orElse(null);
        if (ruleProvider == null) {
            handler.onError(new RuntimeException("所有模型供应商流式调用均失败，最后错误: "
                    + (previousError != null ? ProviderErrorSanitizer.sanitize(previousError.getMessage()) : "unknown")));
            return;
        }

        try {
            handler.onStart();
            ModelGateway gateway = factory.getGateway(ruleProvider);
            GenerationContext ruleContext = new GenerationContext(
                    context.requirement(),
                    context.appName(),
                    context.appType(),
                    context.codeGenType(),
                    context.appId(),
                    context.userId(),
                    context.taskId(),
                    context.sessionId(),
                    ruleProvider.getProviderCode(),
                    ruleProvider.getDefaultModel(),
                    ruleProvider.getBaseUrl(),
                    null,
                    context.systemPrompt()
            );
            long start = System.currentTimeMillis();
            String content = gateway.generate(ruleContext);
            String[] chunks = content.split("\n\n");
            StringBuilder fullContent = new StringBuilder();
            for (String chunk : chunks) {
                String delta = chunk.trim().isEmpty() ? "\n\n" : chunk + "\n\n";
                fullContent.append(delta);
                handler.onDelta(delta);
            }
            long latency = System.currentTimeMillis() - start;
            ModelChatResult result = ModelChatResult.success(
                    fullContent.toString(),
                    "stop",
                    0L,
                    0L,
                    0L,
                    latency,
                    ruleProvider.getProviderCode(),
                    ruleProvider.getDefaultModel()
            );
            String generationSource = previousError == null ? GenerationSource.RULE_ONLY.code() : GenerationSource.RULE_FALLBACK.code();
            boolean fallbackUsed = previousError != null;
            recordStreamResult(ruleProvider, context, result, "SUCCESS",
                    context.userId(), context.taskId(), generationSource, fallbackUsed);
            handler.onComplete(result);
        } catch (Exception exception) {
            recordStreamResult(ruleProvider, context, null, "FAILED",
                    context.userId(), context.taskId(), GenerationSource.RULE_FALLBACK.code(), true);
            handler.onError(new RuntimeException("规则引擎流式模拟失败: " + ProviderErrorSanitizer.sanitize(exception.getMessage())));
        }
    }

    private ModelChatRequest buildRequest(ModelProviderEntity provider, List<ModelMessage> messages, GenerationContext context) {
        String key = isRuleProvider(provider) ? null : credentialResolver.resolveApiKey(provider);
        return ModelChatRequest.of(
                provider.getProviderCode(),
                provider.getBaseUrl(),
                key,
                provider.getDefaultModel(),
                messages,
                context.appId(),
                context.taskId(),
                context.userId(),
                configuredMaxTokens,
                configuredTemperature
        );
    }

    private void recordSuccess(ModelProviderEntity provider,
                               GenerationContext context,
                               List<ModelMessage> messages,
                               long latencyMs,
                               Long userId,
                               Long taskId,
                               String generationSource,
                               boolean fallbackUsed) {
        recordSuccess(provider, context, messages, latencyMs, userId, taskId, generationSource, fallbackUsed, (ModelChatResult) null);
    }

    private void recordSuccess(ModelProviderEntity provider,
                               GenerationContext context,
                               List<ModelMessage> messages,
                               long latencyMs,
                               Long userId,
                               Long taskId,
                               String generationSource,
                               boolean fallbackUsed,
                               ModelChatResult chatResult) {
        try {
            callLogMapper.insertCallLog(applyPromptTrace(ModelCallLogEntity.builder()
                    .taskId(taskId)
                    .appId(context.appId())
                    .sessionId(context.sessionId())
                    .providerId(provider.getId())
                    .providerCode(provider.getProviderCode())
                    .modelName(resolveModelName(provider, context, chatResult))
                    .apiProtocol(provider.getApiProtocol())
                    .status("SUCCESS")
                    .durationMs(latencyMs)
                    .inputTokens(chatResult != null && chatResult.promptTokens() != null
                            ? chatResult.promptTokens().intValue() : 0)
                    .outputTokens(chatResult != null && chatResult.completionTokens() != null
                            ? chatResult.completionTokens().intValue() : 0)
                    .fallbackUsed(fallbackUsed)
                    .generationSource(generationSource)
                    .createdBy(userId)
                    .createdAt(LocalDateTime.now())
                    .build(), context, messages));
        } catch (Exception exception) {
            log.warn("Failed to write call log: {}",
                    com.codeforge.ai.infrastructure.persistence.SqlExceptionDiagnostics.summarize(exception));
        }
    }

    private void recordSuccess(ModelProviderEntity provider,
                               GenerationContext context,
                               long latencyMs,
                               Long userId,
                               Long taskId,
                               String generationSource,
                               boolean fallbackUsed,
                               String fallbackReason) {
        try {
            callLogMapper.insertCallLog(applyPromptTrace(ModelCallLogEntity.builder()
                    .taskId(taskId)
                    .appId(context.appId())
                    .sessionId(context.sessionId())
                    .providerId(provider.getId())
                    .providerCode(provider.getProviderCode())
                    .modelName(resolveModelName(provider, context, null))
                    .apiProtocol(provider.getApiProtocol())
                    .status("SUCCESS")
                    .durationMs(latencyMs)
                    .inputTokens(0)
                    .outputTokens(0)
                    .fallbackUsed(fallbackUsed)
                    .generationSource(generationSource)
                    .errorMessage(fallbackUsed ? ProviderErrorSanitizer.sanitize(fallbackReason) : null)
                    .createdBy(userId)
                    .createdAt(LocalDateTime.now())
                    .build(), context, List.of()));
        } catch (Exception exception) {
            log.warn("Failed to write call log: {}",
                    com.codeforge.ai.infrastructure.persistence.SqlExceptionDiagnostics.summarize(exception));
        }
    }

    private void recordFailure(ModelProviderEntity provider,
                               GenerationContext context,
                               List<ModelMessage> messages,
                               Exception exception,
                               Long userId,
                               Long taskId,
                               String generationSource,
                               boolean fallbackUsed) {
        try {
            callLogMapper.insertCallLog(applyPromptTrace(ModelCallLogEntity.builder()
                    .taskId(taskId)
                    .appId(context.appId())
                    .sessionId(context.sessionId())
                    .providerId(provider.getId())
                    .providerCode(provider.getProviderCode())
                    .modelName(resolveModelName(provider, context, null))
                    .apiProtocol(provider.getApiProtocol())
                    .status("FAILED")
                    .durationMs(0L)
                    .inputTokens(0)
                    .outputTokens(0)
                    .fallbackUsed(fallbackUsed)
                    .generationSource(generationSource)
                    .errorMessage(ProviderErrorSanitizer.sanitize(exception.getMessage()))
                    .createdBy(userId)
                    .createdAt(LocalDateTime.now())
                    .build(), context, messages));
        } catch (Exception writeException) {
            log.warn("Failed to write call log: {}",
                    com.codeforge.ai.infrastructure.persistence.SqlExceptionDiagnostics.summarize(writeException));
        }
    }

    private void recordStreamResult(ModelProviderEntity provider,
                                    GenerationContext context,
                                    ModelChatResult result,
                                    String status,
                                    Long userId,
                                    Long taskId,
                                    String generationSource,
                                    boolean fallbackUsed) {
        try {
            callLogMapper.insertCallLog(applyPromptTrace(ModelCallLogEntity.builder()
                    .taskId(taskId)
                    .appId(context.appId())
                    .sessionId(context.sessionId())
                    .providerId(provider.getId())
                    .providerCode(provider.getProviderCode())
                    .modelName(resolveModelName(provider, context, result))
                    .apiProtocol(provider.getApiProtocol())
                    .status(status)
                    .durationMs(result != null ? result.latencyMs() : 0L)
                    .inputTokens(result != null && result.promptTokens() != null ? result.promptTokens().intValue() : 0)
                    .outputTokens(result != null && result.completionTokens() != null ? result.completionTokens().intValue() : 0)
                    .fallbackUsed(fallbackUsed)
                    .generationSource(generationSource)
                    .createdBy(userId)
                    .createdAt(LocalDateTime.now())
                    .build(), context, List.of()));
        } catch (Exception exception) {
            log.warn("Failed to write stream call log: {}",
                    com.codeforge.ai.infrastructure.persistence.SqlExceptionDiagnostics.summarize(exception));
        }
    }

    private boolean isRuleProvider(ModelProviderEntity provider) {
        return provider != null && "RULE_BASED".equalsIgnoreCase(provider.getApiProtocol());
    }

    private String resolveModelName(ModelProviderEntity provider, GenerationContext context, ModelChatResult result) {
        if (result != null && result.modelName() != null && !result.modelName().isBlank()) {
            return result.modelName();
        }
        if (context.modelName() != null && !context.modelName().isBlank()) {
            return context.modelName();
        }
        if (provider.getDefaultModel() != null && !provider.getDefaultModel().isBlank()) {
            return provider.getDefaultModel();
        }
        return provider.getProviderCode();
    }

    private ModelCallLogEntity applyPromptTrace(ModelCallLogEntity entity,
                                                GenerationContext context,
                                                List<ModelMessage> messages) {
        PromptExecutionTrace trace = PromptExecutionTrace.fromProviderPayload(messages, context, promptTemplateTraceResolver);
        if (trace.promptTemplateVersionId() == null) {
            trace = PromptExecutionTrace.noTemplateFromProviderPayload(messages, context);
        }
        return trace.applyTo(entity);
    }

    private static class StreamTracker implements ModelStreamHandler {
        private final ModelStreamHandler delegate;
        private volatile boolean completed;
        private volatile Throwable error;
        private volatile ModelChatResult result;

        private StreamTracker(ModelStreamHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onStart() {
            delegate.onStart();
        }

        @Override
        public void onDelta(String delta) {
            delegate.onDelta(delta);
        }

        @Override
        public synchronized void onError(Throwable throwable) {
            error = throwable;
            delegate.onError(throwable);
        }

        @Override
        public synchronized void onComplete(ModelChatResult modelChatResult) {
            result = modelChatResult;
            delegate.onComplete(modelChatResult);
            completed = true;
        }

        private boolean isCompleted() {
            return completed;
        }

        private Throwable getError() {
            return error;
        }

        private ModelChatResult getResult() {
            return result;
        }
    }
}
