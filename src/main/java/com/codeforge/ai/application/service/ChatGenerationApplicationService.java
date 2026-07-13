package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.chat.ChatSessionResponse;
import com.codeforge.ai.application.generation.CodeGenerationFacade;
import com.codeforge.ai.application.generation.CodeGenerationFacade.GenerationResult;
import com.codeforge.ai.application.generator.RuleBasedAppGenerator;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.generation.CodeGenTypeEnum;
import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.ModelStreamHandler;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelGatewayInvoker;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser;
import com.codeforge.ai.domain.task.entity.GenerationRecordEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEventEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Application service for AI streaming chat generation.
 * <p>
 * Owns the complete business flow:
 * <ol>
 *   <li>Session creation with concurrency limits</li>
 *   <li>Streaming generation with SSE events (start → status → delta* → file* → done/error)</li>
 *   <li>Cancellation (interrupt stream, mark task CANCELLED, no version saved)</li>
 * </ol>
 * The controller layer is thin — it only validates auth and delegates here.
 */
@Service
public class ChatGenerationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatGenerationApplicationService.class);
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    private final AiAppEntityMapper aiAppMapper;
    private final GenerationTaskEntityMapper taskMapper;
    private final GenerationRecordEntityMapper generationRecordMapper;
    private final GenerationTaskEventEntityMapper taskEventMapper;
    private final GenerationMessageApplicationService messageService;
    private final ModelGatewayInvoker invoker;
    private final AiGeneratedProjectParser parser;
    private final CodeGenerationFacade codeGenerationFacade;
    private final StreamSessionRegistry registry;
    private final StreamTokenManager tokenManager;
    private final Executor streamingExecutor;
    private final RuleBasedAppGenerator ruleGenerator = new RuleBasedAppGenerator();
    private final PromptResourceLoader promptLoader;

    public ChatGenerationApplicationService(
            AiAppEntityMapper aiAppMapper,
            GenerationTaskEntityMapper taskMapper,
            GenerationRecordEntityMapper generationRecordMapper,
            GenerationTaskEventEntityMapper taskEventMapper,
            GenerationMessageApplicationService messageService,
            ModelGatewayInvoker invoker,
            AiGeneratedProjectParser parser,
            CodeGenerationFacade codeGenerationFacade,
            StreamSessionRegistry registry,
            StreamTokenManager tokenManager,
            @Qualifier("streamingTaskExecutor") Executor streamingExecutor,
            PromptResourceLoader promptLoader) {
        this.aiAppMapper = aiAppMapper;
        this.taskMapper = taskMapper;
        this.generationRecordMapper = generationRecordMapper;
        this.taskEventMapper = taskEventMapper;
        this.messageService = messageService;
        this.invoker = invoker;
        this.parser = parser;
        this.codeGenerationFacade = codeGenerationFacade;
        this.registry = registry;
        this.tokenManager = tokenManager;
        this.streamingExecutor = streamingExecutor;
        this.promptLoader = promptLoader;
    }

    // ── Session lifecycle ──

    /**
     * Create a streaming session: validate app, check concurrency limits,
     * save user message, persist task, generate one-time stream token.
     */
    public ChatSessionResponse createSession(Long appId, Long userId, String message) {
        AiAppEntity app = aiAppMapper.selectOneById(appId);
        if (app == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }

        // Register session with concurrency limits
        long sessionId = registry.tryCreate(appId, userId, message);

        // Save user message (taskId is null at this point — updated later if needed)
        messageService.saveMessage(app.getWorkspaceId(), appId, null, userId, "USER", message);

        // Create generation task in QUEUED state
        GenerationTaskEntity task = GenerationTaskEntity.builder()
                .workspaceId(app.getWorkspaceId())
                .appId(appId)
                .taskType("CHAT_GENERATION")
                .taskStatus(GenerationTaskStatus.QUEUED.name())
                .retryCount(0)
                .requirement(message)
                .queuedAt(LocalDateTime.now())
                .build();
        task.setCreatedBy(userId);
        task.setUpdatedBy(userId);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setIsDeleted(0);
        taskMapper.insertTask(task);

        // Create generation record in RUNNING state
        GenerationRecordEntity record = GenerationRecordEntity.builder()
                .workspaceId(app.getWorkspaceId())
                .appId(appId)
                .taskId(task.getId())
                .status(GenerationTaskStatus.RUNNING.name())
                .inputSummary(truncate(message, 200))
                .tokenInput(0)
                .tokenOutput(0)
                .durationMs(0L)
                .build();
        record.setCreatedBy(userId);
        record.setUpdatedBy(userId);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setIsDeleted(0);
        generationRecordMapper.insertRecord(record);

        // Associate task with session
        registry.setTaskId(sessionId, task.getId());

        // Generate one-time stream token (5 min expiry)
        String streamToken = tokenManager.createToken(sessionId, task.getId(), appId);

        log.info("Chat session created: sessionId={}, taskId={}, appId={}", sessionId, task.getId(), appId);
        return new ChatSessionResponse(sessionId, task.getId(), streamToken, 300);
    }

    /**
     * Start streaming AI generation via SSE.
     * <p>
     * Validates the one-time stream token, creates an {@link SseEmitter},
     * and submits the streaming work to a bounded executor. The emitter
     * is returned immediately; events are pushed as the model responds.
     * <p>
     * Events sent: {@code start}, {@code status}, {@code delta}*, {@code file}*,
     * {@code done} / {@code error}.
     */
    public SseEmitter startStreaming(Long appId, Long taskId, String streamToken) {
        // 1. Validate one-time stream token
        StreamTokenManager.TokenInfo tokenInfo = tokenManager.consume(streamToken);
        if (tokenInfo == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "streamToken 无效或已过期");
        }
        Long sessionId = tokenInfo.sessionId();

        // 2. Look up session metadata
        StreamSessionRegistry.SessionInfo sessionInfo = registry.getSessionInfo(sessionId);
        if (sessionInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        Long userId = sessionInfo.userId();
        String requirement = sessionInfo.requirement();

        // 3. Look up app
        AiAppEntity app = aiAppMapper.selectOneById(appId);
        if (app == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }

        // 4. Create SseEmitter (5 min timeout)
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        AtomicBoolean terminalDispatched = new AtomicBoolean(false);

        // 5. Submit streaming work to bounded executor
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                // ---- Phase 1: Start & Analyze ----
                sendJsonEvent(emitter, "start",
                        "{\"sessionId\":" + sessionId + ",\"taskId\":" + taskId + "}");
                sendJsonEvent(emitter, "status",
                        "{\"stage\":\"MODEL_CALLING\",\"message\":\"正在理解需求\"}");

                // Transition task from QUEUED to RUNNING
                taskMapper.transitionState(taskId,
                        GenerationTaskStatus.QUEUED.name(),
                        GenerationTaskStatus.RUNNING.name(),
                        LocalDateTime.now(), null, null, null, null, userId);

                // Build generation context with app-type-specific system prompt
                String promptFile = getPromptFileForAppType(app.getAppType());
                String systemPrompt = promptLoader.load(promptFile);
                GenerationContext ctx = new GenerationContext(
                        requirement, app.getName(), app.getAppType(),
                        CodeGenTypeEnum.fromAppType(app.getAppType()).name(),
                        appId, userId, taskId, sessionId, "auto", null, null, null,
                        systemPrompt);

                sendJsonEvent(emitter, "status",
                        "{\"stage\":\"MODEL_STREAMING\",\"message\":\"正在调用 AI 模型\"}");

                // ---- Phase 2: Stream from model ----
                StringBuilder fullContent = new StringBuilder();

                invoker.streamWithFallback(ctx, new ModelStreamHandler() {
                    @Override
                    public void onStart() {
                        // Gateway already handled this
                    }

                    @Override
                    public void onDelta(String delta) {
                        fullContent.append(delta);
                        sendJsonEvent(emitter, "delta",
                                "{\"content\":" + jsonEscape(delta) + "}");
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!terminalDispatched.compareAndSet(false, true)) return;

                        String msg = error.getMessage() != null ? error.getMessage() : "生成失败";
                        sendEnhancedErrorEvent(emitter, "MODEL_STREAMING",
                                "STREAM_ERROR", msg, msg, taskId, appId);
                        safeCompleteWithError(emitter, error);

                        // Mark task as FAILED or CANCELLED based on error type
                        String taskStatus = isCancellationError(error)
                                ? GenerationTaskStatus.CANCELLED.name()
                                : GenerationTaskStatus.FAILED.name();
                        taskMapper.updateTerminalState(taskId, taskStatus,
                                isCancellationError(error) ? null : "STREAM_ERROR",
                                truncate(msg, 200), LocalDateTime.now(), userId);

                        // Sync generation record status
                        generationRecordMapper.updateResultByTaskId(taskId, taskStatus,
                                truncate(msg, 200), 0L, userId);

                        // Write task failure event
                        writeTaskFailedEvent(taskId, "STREAM_ERROR", msg, userId);
                    }

                    @Override
                    public void onComplete(ModelChatResult result) {
                        if (!terminalDispatched.compareAndSet(false, true)) return;

                        try {
                            sendJsonEvent(emitter, "status",
                                    "{\"stage\":\"PARSING\",\"message\":\"正在解析 AI 输出\"}");

                            // Try to parse AI output into project structure
                            GeneratedProject project;
                            try {
                                project = parser.parse(result.content(),
                                        ctx.codeGenType());
                            } catch (Exception parseEx) {
                                // AI parsing failed — fall back to RuleBased generator
                                log.warn("AI parsing failed for task {}, falling back to RuleBased: {}",
                                        taskId, parseEx.getMessage());
                                sendJsonEvent(emitter, "status",
                                        "{\"stage\":\"FALLBACK\",\"message\":\"AI 输出解析失败，正在使用规则引擎生成\"}");

                                project = generateFallbackProject(app, requirement);
                                if (project == null) {
                                    // Fallback also failed — rethrow original
                                    throw parseEx;
                                }
                                // Record fallback event
                                writeTaskFailedEvent(taskId, "AI_OUTPUT_PARSE_FALLBACK",
                                        "AI 输出解析失败，已使用规则引擎生成: "
                                        + truncate(parseEx.getMessage(), 100), userId);
                            }

                            sendJsonEvent(emitter, "status",
                                    "{\"stage\":\"SAVING_FILES\",\"message\":\"正在保存源码文件\"}");

                            // Save project files as a new version
                            GenerationResult genResult = codeGenerationFacade
                                    .saveProjectFiles(project, ctx);

                            // Send file events
                            for (GeneratedProjectFile f : project.files()) {
                                sendJsonEvent(emitter, "file",
                                        "{\"filePath\":\"" + jsonEscape(f.filePath()) + "\"}");
                            }

                            // Save assistant message with full AI content
                            messageService.saveMessage(app.getWorkspaceId(), appId, taskId,
                                    userId, "ASSISTANT", result.content());

                            // Mark task SUCCESS
                            taskMapper.updateTerminalState(taskId,
                                    GenerationTaskStatus.SUCCESS.name(),
                                    null, null, LocalDateTime.now(), userId);

                            // Sync generation record to SUCCESS
                            String fileSummary = "生成 " + project.files().size() + " 个文件";
                            generationRecordMapper.updateResultByTaskId(taskId,
                                    GenerationTaskStatus.SUCCESS.name(),
                                    fileSummary, 0L, userId);

                            // Send done event with version info
                            String previewUrl = "/api/v1/static-preview/"
                                    + genResult.versionId() + "/index.html";
                            String downloadUrl = "/api/v1/apps/" + appId
                                    + "/versions/" + genResult.versionId() + "/download";
                            sendJsonEvent(emitter, "done",
                                    "{\"versionId\":" + genResult.versionId()
                                    + ",\"versionNo\":" + genResult.versionNo()
                                    + ",\"previewUrl\":\"" + jsonEscape(previewUrl) + "\""
                                    + ",\"downloadUrl\":\"" + jsonEscape(downloadUrl) + "\"}");

                            emitter.complete();
                            log.info("Stream completed successfully: taskId={}, versionId={}",
                                    taskId, genResult.versionId());

                        } catch (Exception e) {
                            log.error("Failed to save streaming result for task {}", taskId, e);

                            // Both AI and fallback failed — send enhanced error event
                            String stage = isParseException(e) ? "PARSING" : "SAVING_FILES";
                            String errorCode = isParseException(e) ? "AI_OUTPUT_PARSE_FAILED" : "SAVE_ERROR";
                            sendEnhancedErrorEvent(emitter, stage, errorCode,
                                    "保存生成结果失败", e.getMessage(), taskId, appId);
                            safeCompleteWithError(emitter, e);
                            taskMapper.updateTerminalState(taskId,
                                    GenerationTaskStatus.FAILED.name(), errorCode,
                                    truncate(e.getMessage(), 200), LocalDateTime.now(), userId);
                            generationRecordMapper.updateResultByTaskId(taskId,
                                    GenerationTaskStatus.FAILED.name(),
                                    truncate("保存生成结果失败: " + e.getMessage(), 200),
                                    0L, userId);
                            writeTaskFailedEvent(taskId, errorCode, e.getMessage(), userId);
                        }
                    }
                });

            } catch (Exception e) {
                if (terminalDispatched.compareAndSet(false, true)) {
                    String msg = e.getMessage() != null ? e.getMessage() : "流式生成异常";
                    sendEnhancedErrorEvent(emitter, "FAILED",
                            "STREAM_EXCEPTION", msg, msg, taskId, appId);
                    safeCompleteWithError(emitter, e);
                    taskMapper.updateTerminalState(taskId,
                            GenerationTaskStatus.FAILED.name(), "STREAM_EXCEPTION",
                            truncate(msg, 200), LocalDateTime.now(), userId);
                    generationRecordMapper.updateResultByTaskId(taskId,
                            GenerationTaskStatus.FAILED.name(),
                            truncate(msg, 200), 0L, userId);
                    writeTaskFailedEvent(taskId, "STREAM_EXCEPTION", msg, userId);
                }
            } finally {
                registry.cleanup(sessionId);
            }
        }, streamingExecutor);

        // 6. Register future for cancellation
        registry.registerFuture(sessionId, future);

        // 7. Emitter lifecycle callbacks
        emitter.onCompletion(() -> registry.cleanup(sessionId));
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for session {}", sessionId);
            registry.cleanup(sessionId);
            future.cancel(true);
        });
        emitter.onError(e -> {
            log.warn("SSE error for session {}: {}", sessionId, e.getMessage());
            registry.cleanup(sessionId);
        });

        // 8. Update app's latest task reference
        aiAppMapper.updateLatestTaskId(appId, taskId, userId);

        return emitter;
    }

    /**
     * Cancel a streaming session.
     * <p>
     * Interrupts the model HTTP request via {@link CompletableFuture#cancel(boolean)},
     * cleans up the session registry, and marks the task as CANCELLED (if still active).
     * No version or files are saved.
     */
    public void cancelSession(Long appId, Long sessionId, Long userId) {
        StreamSessionRegistry.SessionInfo info = registry.getSessionInfo(sessionId);
        if (info == null) {
            log.warn("Cancel called for unknown session: appId={}, sessionId={}", appId, sessionId);
            return;
        }

        Long taskId = info.taskId();

        // Cancel the future — this interrupts the streaming thread
        registry.cancel(sessionId);
        log.info("Session cancelled: sessionId={}, appId={}, taskId={}", sessionId, appId, taskId);

        // Update task status to CANCELLED (safe SQL: only if not already in terminal state)
        if (taskId != null) {
            taskMapper.cancelIfActive(taskId, LocalDateTime.now(), userId);

            // Sync generation record to CANCELLED
            generationRecordMapper.updateResultByTaskId(taskId,
                    GenerationTaskStatus.CANCELLED.name(),
                    "用户取消生成", 0L, userId);
        }
    }

    // ── Fallback & error helpers ──

    /**
     * Select the appropriate system prompt file based on the application type.
     */
    private static String getPromptFileForAppType(String appType) {
        if (appType == null) return "codegen-html-system-prompt.txt";
        return switch (appType.toUpperCase()) {
            case "VUE_PROJECT" -> "codegen-vue-project-system-prompt.txt";
            case "MULTI_FILE" -> "codegen-multi-file-system-prompt.txt";
            default -> "codegen-html-system-prompt.txt";  // WEB_APP, HTML, BLOG, etc.
        };
    }

    /**
     * Generate project files using RuleBased generator as fallback.
     * Returns null if rule generation also fails.
     */
    private GeneratedProject generateFallbackProject(AiAppEntity app, String requirement) {
        try {
            var raw = ruleGenerator.generate(app.getName(), app.getAppType(), requirement);
            String summary = raw.appName() + " — " + truncate(raw.requirement(), 100);
            return new GeneratedProject(summary, raw.appName(), raw.appType(), raw.requirement(),
                    raw.files().stream().map(f ->
                        new GeneratedProjectFile(f.filePath(), f.fileName(), f.content())).toList());
        } catch (Exception e) {
            log.error("RuleBased fallback generation failed for app {}", app.getId(), e);
            return null;
        }
    }

    /**
     * Complete streaming successfully with fallback-generated project.
     */
    private void completeWithFallback(SseEmitter emitter, GeneratedProject project,
                                       Long taskId, Long appId, Long userId,
                                       AiAppEntity app, String requirement,
                                       GenerationContext ctx) throws IOException {
        GenerationResult genResult = codeGenerationFacade.saveProjectFiles(project, ctx);
        for (GeneratedProjectFile f : project.files()) {
            sendJsonEvent(emitter, "file",
                    "{\"filePath\":\"" + jsonEscape(f.filePath()) + "\"}");
        }
        messageService.saveMessage(app.getWorkspaceId(), appId, taskId,
                userId, "ASSISTANT", "【规则引擎生成】" + requirement);
        taskMapper.updateTerminalState(taskId,
                GenerationTaskStatus.SUCCESS.name(),
                null, null, LocalDateTime.now(), userId);
        String fileSummary = "生成 " + project.files().size() + " 个文件";
        generationRecordMapper.updateResultByTaskId(taskId,
                GenerationTaskStatus.SUCCESS.name(),
                fileSummary, 0L, userId);
        String previewUrl = "/api/v1/static-preview/"
                + genResult.versionId() + "/index.html";
        String downloadUrl = "/api/v1/apps/" + appId
                + "/versions/" + genResult.versionId() + "/download";
        sendJsonEvent(emitter, "done",
                "{\"versionId\":" + genResult.versionId()
                + ",\"versionNo\":" + genResult.versionNo()
                + ",\"previewUrl\":\"" + jsonEscape(previewUrl) + "\""
                + ",\"downloadUrl\":\"" + jsonEscape(downloadUrl) + "\"}");
        emitter.complete();
        log.info("Fallback stream completed: taskId={}, versionId={}",
                taskId, genResult.versionId());
    }

    /**
     * Send an enhanced SSE error event with structured fields.
     */
    private void sendEnhancedErrorEvent(SseEmitter emitter, String stage, String errorCode,
                                         String message, String detail, Long taskId, Long appId) {
        String json = "{\"stage\":\"" + jsonEscape(stage)
                + "\",\"errorCode\":\"" + jsonEscape(errorCode)
                + "\",\"message\":\"" + jsonEscape(message)
                + "\",\"detail\":\"" + jsonEscape(truncate(detail, 500))
                + "\",\"taskId\":" + taskId
                + ",\"appId\":" + appId
                + ",\"timestamp\":\"" + LocalDateTime.now() + "\"}";
        sendJsonEvent(emitter, "error", json);
    }

    /**
     * Write a TASK_FAILED event to generation_task_event table.
     */
    private void writeTaskFailedEvent(Long taskId, String errorCode, String message, Long userId) {
        try {
            GenerationTaskEventEntity event = GenerationTaskEventEntity.builder()
                    .taskId(taskId)
                    .eventType("TASK_FAILED")
                    .eventMessage(errorCode + ": " + truncate(message, 200))
                    .eventPayloadJson("{\"errorCode\":\"" + jsonEscape(errorCode) + "\"}")
                    .build();
            event.setCreatedBy(userId);
            event.setUpdatedBy(userId);
            event.setCreatedAt(LocalDateTime.now());
            event.setUpdatedAt(LocalDateTime.now());
            event.setIsDeleted(0);
            taskEventMapper.insertEvent(event);
            log.info("Recorded TASK_FAILED event for task {}: {}", taskId, errorCode);
        } catch (Exception e) {
            log.warn("Failed to record task failure event: {}", e.getMessage());
        }
    }

    /**
     * Check if an exception is an AI output parse exception.
     */
    private static boolean isParseException(Throwable e) {
        if (e == null) return false;
        String cls = e.getClass().getName();
        return cls.contains("AiOutputParseException")
                || cls.contains("JsonParseException")
                || cls.contains("JsonMappingException");
    }

    // ── SSE helpers ──

    private void sendJsonEvent(SseEmitter emitter, String eventName, String jsonData) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(jsonData, org.springframework.http.MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            // Emitter is closed/done — this is expected during normal completion or cancel
            log.debug("Failed to send SSE event '{}': {}", eventName, e.getMessage());
        }
    }

    private void safeCompleteWithError(SseEmitter emitter, Throwable error) {
        try {
            emitter.completeWithError(error);
        } catch (Exception e) {
            // Already completed — ignore
        }
    }

    // ── Utility methods ──

    /**
     * JSON-escape a string value for embedding in JSON.
     * Handles control characters, quotes, and backslashes.
     */
    static String jsonEscape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static boolean isCancellationError(Throwable error) {
        if (error == null || error.getMessage() == null) return false;
        String msg = error.getMessage();
        return msg.contains("取消") || msg.contains("中断")
                || msg.contains("cancel") || msg.contains("interrupt")
                || msg.contains("Cancel") || msg.contains("Interrupt");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
