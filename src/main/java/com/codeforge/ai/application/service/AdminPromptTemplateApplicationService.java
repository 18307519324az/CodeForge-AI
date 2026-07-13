package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.PromptTemplateTestRunRequest;
import com.codeforge.ai.application.dto.admin.PromptTemplateTestRunResponse;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.model.ProviderErrorSanitizer;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.model.PromptFingerprintHasher;
import com.codeforge.ai.domain.prompt.model.PromptTemplateRenderer;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTrace;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTraceResolver;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.response.ResultUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPromptTemplateApplicationService {

    private static final int OUTPUT_PREVIEW_MAX_LENGTH = 200;
    private static final String GENERATION_SOURCE_TEST_RUN = "PROMPT_TEST_RUN";

    private final PromptTemplateEntityMapper promptTemplateEntityMapper;
    private final PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private final ModelCallLogEntityMapper modelCallLogEntityMapper;
    private final AuditLogWriter auditLogWriter;
    private final ModelProviderSelector modelProviderSelector;
    private final PromptTemplateTraceResolver promptTemplateTraceResolver;
    private final ObjectMapper objectMapper;

    @Transactional
    public PromptTemplateTestRunResponse testRunTemplate(CurrentUser currentUser,
                                                         Long templateId,
                                                         PromptTemplateTestRunRequest request) {
        requirePlatformAdmin(currentUser);
        PromptTemplateEntity templateEntity = promptTemplateEntityMapper.selectOneById(templateId);
        if (templateEntity == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        PromptTemplateVersionEntity versionEntity = resolveTestRunVersion(templateEntity, request.getVersionNo());
        PromptTemplateRenderer.validateRequiredVariables(
                versionEntity.getSystemPrompt(),
                versionEntity.getUserPrompt(),
                request.getMockVariables());

        long start = System.currentTimeMillis();
        String renderedSystemPrompt = PromptTemplateRenderer.render(versionEntity.getSystemPrompt(), request.getMockVariables());
        String renderedUserPrompt = PromptTemplateRenderer.render(versionEntity.getUserPrompt(), request.getMockVariables());
        String outputPreview = buildTestRunPreview(renderedUserPrompt);
        long latencyMs = System.currentTimeMillis() - start;

        PromptTemplateTrace trace = promptTemplateTraceResolver.resolveByVersionEntity(versionEntity, templateEntity);
        var fingerprint = PromptFingerprintHasher.hash(renderedSystemPrompt, renderedUserPrompt);
        ModelProviderEntity provider = resolveTestRunProvider();
        writeTestRunCallLog(currentUser, provider, trace, fingerprint, latencyMs);
        auditLogWriter.insert(buildTestRunAuditLog(currentUser.requiredUserId(), templateEntity, trace));

        return new PromptTemplateTestRunResponse(
                templateEntity.getId(),
                trace.promptTemplateVersionId(),
                trace.promptTemplateCode(),
                trace.promptTemplateVersionNo(),
                true,
                outputPreview,
                latencyMs
        );
    }

    private PromptTemplateVersionEntity resolveTestRunVersion(PromptTemplateEntity templateEntity, Integer versionNo) {
        Integer resolvedVersionNo = versionNo != null ? versionNo : templateEntity.getCurrentVersionNo();
        if (resolvedVersionNo == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模板尚无可用版本");
        }
        PromptTemplateVersionEntity versionEntity = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(
                templateEntity.getId(), resolvedVersionNo);
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "Prompt 模板版本不存在");
        }
        return versionEntity;
    }

    private ModelProviderEntity resolveTestRunProvider() {
        ModelProviderEntity ruleProvider = modelProviderSelector.selectRuleProvider();
        if (ruleProvider != null) {
            return ruleProvider;
        }
        return ModelProviderEntity.builder()
                .id(0L)
                .providerCode("test-run")
                .providerName("Prompt Test Run")
                .apiProtocol("TEST_RUN")
                .defaultModel("mock")
                .build();
    }

    private String buildTestRunPreview(String renderedUserPrompt) {
        String sanitized = ProviderErrorSanitizer.sanitize(renderedUserPrompt);
        return PromptTemplateRenderer.truncatePreview(sanitized, OUTPUT_PREVIEW_MAX_LENGTH);
    }

    private void writeTestRunCallLog(CurrentUser currentUser,
                                       ModelProviderEntity provider,
                                       PromptTemplateTrace trace,
                                       PromptFingerprintHasher.PromptFingerprint fingerprint,
                                       long latencyMs) {
        ModelCallLogEntity entity = ModelCallLogEntity.builder()
                .providerId(provider.getId())
                .providerCode(provider.getProviderCode())
                .modelName(provider.getDefaultModel())
                .apiProtocol(provider.getApiProtocol())
                .status("SUCCESS")
                .durationMs(latencyMs)
                .inputTokens(0)
                .outputTokens(0)
                .fallbackUsed(false)
                .generationSource(GENERATION_SOURCE_TEST_RUN)
                .promptTemplateVersionId(trace.promptTemplateVersionId())
                .promptTemplateCode(trace.promptTemplateCode())
                .promptTemplateVersionNo(trace.promptTemplateVersionNo())
                .systemPromptSha256(fingerprint.systemSha256())
                .userPromptSha256(fingerprint.userSha256())
                .combinedPromptFingerprint(fingerprint.combined())
                .createdBy(currentUser.requiredUserId())
                .createdAt(LocalDateTime.now())
                .build();
        modelCallLogEntityMapper.insertCallLog(entity);
    }

    private AuditLogEntity buildTestRunAuditLog(Long operatorUserId,
                                                PromptTemplateEntity templateEntity,
                                                PromptTemplateTrace trace) {
        return AuditLogEntity.builder()
                .workspaceId(templateEntity.getWorkspaceId())
                .actorUserId(operatorUserId)
                .actionCode("PROMPT_TEMPLATE_TEST_RUN")
                .targetType("PROMPT_TEMPLATE")
                .targetId(String.valueOf(templateEntity.getId()))
                .requestId(ResultUtils.currentRequestId())
                .detailJson(buildTestRunAuditDetail(templateEntity, trace))
                .build();
    }

    private String buildTestRunAuditDetail(PromptTemplateEntity templateEntity, PromptTemplateTrace trace) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("templateId", templateEntity.getId());
            detail.put("promptTemplateVersionId", trace.promptTemplateVersionId());
            detail.put("promptTemplateCode", trace.promptTemplateCode());
            detail.put("promptTemplateVersionNo", trace.promptTemplateVersionNo());
            detail.put("testRun", true);
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Prompt test run audit detail serialization failed", exception);
        }
    }

    private void requirePlatformAdmin(CurrentUser currentUser) {
        if (!currentUser.isPlatformAdmin()) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }
}
