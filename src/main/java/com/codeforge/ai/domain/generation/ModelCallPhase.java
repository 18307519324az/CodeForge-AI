package com.codeforge.ai.domain.generation;

public enum ModelCallPhase {
    INITIAL("AI_DIRECT_INITIAL"),
    COMPACT_RETRY("AI_DIRECT_COMPACT_RETRY"),
    REPAIR("AI_DIRECT_REPAIR"),
    PARSE_RETRY("AI_DIRECT_PARSE_RETRY");

    private final String generationSourceCode;

    ModelCallPhase(String generationSourceCode) {
        this.generationSourceCode = generationSourceCode;
    }

    public String generationSourceCode() {
        return generationSourceCode;
    }
}
