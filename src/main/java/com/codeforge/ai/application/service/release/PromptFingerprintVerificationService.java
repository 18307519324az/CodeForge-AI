package com.codeforge.ai.application.service.release;

import com.codeforge.ai.application.generation.AiCodegenPromptBuilder;
import com.codeforge.ai.application.task.GenerationTaskRequestPayloadSupport;
import com.codeforge.ai.application.task.GenerationTaskRequestPayloadSupport.ParsedPayload;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.ModelCallPhase;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.model.PromptFingerprintHasher;
import com.codeforge.ai.domain.prompt.model.PromptFingerprintHasher.PromptFingerprint;
import com.codeforge.ai.domain.prompt.model.PromptTemplateExecutionResolver;
import com.codeforge.ai.domain.prompt.model.ResolvedGenerationPrompt;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromptFingerprintVerificationService {

    private final GenerationTaskEntityMapper generationTaskEntityMapper;
    private final ModelCallLogEntityMapper modelCallLogEntityMapper;
    private final AiAppEntityMapper aiAppEntityMapper;
    private final PromptTemplateExecutionResolver promptTemplateExecutionResolver;
    private final PromptTemplateEntityMapper promptTemplateEntityMapper;
    private final PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private final PromptResourceLoader promptResourceLoader;
    private final ObjectMapper objectMapper;

    public PromptFingerprintVerificationResult verify(Long taskId, Long modelCallId) {
        GenerationTaskEntity task = requireTask(taskId);
        ModelCallLogEntity modelCall = resolveModelCall(taskId, modelCallId);
        GenerationContext context = buildGenerationContext(task);
        ModelCallPhase phase = resolvePhase(modelCall.getGenerationSource());
        String systemPrompt = resolveSystemPrompt(context);
        List<ModelMessage> messages = buildMessagesForPhase(phase, systemPrompt, context);
        PromptFingerprint expectedPinned = PromptFingerprintHasher.fromMessages(messages);

        FingerprintMatch pinnedMatch = compare(expectedPinned, modelCall);
        FingerprintMatch latestMatch = compareLatestVersion(task, context, phase, systemPrompt, modelCall);

        return new PromptFingerprintVerificationResult(
                taskId,
                modelCall.getId(),
                modelCall.getGenerationSource(),
                phase.name(),
                modelCall.getPromptTemplateVersionId(),
                pinnedMatch.systemHashMatches(),
                pinnedMatch.userHashMatches(),
                pinnedMatch.combinedMatches(),
                pinnedMatch.allMatch(),
                latestMatch.systemHashMatches(),
                latestMatch.userHashMatches(),
                latestMatch.combinedMatches(),
                latestMatch.allMatch());
    }

    public List<ModelCallSummary> listModelCalls(Long taskId) {
        return modelCallLogEntityMapper.findByTaskId(taskId).stream()
                .sorted(Comparator.comparing(ModelCallLogEntity::getId))
                .map(call -> new ModelCallSummary(
                        call.getId(),
                        call.getGenerationSource(),
                        call.getStatus(),
                        call.getPromptTemplateVersionId(),
                        hasFingerprint(call)))
                .toList();
    }

    private GenerationTaskEntity requireTask(Long taskId) {
        GenerationTaskEntity task = generationTaskEntityMapper.selectOneById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "生成任务不存在");
        }
        return task;
    }

    private ModelCallLogEntity resolveModelCall(Long taskId, Long modelCallId) {
        if (modelCallId != null) {
            ModelCallLogEntity modelCall = modelCallLogEntityMapper.findById(modelCallId);
            if (modelCall == null || !taskId.equals(modelCall.getTaskId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "模型调用记录不存在");
            }
            return modelCall;
        }
        List<ModelCallLogEntity> calls = modelCallLogEntityMapper.findByTaskId(taskId);
        if (calls.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务没有模型调用记录");
        }
        return calls.stream()
                .filter(this::hasFingerprint)
                .filter(call -> ModelCallPhase.INITIAL.generationSourceCode().equals(call.getGenerationSource()))
                .findFirst()
                .orElseGet(() -> calls.stream()
                        .filter(this::hasFingerprint)
                        .max(Comparator.comparing(ModelCallLogEntity::getId))
                        .orElse(calls.getLast()));
    }

    private boolean hasFingerprint(ModelCallLogEntity call) {
        return call.getCombinedPromptFingerprint() != null && !call.getCombinedPromptFingerprint().isBlank();
    }

    private GenerationContext buildGenerationContext(GenerationTaskEntity task) {
        AiAppEntity app = aiAppEntityMapper.selectOneById(task.getAppId());
        if (app == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND, "应用不存在");
        }
        ParsedPayload payload = GenerationTaskRequestPayloadSupport.parse(objectMapper, task.getRequestPayloadJson());
        String requirement = payload.requirement() != null ? payload.requirement() : task.getRequestPayloadJson();
        Long templateId = task.getPromptTemplateId() != null ? task.getPromptTemplateId() : payload.promptTemplateId();
        Long versionId = task.getPromptTemplateVersionId() != null
                ? task.getPromptTemplateVersionId()
                : payload.promptTemplateVersionId();
        String codeGenType = resolveCodeGenType(app.getAppType());
        Optional<ResolvedGenerationPrompt> resolvedPrompt = promptTemplateExecutionResolver.resolveOptional(
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
                    task.getCreatedBy(),
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
                task.getCreatedBy(),
                task.getId(),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private String resolveCodeGenType(String appType) {
        if ("WEB_APP".equalsIgnoreCase(appType)) {
            return "HTML";
        }
        return appType != null ? appType : "HTML";
    }

    private String resolveSystemPrompt(GenerationContext context) {
        if (context.usesTemplatePrompt()) {
            return context.systemPrompt();
        }
        return promptResourceLoader.load(resolvePromptFileName(context.codeGenType()));
    }

    private String resolvePromptFileName(String codeGenType) {
        if ("MULTI_FILE".equalsIgnoreCase(codeGenType)) {
            return "codegen-multi-file-system-prompt.txt";
        }
        if ("VUE_PROJECT".equalsIgnoreCase(codeGenType)) {
            return "codegen-vue-project-system-prompt.txt";
        }
        return "codegen-html-system-prompt.txt";
    }

    private ModelCallPhase resolvePhase(String generationSource) {
        if (generationSource == null || generationSource.isBlank()) {
            return ModelCallPhase.INITIAL;
        }
        for (ModelCallPhase phase : ModelCallPhase.values()) {
            if (phase.generationSourceCode().equals(generationSource)) {
                return phase;
            }
        }
        if ("AI_DIRECT".equals(generationSource)) {
            return ModelCallPhase.INITIAL;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "无法识别模型调用阶段: " + generationSource);
    }

    private List<ModelMessage> buildMessagesForPhase(ModelCallPhase phase,
                                                     String systemPrompt,
                                                     GenerationContext context) {
        return switch (phase) {
            case INITIAL -> AiCodegenPromptBuilder.buildInitialMessages(systemPrompt, context);
            case PARSE_RETRY -> AiCodegenPromptBuilder.buildRetryMessages(systemPrompt, context);
            case COMPACT_RETRY -> AiCodegenPromptBuilder.buildCompactMessages(systemPrompt, context);
            case REPAIR -> AiCodegenPromptBuilder.buildArtifactRepairMessages(
                    systemPrompt, context, "artifact validation failed");
        };
    }

    private FingerprintMatch compare(PromptFingerprint expected, ModelCallLogEntity actual) {
        return new FingerprintMatch(
                safeEquals(expected.systemSha256(), actual.getSystemPromptSha256()),
                safeEquals(expected.userSha256(), actual.getUserPromptSha256()),
                safeEquals(expected.combined(), actual.getCombinedPromptFingerprint()));
    }

    private FingerprintMatch compareLatestVersion(GenerationTaskEntity task,
                                                  GenerationContext pinnedContext,
                                                  ModelCallPhase phase,
                                                  String pinnedSystemPrompt,
                                                  ModelCallLogEntity modelCall) {
        Long templateId = task.getPromptTemplateId();
        if (templateId == null) {
            return FingerprintMatch.noMatch();
        }
        PromptTemplateEntity template = promptTemplateEntityMapper.selectOneById(templateId);
        if (template == null || template.getCurrentVersionNo() == null) {
            return FingerprintMatch.noMatch();
        }
        PromptTemplateVersionEntity latestVersion = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(
                templateId, template.getCurrentVersionNo());
        if (latestVersion == null) {
            return FingerprintMatch.noMatch();
        }
        if (latestVersion.getId().equals(pinnedContext.promptTemplateVersionId())) {
            return FingerprintMatch.noMatch();
        }
        ParsedPayload payload = GenerationTaskRequestPayloadSupport.parse(objectMapper, task.getRequestPayloadJson());
        ResolvedGenerationPrompt latestResolved = promptTemplateExecutionResolver.resolvePinned(
                templateId,
                latestVersion.getId(),
                pinnedContext.requirement(),
                payload.templateVariables());
        GenerationContext latestContext = new GenerationContext(
                pinnedContext.requirement(),
                pinnedContext.appName(),
                pinnedContext.appType(),
                pinnedContext.codeGenType(),
                pinnedContext.appId(),
                pinnedContext.userId(),
                pinnedContext.taskId(),
                null,
                null,
                null,
                null,
                null,
                latestResolved.renderedSystemPrompt(),
                latestResolved.renderedUserPrompt(),
                latestResolved.templateId(),
                latestResolved.templateVersionId(),
                latestResolved.templateCode(),
                latestResolved.versionNo());
        String latestSystemPrompt = latestContext.systemPrompt();
        List<ModelMessage> latestMessages = buildMessagesForPhase(phase, latestSystemPrompt, latestContext);
        PromptFingerprint expectedLatest = PromptFingerprintHasher.fromMessages(latestMessages);
        return compare(expectedLatest, modelCall);
    }

    private boolean safeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    public record ModelCallSummary(
            Long id,
            String generationSource,
            String status,
            Long promptTemplateVersionId,
            boolean fingerprintPresent
    ) {
    }

    public record FingerprintMatch(
            boolean systemHashMatches,
            boolean userHashMatches,
            boolean combinedMatches
    ) {
        boolean allMatch() {
            return systemHashMatches && userHashMatches && combinedMatches;
        }

        static FingerprintMatch noMatch() {
            return new FingerprintMatch(false, false, false);
        }
    }

    public record PromptFingerprintVerificationResult(
            Long taskId,
            Long modelCallId,
            String generationSource,
            String attemptPhase,
            Long pinnedTemplateVersionId,
            boolean systemHashMatches,
            boolean userHashMatches,
            boolean combinedMatches,
            boolean matchesPinnedVersion,
            boolean latestSystemHashMatches,
            boolean latestUserHashMatches,
            boolean latestCombinedMatches,
            boolean matchesLatestVersion
    ) {
    }
}
