package com.codeforge.ai.domain.prompt.model;

public record PromptTemplateTrace(
        Long promptTemplateVersionId,
        String promptTemplateCode,
        Integer promptTemplateVersionNo
) {

    public static PromptTemplateTrace empty() {
        return new PromptTemplateTrace(null, null, null);
    }

    public boolean isEmpty() {
        return promptTemplateVersionId == null;
    }
}
