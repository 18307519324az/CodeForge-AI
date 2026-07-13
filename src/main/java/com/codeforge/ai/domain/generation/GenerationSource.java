package com.codeforge.ai.domain.generation;

public enum GenerationSource {
    AI_DIRECT,
    RULE_FALLBACK,
    RULE_ONLY;

    public String code() {
        return name();
    }
}
