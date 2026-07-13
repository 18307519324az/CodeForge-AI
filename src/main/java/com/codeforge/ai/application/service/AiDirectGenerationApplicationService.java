package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.CodeGenerationAiService;
import com.codeforge.ai.application.generation.GenerationFallbackPolicy;
import com.codeforge.ai.application.generator.RuleBasedAppGenerator;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.common.BaseEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.generation.CodeGenTypeEnum;
import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GenerationSource;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.validation.ArtifactValidationResult;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import com.codeforge.ai.application.task.GenerationTaskRequestPayloadSupport;
import com.codeforge.ai.application.task.GenerationTaskRequestPayloadSupport.ParsedPayload;
import com.codeforge.ai.domain.prompt.model.PromptExecutionTrace;
import com.codeforge.ai.domain.prompt.model.PromptTemplateExecutionResolver;
import com.codeforge.ai.domain.prompt.model.ResolvedGenerationPrompt;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.model.NoAiProviderAvailableException;
import com.codeforge.ai.domain.generation.progress.ModelGenerationProgress;
import com.codeforge.ai.domain.generation.progress.ModelGenerationProgressListener;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser.AiOutputParseException;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskEventType;
import com.codeforge.ai.domain.task.enums.GenerationTaskStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEventEntityMapper;
import com.codeforge.ai.infrastructure.persistence.SqlExceptionDiagnostics;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiDirectGenerationApplicationService {

    private final CodeGenerationAiService aiService;
    private final ModelProviderSelector providerSelector;
    private final RuleBasedAppGenerator ruleBasedAppGenerator = new RuleBasedAppGenerator();
    private final GenerationTaskEntityMapper generationTaskEntityMapper;
    private final GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    private final GenerationTaskStreamRegistry generationTaskStreamRegistry;
    private final PublicGenerationStreamEventMapper publicGenerationStreamEventMapper;
    private final GenerationRecordEntityMapper generationRecordEntityMapper;
    private final AiAppEntityMapper aiAppEntityMapper;
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final GeneratedFileEntityMapper generatedFileEntityMapper;
    private final ModelCallLogEntityMapper modelCallLogEntityMapper;
    private final GeneratedArtifactValidator artifactValidator;
    private final ObjectMapper objectMapper;
    private final PromptTemplateExecutionResolver promptTemplateExecutionResolver;
    private final com.codeforge.ai.domain.prompt.model.PromptTemplateTraceResolver promptTemplateTraceResolver;

    @Value("${codeforge.generation.force-rule-only:false}")
    private boolean forceRuleOnly;

    public GenerationExecutionResult executeSync(GenerationTaskEntity task,
                                                 AiAppEntity app,
                                                 String requirement,
                                                 Long userId,
                                                 String requestId) {
        long startedAt = System.currentTimeMillis();
        try {
            generationTaskEntityMapper.updateTerminalState(task.getId(), GenerationTaskStatus.RUNNING.name(),
                    null, null, null, userId);
            publishEvent(task.getId(), GenerationTaskEventType.TASK_STARTED, "开始执行生成任务",
                    payload("RUNNING"), requestId, userId);
            publishEvent(task.getId(), GenerationTaskEventType.PROMPT_RENDERED, "已生成提示词上下文",
                    payload("GENERATING"), requestId, userId);

            String codeGenType = CodeGenTypeEnum.fromAppType(app.getAppType()).name();
            GenerationContext context = buildGenerationContext(task, app, requirement, userId, codeGenType);

            publishEvent(task.getId(), GenerationTaskEventType.MODEL_CALL_STARTED,
                    buildModelStartMessage(context),
                    json(Map.of(
                            "taskStatus", "GENERATING",
                            "aiConfigured", providerSelector.hasConfiguredAiProvider() && !forceRuleOnly
                    )),
                    requestId, userId);

            ResolvedGeneration resolved = resolveGeneration(context, userId, requestId);

            if (resolved.source() == GenerationSource.AI_DIRECT) {
                publishEvent(task.getId(), GenerationTaskEventType.MODEL_CALL_FINISHED, "AI 模型调用完成",
                        modelPayload(resolved, "VALIDATING"), requestId, userId);
            } else if (resolved.source() == GenerationSource.RULE_FALLBACK) {
                publishEvent(task.getId(), GenerationTaskEventType.MODEL_CALL_FINISHED, "AI 模型不可用，已使用规则模式生成",
                        modelPayload(resolved, "VALIDATING"), requestId, userId);
            } else {
                publishEvent(task.getId(), GenerationTaskEventType.MODEL_CALL_FINISHED, "AI 模型不可用，已使用规则模式生成",
                        modelPayload(resolved, "VALIDATING"), requestId, userId);
            }

            ArtifactValidationResult artifactValidation = artifactValidator.validate(
                    resolved.project(), codeGenType);
            if (!artifactValidation.isValid()) {
                throw AiGenerationFailureException.artifactInvalid(
                        artifactValidation.errorCode(),
                        "AI 生成产物无法运行",
                        artifactValidation.summary(),
                        Map.of("taskId", task.getId(), "generationSource", resolved.source().code()));
            }

            int fileCount = resolved.project().files().size();
            publishEvent(task.getId(), GenerationTaskEventType.FILES_GENERATED,
                    "已生成 " + fileCount + " 个项目文件",
                    json(Map.of("taskStatus", "PERSISTING", "fileCount", fileCount,
                            "generationSource", resolved.source().code(), "fallbackUsed", resolved.fallbackUsed())),
                    requestId, userId);

            PersistedVersion version = persistProject(app, task, resolved.project(), resolved, userId);

            aiAppEntityMapper.updateCurrentVersionId(app.getId(), version.versionId(), userId);
            aiAppEntityMapper.updateStatus(app.getId(), "DEVELOPING", userId);
            generationRecordEntityMapper.updateResultByTaskId(task.getId(),
                    GenerationTaskStatus.SUCCESS.name(),
                    "生成 " + fileCount + " 个文件 (" + resolved.source().code() + ")",
                    System.currentTimeMillis() - startedAt, userId);

            int updatedRows = generationTaskEntityMapper.updateTerminalState(task.getId(), GenerationTaskStatus.SUCCESS.name(),
                    null, null, LocalDateTime.now(), userId);
            if (updatedRows == 0) {
                throw new IllegalStateException("Failed to mark generation task as SUCCESS, taskId=" + task.getId());
            }

            publishEvent(task.getId(), GenerationTaskEventType.VERSION_CREATED,
                    "已创建版本 v" + version.versionNo(),
                    json(Map.of("versionId", version.versionId(), "versionNo", version.versionNo(),
                            "generationSource", resolved.source().code())),
                    requestId, userId);

            publishEvent(task.getId(), GenerationTaskEventType.TASK_SUCCESS,
                    "生成完成，共生成 " + fileCount + " 个文件",
                    json(Map.of(
                            "taskStatus", "SUCCESS",
                            "fileCount", fileCount,
                            "versionId", version.versionId(),
                            "generationSource", resolved.source().code(),
                            "fallbackUsed", resolved.fallbackUsed(),
                            "providerCode", resolved.providerCode(),
                            "modelName", resolved.modelName()
                    )),
                    requestId, userId);

            return new GenerationExecutionResult(resolved.source(), resolved.fallbackUsed(),
                    resolved.providerCode(), resolved.modelName(), version.versionId(), fileCount, true, null);
        } catch (AiGenerationFailureException failure) {
            return failGenerationTask(task, userId, requestId, startedAt, failure);
        } catch (AiOutputParseException parseException) {
            return failGenerationTask(task, userId, requestId, startedAt, parseException);
        } catch (Exception exception) {
            if (GenerationFallbackPolicy.allowsRuleFallback(exception)) {
                log.warn("AI generation failed for task {}, fallback to rule: {}", task.getId(), exception.getMessage());
                return executeRuleFallbackAfterFailure(task, app, requirement, userId, requestId, startedAt, exception);
            }
            return failGenerationTask(task, userId, requestId, startedAt, exception);
        }
    }

    private GenerationExecutionResult executeRuleFallbackAfterFailure(GenerationTaskEntity task,
                                                                      AiAppEntity app,
                                                                      String requirement,
                                                                      Long userId,
                                                                      String requestId,
                                                                      long startedAt,
                                                                      Exception cause) {
        try {
            String codeGenType = CodeGenTypeEnum.fromAppType(app.getAppType()).name();
            GenerationContext context = buildGenerationContext(task, app, requirement, userId, codeGenType);
            ResolvedGeneration resolved = resolveRuleFallback(context, userId, cause.getMessage());
            int fileCount = resolved.project().files().size();
            PersistedVersion version = persistProject(app, task, resolved.project(), resolved, userId);
            aiAppEntityMapper.updateCurrentVersionId(app.getId(), version.versionId(), userId);
            aiAppEntityMapper.updateStatus(app.getId(), "DEVELOPING", userId);
            generationRecordEntityMapper.updateResultByTaskId(task.getId(),
                    GenerationTaskStatus.SUCCESS.name(),
                    "生成 " + fileCount + " 个文件 (" + resolved.source().code() + ")",
                    System.currentTimeMillis() - startedAt, userId);
            int updatedRows = generationTaskEntityMapper.updateTerminalState(task.getId(), GenerationTaskStatus.SUCCESS.name(),
                    null, null, LocalDateTime.now(), userId);
            if (updatedRows == 0) {
                throw new IllegalStateException("Failed to mark generation task as SUCCESS, taskId=" + task.getId());
            }
            publishEvent(task.getId(), GenerationTaskEventType.VERSION_CREATED,
                    "已创建版本 v" + version.versionNo(),
                    json(Map.of("versionId", version.versionId(), "versionNo", version.versionNo(),
                            "generationSource", resolved.source().code())),
                    requestId, userId);
            publishEvent(task.getId(), GenerationTaskEventType.TASK_SUCCESS,
                    "生成完成，共生成 " + fileCount + " 个文件",
                    json(Map.of(
                            "taskStatus", "SUCCESS",
                            "fileCount", fileCount,
                            "versionId", version.versionId(),
                            "generationSource", resolved.source().code(),
                            "fallbackUsed", resolved.fallbackUsed(),
                            "providerCode", resolved.providerCode(),
                            "modelName", resolved.modelName()
                    )),
                    requestId, userId);
            return new GenerationExecutionResult(resolved.source(), resolved.fallbackUsed(),
                    resolved.providerCode(), resolved.modelName(), version.versionId(), fileCount, true, null);
        } catch (Exception exception) {
            return failGenerationTask(task, userId, requestId, startedAt, exception);
        }
    }

    private GenerationExecutionResult failGenerationTask(GenerationTaskEntity task,
                                                           Long userId,
                                                           String requestId,
                                                           long startedAt,
                                                           Throwable failure) {
        if (GenerationFallbackPolicy.isSseTransportFailure(failure)) {
            log.warn("Suppressing TASK_FAILED for SSE transport failure on task {}", task.getId(), failure);
            return new GenerationExecutionResult(null, false, null, null, null, 0, false, null);
        }
        String errorCode = GenerationFallbackPolicy.taskErrorCode(failure);
        String userMessage = GenerationFallbackPolicy.taskErrorMessage(failure);
        log.warn("Generation task {} failed with {}: {}", task.getId(), errorCode, userMessage);

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("taskStatus", "FAILED");
        payload.put("errorCode", errorCode);
        payload.put("error", userMessage);
        if (failure instanceof AiGenerationFailureException generationFailure) {
            payload.putAll(generationFailure.safeMetadata());
        }

        int updatedRows = generationTaskEntityMapper.updateTerminalState(task.getId(), GenerationTaskStatus.FAILED.name(),
                errorCode, userMessage, LocalDateTime.now(), userId);
        if (updatedRows > 0) {
            generationRecordEntityMapper.updateResultByTaskId(task.getId(),
                    GenerationTaskStatus.FAILED.name(),
                    userMessage,
                    System.currentTimeMillis() - startedAt,
                    userId);
            publishEvent(task.getId(), GenerationTaskEventType.TASK_FAILED,
                    "生成失败：" + userMessage,
                    json(payload),
                    requestId, userId);
        } else {
            log.warn("Skipped TASK_FAILED event for task {} because terminal state was not updated", task.getId());
        }
        return new GenerationExecutionResult(null, false, null, null, null, 0, false, userMessage);
    }

    private ResolvedGeneration resolveGeneration(GenerationContext context, Long userId, String requestId) {
        if (forceRuleOnly || !providerSelector.hasConfiguredAiProvider()) {
            ModelProviderEntity ruleProvider = providerSelector.selectRuleProvider();
            GeneratedProject project = toGeneratedProject(ruleBasedAppGenerator.generate(
                    context.appName(), context.appType(), context.requirement()));
            String reason = forceRuleOnly ? "force-rule-only" : "no-configured-ai-provider";
            recordRuleOnlyLog(context, ruleProvider, userId, reason);
            return new ResolvedGeneration(project, GenerationSource.RULE_ONLY, false,
                    ruleProvider != null ? ruleProvider.getProviderCode() : "rule",
                    ruleProvider != null ? ruleProvider.getDefaultModel() : "rule-based", reason);
        }

        try {
            ModelGenerationProgressListener progressListener = createModelProgressListener(context.taskId(), requestId, userId);
            GeneratedProject project = aiService.generate(context, progressListener);
            ModelCallLogEntity latestLog = latestLog(context.taskId());
            return new ResolvedGeneration(
                    project,
                    GenerationSource.AI_DIRECT,
                    false,
                    resolveProviderCode(latestLog),
                    resolveModelName(latestLog),
                    null
            );
        } catch (NoAiProviderAvailableException exception) {
            return resolveRuleOnly(context, userId, requestId, exception.getMessage());
        } catch (AiGenerationFailureException | AiOutputParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (GenerationFallbackPolicy.allowsRuleFallback(exception)) {
                log.warn("AI generation failed for task {}, fallback to rule: {}", context.taskId(), exception.getMessage());
                return resolveRuleFallback(context, userId, exception.getMessage());
            }
            throw exception;
        }
    }

    private ResolvedGeneration resolveRuleFallback(GenerationContext context, Long userId, String reason) {
        ModelProviderEntity ruleProvider = providerSelector.selectRuleProvider();
        GeneratedProject project = toGeneratedProject(ruleBasedAppGenerator.generate(
                context.appName(), context.appType(), context.requirement()));
        recordRuleFallbackLog(context, ruleProvider, userId, reason);
        return new ResolvedGeneration(
                project,
                GenerationSource.RULE_FALLBACK,
                true,
                ruleProvider != null ? ruleProvider.getProviderCode() : "rule",
                ruleProvider != null ? ruleProvider.getDefaultModel() : "rule-based",
                reason
        );
    }

    private ResolvedGeneration resolveRuleOnly(GenerationContext context, Long userId, String requestId, String reason) {
        ModelProviderEntity ruleProvider = providerSelector.selectRuleProvider();
        GeneratedProject project = toGeneratedProject(ruleBasedAppGenerator.generate(
                context.appName(), context.appType(), context.requirement()));
        recordRuleOnlyLog(context, ruleProvider, userId, reason);
        return new ResolvedGeneration(project, GenerationSource.RULE_ONLY, false,
                ruleProvider != null ? ruleProvider.getProviderCode() : "rule",
                ruleProvider != null ? ruleProvider.getDefaultModel() : "rule-based", reason);
    }

    private void recordRuleOnlyLog(GenerationContext context, ModelProviderEntity ruleProvider, Long userId, String reason) {
        if (ruleProvider == null) {
            return;
        }
        insertCallLog(context, ruleProvider, userId, GenerationSource.RULE_ONLY.code(), false, reason);
    }

    private void recordRuleFallbackLog(GenerationContext context, ModelProviderEntity ruleProvider, Long userId, String reason) {
        if (ruleProvider == null) {
            return;
        }
        insertCallLog(context, ruleProvider, userId, GenerationSource.RULE_FALLBACK.code(), true, reason);
    }

    private void insertCallLog(GenerationContext context,
                               ModelProviderEntity provider,
                               Long userId,
                               String generationSource,
                               boolean fallbackUsed,
                               String errorMessage) {
        try {
            List<com.codeforge.ai.domain.generation.model.ModelMessage> messages = List.of();
            PromptExecutionTrace trace = context != null && context.usesTemplatePrompt()
                    ? PromptExecutionTrace.fromProviderPayload(messages, context, promptTemplateTraceResolver)
                    : PromptExecutionTrace.noTemplateFromProviderPayload(messages, context);
            ModelCallLogEntity entity = ModelCallLogEntity.builder()
                    .taskId(context.taskId())
                    .appId(context.appId())
                    .providerId(provider.getId())
                    .providerCode(provider.getProviderCode())
                    .modelName(provider.getDefaultModel())
                    .apiProtocol(provider.getApiProtocol())
                    .status("SUCCESS")
                    .durationMs(0L)
                    .inputTokens(0)
                    .outputTokens(0)
                    .fallbackUsed(fallbackUsed)
                    .generationSource(generationSource)
                    .errorMessage(errorMessage)
                    .createdBy(userId)
                    .createdAt(LocalDateTime.now())
                    .build();
            modelCallLogEntityMapper.insertCallLog(trace.applyTo(entity));
        } catch (Exception exception) {
            log.warn("Failed to write rule generation call log: {}", exception.getMessage());
        }
    }

    private ModelCallLogEntity latestLog(Long taskId) {
        try {
            List<ModelCallLogEntity> logs = modelCallLogEntityMapper.findByTaskId(taskId);
            return logs.isEmpty() ? null : logs.getFirst();
        } catch (RuntimeException exception) {
            log.error("Failed to read model call log for task {}: {}",
                    taskId, SqlExceptionDiagnostics.summarize(exception));
            return null;
        }
    }

    private String resolveProviderCode(ModelCallLogEntity latestLog) {
        if (latestLog != null && latestLog.getProviderCode() != null && !latestLog.getProviderCode().isBlank()) {
            return latestLog.getProviderCode();
        }
        return providerSelector.selectAiProviders().stream()
                .findFirst()
                .map(ModelProviderEntity::getProviderCode)
                .orElse("deepseek");
    }

    private String resolveModelName(ModelCallLogEntity latestLog) {
        if (latestLog != null && latestLog.getModelName() != null && !latestLog.getModelName().isBlank()) {
            return latestLog.getModelName();
        }
        return providerSelector.selectAiProviders().stream()
                .findFirst()
                .map(ModelProviderEntity::getDefaultModel)
                .orElse(null);
    }

    private PersistedVersion persistProject(AiAppEntity app,
                                            GenerationTaskEntity task,
                                            GeneratedProject project,
                                            ResolvedGeneration resolved,
                                            Long userId) throws Exception {
        int nextVersionNo = getNextVersionNo(app.getId());
        AppVersionEntity version = AppVersionEntity.builder()
                .appId(app.getId())
                .versionNo(nextVersionNo)
                .versionSource(resolved.source().code())
                .sourceTaskId(task.getId())
                .changeSummary("根据需求自动生成：" + truncateSummary(task.getRequestPayloadJson()))
                .status("READY")
                .build();
        version.setCreatedBy(userId);
        version.setUpdatedBy(userId);
        applyAuditFields(version, userId);
        appVersionEntityMapper.insertVersion(version);

        Path storageDir = Path.of(".local-storage", "apps", String.valueOf(app.getId()),
                "versions", String.valueOf(version.getId()));
        Files.createDirectories(storageDir);

        for (GeneratedProjectFile file : project.files()) {
            String normalizedFilePath = normalizeRelativeFilePath(file.filePath(), file.fileName());
            Path targetPath = resolveVersionFilePath(storageDir, normalizedFilePath);
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, file.content(), java.nio.charset.StandardCharsets.UTF_8);

            GeneratedFileEntity fileEntity = GeneratedFileEntity.builder()
                    .appVersionId(version.getId())
                    .filePath(normalizedFilePath)
                    .fileName(targetPath.getFileName().toString())
                    .fileType(detectFileType(normalizedFilePath))
                    .fileContent(file.content())
                    .storagePath(targetPath.toString())
                    .fileSize((long) file.content().length())
                    .build();
            fileEntity.setCreatedBy(userId);
            fileEntity.setUpdatedBy(userId);
            applyAuditFields(fileEntity, userId);
            generatedFileEntityMapper.insertFile(fileEntity);
        }

        boolean hasIndexHtml = project.files().stream()
                .map(file -> normalizeRelativeFilePath(file.filePath(), file.fileName()))
                .anyMatch(path -> "index.html".equalsIgnoreCase(path));
        if (hasIndexHtml) {
            appVersionEntityMapper.updatePreviewInfo(
                    version.getId(),
                    "/api/v1/static-preview/" + version.getId() + "/index.html",
                    "READY",
                    userId);
        }

        return new PersistedVersion(version.getId(), nextVersionNo);
    }

    private String normalizeRelativeFilePath(String filePath, String fileName) {
        String rawPath = filePath;
        if (rawPath == null || rawPath.isBlank()) {
            rawPath = fileName;
        }
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("generated file path is blank");
        }

        String normalizedSeparators = rawPath.replace('\\', '/').trim();
        if (normalizedSeparators.startsWith("/") || normalizedSeparators.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("absolute generated file path is not allowed: " + rawPath);
        }

        Path normalizedPath = Paths.get(normalizedSeparators).normalize();
        String normalized = normalizedPath.toString().replace('\\', '/');
        if (normalized.isBlank() || ".".equals(normalized) || normalized.startsWith("..")) {
            throw new IllegalArgumentException("unsafe generated file path: " + rawPath);
        }
        return normalized;
    }

    private Path resolveVersionFilePath(Path versionRoot, String relativeFilePath) {
        Path resolved = versionRoot.resolve(relativeFilePath).normalize();
        if (!resolved.startsWith(versionRoot)) {
            throw new IllegalArgumentException("generated file path escapes version root: " + relativeFilePath);
        }
        return resolved;
    }

    private GeneratedProject toGeneratedProject(RuleBasedAppGenerator.GeneratedProject raw) {
        return new GeneratedProject(
                raw.appName(),
                raw.appName(),
                raw.appType(),
                raw.requirement(),
                raw.files().stream()
                        .map(file -> new GeneratedProjectFile(file.filePath(), file.fileName(), file.content()))
                        .toList()
        );
    }

    private int getNextVersionNo(Long appId) {
        List<AppVersionEntity> versions = appVersionEntityMapper.findByAppId(appId);
        return versions.stream().mapToInt(AppVersionEntity::getVersionNo).max().orElse(0) + 1;
    }

    private String buildModelStartMessage(GenerationContext context) {
        return "准备调用模型，appId=" + context.appId() + ", taskId=" + context.taskId();
    }

    private GenerationContext buildGenerationContext(GenerationTaskEntity task,
                                                       AiAppEntity app,
                                                       String requirement,
                                                       Long userId,
                                                       String codeGenType) {
        ParsedPayload payload = GenerationTaskRequestPayloadSupport.parse(objectMapper, task.getRequestPayloadJson());
        Long templateId = task.getPromptTemplateId() != null ? task.getPromptTemplateId() : payload.promptTemplateId();
        Long versionId = task.getPromptTemplateVersionId() != null
                ? task.getPromptTemplateVersionId()
                : payload.promptTemplateVersionId();
        var resolvedPrompt = promptTemplateExecutionResolver.resolveOptional(
                templateId,
                versionId,
                requirement,
                payload.templateVariables());
        if (resolvedPrompt.isPresent()) {
            ResolvedGenerationPrompt prompt = resolvedPrompt.get();
            return new GenerationContext(
                    requirement,
                    app.getName(),
                    app.getAppType(),
                    codeGenType,
                    app.getId(),
                    userId,
                    task.getId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    prompt.renderedSystemPrompt(),
                    prompt.renderedUserPrompt(),
                    prompt.templateId(),
                    prompt.templateVersionId(),
                    prompt.templateCode(),
                    prompt.versionNo());
        }
        return new GenerationContext(
                requirement,
                app.getName(),
                app.getAppType(),
                codeGenType,
                app.getId(),
                userId,
                task.getId(),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private String modelPayload(ResolvedGeneration resolved, String taskStatus) {
        return json(Map.of(
                "taskStatus", taskStatus,
                "generationSource", resolved.source().code(),
                "fallbackUsed", resolved.fallbackUsed(),
                "providerCode", resolved.providerCode(),
                "modelName", resolved.modelName() != null ? resolved.modelName() : ""
        ));
    }

    private String payload(String taskStatus) {
        return json(Map.of("taskStatus", taskStatus));
    }

    private String json(java.util.Map<String, Object> values) {
        ObjectNode node = objectMapper.createObjectNode();
        values.forEach((key, value) -> {
            if (value == null) {
                node.putNull(key);
            } else if (value instanceof Number number) {
                putNumber(node, key, number);
            } else if (value instanceof Boolean bool) {
                node.put(key, bool);
            } else {
                node.put(key, String.valueOf(value));
            }
        });
        return node.toString();
    }

    private void putNumber(ObjectNode node, String key, Number number) {
        if (number instanceof Long || number instanceof Integer || number instanceof Short || number instanceof Byte) {
            node.put(key, number.longValue());
            return;
        }
        double doubleValue = number.doubleValue();
        if (!Double.isNaN(doubleValue) && !Double.isInfinite(doubleValue) && doubleValue == Math.rint(doubleValue)) {
            node.put(key, number.longValue());
            return;
        }
        node.put(key, doubleValue);
    }

    private String truncateSummary(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }

    private String truncateErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        String sanitized = message.replace("\"", "'");
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }

    private void applyAuditFields(BaseEntity entity, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        if (entity.getIsDeleted() == null) {
            entity.setIsDeleted(0);
        }
    }

    private String detectFileType(String filePath) {
        if (filePath == null) {
            return "other";
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".md")) {
            return "markdown";
        }
        if (lower.endsWith(".vue")) {
            return "vue";
        }
        if (lower.endsWith(".ts")) {
            return "typescript";
        }
        if (lower.endsWith(".js")) {
            return "javascript";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        if (lower.endsWith(".css")) {
            return "css";
        }
        if (lower.endsWith(".html")) {
            return "html";
        }
        return "other";
    }

    private ModelGenerationProgressListener createModelProgressListener(Long taskId, String requestId, Long userId) {
        return progress -> publishModelDelta(taskId, progress, requestId, userId);
    }

    private void publishModelDelta(Long taskId,
                                   ModelGenerationProgress progress,
                                   String requestId,
                                   Long userId) {
        if (progress == null || progress.chunkCount() <= 0) {
            return;
        }
        publishEvent(taskId, GenerationTaskEventType.MODEL_DELTA,
                "AI 正在生成项目内容",
                json(Map.of(
                        "attempt", progress.attempt(),
                        "receivedChars", progress.receivedChars(),
                        "chunkCount", progress.chunkCount(),
                        "elapsedMs", progress.elapsedMs()
                )),
                requestId,
                userId);
    }

    private void publishEvent(Long taskId,
                              GenerationTaskEventType eventType,
                              String message,
                              String payloadJson,
                              String requestId,
                              Long userId) {
        var eventEntity = com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity.builder()
                .taskId(taskId)
                .eventType(eventType.name())
                .eventMessage(message)
                .eventPayloadJson(payloadJson)
                .requestId(requestId)
                .build();
        eventEntity.setCreatedBy(userId);
        eventEntity.setUpdatedBy(userId);
        applyAuditFields(eventEntity, userId);
        generationTaskEventEntityMapper.insertEvent(eventEntity);
        try {
            generationTaskStreamRegistry.publish(taskId,
                    publicGenerationStreamEventMapper.fromEntity(eventEntity),
                    eventType == GenerationTaskEventType.TASK_SUCCESS
                            || eventType == GenerationTaskEventType.TASK_FAILED
                            || eventType == GenerationTaskEventType.TASK_CANCELLED);
        } catch (Exception exception) {
            if (SseTransportFailures.isTransportFailure(exception)) {
                log.warn("Ignored SSE transport failure while publishing {} for task {}, event persisted",
                        eventType, taskId, exception);
                return;
            }
            throw exception;
        }
    }

    private record ResolvedGeneration(
            GeneratedProject project,
            GenerationSource source,
            boolean fallbackUsed,
            String providerCode,
            String modelName,
            String failureReason
    ) {
    }

    private record PersistedVersion(Long versionId, int versionNo) {
    }

    public record GenerationExecutionResult(
            GenerationSource generationSource,
            boolean fallbackUsed,
            String providerCode,
            String modelName,
            Long versionId,
            int fileCount,
            boolean success,
            String errorMessage
    ) {
    }
}
