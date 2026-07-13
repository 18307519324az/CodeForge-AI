package com.codeforge.ai.domain.generation.model;

public enum CredentialSource {
    ENV,
    ENCRYPTED_DB,
    NONE;

    public static CredentialSource fromValue(String value) {
        if (value == null || value.isBlank()) {
            return ENV;
        }
        return switch (value.trim().toUpperCase()) {
            case "ENCRYPTED_DB" -> ENCRYPTED_DB;
            case "NONE" -> NONE;
            default -> ENV;
        };
    }

    public String code() {
        return name();
    }
}
