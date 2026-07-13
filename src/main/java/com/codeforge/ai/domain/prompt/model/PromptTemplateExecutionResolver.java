package com.codeforge.ai.domain.prompt.model;

import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PromptTemplateExecutionResolver {

    private final PromptTemplateEntityMapper promptTemplateEntityMapper;
    private final PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;

    public Optional<ResolvedGenerationPrompt> resolveOptional(Long templateId,
                                                              Long templateVersionId,
                                                              String requirement,
                                                              Map<String, String> templateVariables) {
        if (templateId == null && templateVersionId == null) {
            return Optional.empty();
        }
        return Optional.of(resolvePinned(templateId, templateVersionId, requirement, templateVariables));
    }

    public ResolvedGenerationPrompt resolvePinned(Long templateId,
                                                  Long templateVersionId,
                                                  String requirement,
                                                  Map<String, String> templateVariables) {
        if (templateId == null || templateVersionId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "promptTemplateId 与 promptTemplateVersionId 必须同时提供");
        }
        PromptTemplateVersionEntity versionEntity = promptTemplateVersionEntityMapper.selectOneById(templateVersionId);
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "Prompt 模板版本不存在");
        }
        if (!templateId.equals(versionEntity.getTemplateId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "promptTemplateVersionId 与 promptTemplateId 不匹配");
        }
        PromptTemplateEntity templateEntity = promptTemplateEntityMapper.selectOneById(templateId);
        if (templateEntity == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        Map<String, String> variables = buildVariableMap(requirement, templateVariables);
        PromptTemplateRenderer.validateRequiredVariables(
                versionEntity.getSystemPrompt(),
                versionEntity.getUserPrompt(),
                variables);
        String renderedSystemPrompt = PromptTemplateRenderer.render(versionEntity.getSystemPrompt(), variables);
        String renderedUserPrompt = PromptTemplateRenderer.render(versionEntity.getUserPrompt(), variables);
        return PromptFingerprintHasher.withFingerprints(
                templateEntity.getId(),
                versionEntity.getId(),
                templateEntity.getTemplateName(),
                versionEntity.getVersionNo(),
                renderedSystemPrompt,
                renderedUserPrompt);
    }

    private Map<String, String> buildVariableMap(String requirement, Map<String, String> templateVariables) {
        Map<String, String> variables = new LinkedHashMap<>();
        if (templateVariables != null) {
            variables.putAll(templateVariables);
        }
        if (requirement != null && !requirement.isBlank()) {
            variables.putIfAbsent("requirement", requirement);
        }
        return variables;
    }
}
