package com.codeforge.ai.application.generation;

import com.codeforge.ai.application.generator.RuleBasedAppGenerator;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.GeneratedProjectQualityValidator;
import com.codeforge.ai.domain.generation.GeneratedProjectQualityValidator.ValidationResult;
import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEventEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CodeGenerationFacade {
    private static final Logger log = LoggerFactory.getLogger(CodeGenerationFacade.class);
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final GeneratedFileEntityMapper generatedFileEntityMapper;
    private final AiAppEntityMapper aiAppEntityMapper;
    private final GenerationTaskEventEntityMapper taskEventMapper;
    private final GeneratedProjectQualityValidator qualityValidator;
    private final CodeGenerationAiService aiService;
    private final RuleBasedAppGenerator ruleGenerator = new RuleBasedAppGenerator();

    @Transactional(rollbackFor = Exception.class)
    public GenerationResult generateAndSave(GenerationContext ctx) {
        // 1. Try AI generation
        GeneratedProject project = null;
        boolean usedAi = false;
        boolean qualityOk = true;
        String qualityFailMessage = null;

        try {
            project = aiService.generate(ctx);
            usedAi = true;
            log.info("AI generation succeeded for app {}", ctx.appId());

            // 2. Validate quality of AI output
            ValidationResult vr = qualityValidator.validate(project, ctx);
            if (!vr.passed()) {
                qualityFailMessage = vr.message();
                log.warn("AI quality check failed: {}", vr.message());

                // Retry AI once
                log.info("Retrying AI generation once...");
                try {
                    project = aiService.generate(ctx);
                    vr = qualityValidator.validate(project, ctx);
                    if (!vr.passed()) {
                        qualityFailMessage = vr.message();
                        log.warn("AI retry also failed quality: {}", vr.message());
                        qualityOk = false;
                    } else {
                        log.info("AI retry passed quality check");
                        qualityFailMessage = null;
                    }
                } catch (Exception retryEx) {
                    log.warn("AI retry generation failed: {}", retryEx.getMessage());
                    qualityOk = false;
                }
            }
        } catch (Exception e) {
            log.warn("AI generation failed, using rule fallback: {}", e.getMessage());
            qualityOk = false;
        }

        // 3. Fallback to rule generator if AI failed or quality was rejected
        if (!usedAi || !qualityOk) {
            RuleBasedAppGenerator.GeneratedProject raw = ruleGenerator.generate(
                    ctx.appName(), ctx.appType(), ctx.requirement());
            project = new GeneratedProject(
                    raw.appName() + " — " + summarize(raw.requirement()),
                    raw.appName(), raw.appType(), raw.requirement(),
                    raw.files().stream().map(f ->
                        new GeneratedProjectFile(f.filePath(), f.fileName(), f.content())).toList());

            // Validate rule fallback output too
            ValidationResult ruleVr = qualityValidator.validate(project, ctx);
            if (!ruleVr.passed()) {
                log.error("Rule fallback also failed quality check: {}", ruleVr.message());
                qualityFailMessage = ruleVr.message();
            } else {
                qualityFailMessage = null;
                qualityOk = true;
            }
        }

        // 4. Record quality failure event if any
        if (qualityFailMessage != null && ctx.taskId() != null) {
            recordQualityFailureEvent(ctx.taskId(), qualityFailMessage, ctx.userId());
        }

        // 5-8. Delegate to extracted save logic
        return saveProjectFilesInternal(project, ctx, qualityOk);
    }

    /**
     * Save project files as a new version (transactional).
     * <p>
     * This is the external entry point for streaming generation flows
     * (e.g. from {@link com.codeforge.ai.application.service.ChatGenerationApplicationService}).
     * The quality flag is always {@code true} for streaming since the user
     * sees the content in real-time.
     */
    @Transactional(rollbackFor = Exception.class)
    public GenerationResult saveProjectFiles(GeneratedProject project, GenerationContext ctx) {
        return saveProjectFilesInternal(project, ctx, true);
    }

    /**
     * Internal save logic shared by {@link #generateAndSave} and {@link #saveProjectFiles}.
     */
    private GenerationResult saveProjectFilesInternal(GeneratedProject project, GenerationContext ctx,
                                                       boolean qualityOk) {
        // 5. Validate basic structure (non-quality)
        validateProject(project);

        // 6. Save version
        int nextVersionNo = getNextVersionNo(ctx.appId());
        AppVersionEntity version = AppVersionEntity.builder()
                .appId(ctx.appId()).versionNo(nextVersionNo)
                .versionSource(qualityOk ? "FACADE_GENERATION" : "FACADE_GENERATION_QUALITY_WARN")
                .sourceTaskId(ctx.taskId())
                .changeSummary("根据需求自动生成：" + summarize(ctx.requirement()))
                .status(qualityOk ? "READY" : "QUALITY_WARN")
                .build();
        version.setCreatedBy(ctx.userId()); version.setUpdatedBy(ctx.userId());
        version.setCreatedAt(LocalDateTime.now()); version.setUpdatedAt(LocalDateTime.now());
        version.setIsDeleted(0);
        appVersionEntityMapper.insertVersion(version);

        // 7. Save files
        Path storageDir = Path.of(".local-storage", "apps", String.valueOf(ctx.appId()),
                "versions", String.valueOf(version.getId()));
        try { Files.createDirectories(storageDir); } catch (Exception e) { /* ignore */ }

        for (GeneratedProjectFile f : project.files()) {
            GeneratedFileEntity fe = GeneratedFileEntity.builder()
                    .appVersionId(version.getId()).filePath(f.filePath()).fileName(f.fileName())
                    .fileType(detectType(f.filePath())).fileContent(f.content())
                    .storagePath(storageDir.resolve(f.fileName()).toString())
                    .fileSize((long) f.content().length()).build();
            fe.setCreatedBy(ctx.userId()); fe.setUpdatedBy(ctx.userId());
            fe.setCreatedAt(LocalDateTime.now()); fe.setUpdatedAt(LocalDateTime.now());
            fe.setIsDeleted(0);
            generatedFileEntityMapper.insertFile(fe);
        }

        boolean hasIndexHtml = project.files().stream()
                .map(GeneratedProjectFile::filePath)
                .anyMatch("index.html"::equals);
        if (hasIndexHtml && !"VUE_PROJECT".equals(ctx.appType())) {
            appVersionEntityMapper.updatePreviewInfo(
                    version.getId(),
                    "/api/v1/static-preview/" + version.getId() + "/index.html",
                    "READY",
                    ctx.userId());
        }

        // 8. Update app — ONLY update currentVersionId if quality passed
        if (qualityOk) {
            aiAppEntityMapper.updateCurrentVersionId(ctx.appId(), version.getId(), ctx.userId());
        } else {
            log.warn("Quality check failed for version {}, not setting as latest version",
                    version.getId());
        }
        aiAppEntityMapper.updateStatus(ctx.appId(), "DEVELOPING", ctx.userId());

        return new GenerationResult(ctx.appId(), version.getId(), version.getVersionNo(),
                project.files().size(), project.files(), project.summary(), qualityOk);
    }

    private void recordQualityFailureEvent(Long taskId, String message, Long userId) {
        try {
            GenerationTaskEventEntity event = GenerationTaskEventEntity.builder()
                    .taskId(taskId)
                    .eventType("AI_OUTPUT_QUALITY_FAILED")
                    .eventMessage(message)
                    .build();
            event.setCreatedBy(userId); event.setUpdatedBy(userId);
            event.setCreatedAt(LocalDateTime.now()); event.setUpdatedAt(LocalDateTime.now());
            event.setIsDeleted(0);
            taskEventMapper.insertEvent(event);
            log.info("Recorded AI_OUTPUT_QUALITY_FAILED event for task {}", taskId);
        } catch (Exception e) {
            log.warn("Failed to record quality failure event: {}", e.getMessage());
        }
    }

    private void validateProject(GeneratedProject project) {
        if (project.files() == null || project.files().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "生成结果没有包含文件");
        }
        for (GeneratedProjectFile f : project.files()) {
            if (f.filePath() == null || f.filePath().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件路径不能为空");
            }
            if (f.content() == null || f.content().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件内容不能为空: " + f.filePath());
            }
            String normalized = f.filePath().replace("\\", "/");
            if (normalized.contains("../") || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法文件路径: " + f.filePath());
            }
        }
    }

    private int getNextVersionNo(Long appId) {
        Integer maxNo = appVersionEntityMapper.findMaxVersionNo(appId);
        return (maxNo == null ? 0 : maxNo) + 1;
    }

    private String summarize(String s) {
        if (s == null) return "";
        return s.length() <= 100 ? s : s.substring(0, 100);
    }

    private String detectType(String path) {
        if (path == null) return "other";
        String l = path.toLowerCase();
        if (l.endsWith(".md")) return "markdown";
        if (l.endsWith(".vue")) return "vue";
        if (l.endsWith(".ts")) return "typescript";
        if (l.endsWith(".js")) return "javascript";
        if (l.endsWith(".json")) return "json";
        if (l.endsWith(".css")) return "css";
        if (l.endsWith(".html")) return "html";
        return "other";
    }

    public record GenerationResult(Long appId, Long versionId, int versionNo, int fileCount,
                                   List<GeneratedProjectFile> files, String summary, boolean qualityOk) {}
}
