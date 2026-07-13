package com.codeforge.ai.domain.generation.model;

public class NoAiProviderAvailableException extends RuntimeException {
    public NoAiProviderAvailableException(String message) {
        super(message);
    }
}
