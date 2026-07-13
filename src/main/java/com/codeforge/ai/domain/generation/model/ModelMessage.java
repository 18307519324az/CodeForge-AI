package com.codeforge.ai.domain.generation.model;

public record ModelMessage(String role, String content) {
    public static ModelMessage system(String content) { return new ModelMessage("system", content); }
    public static ModelMessage user(String content) { return new ModelMessage("user", content); }
    public static ModelMessage assistant(String content) { return new ModelMessage("assistant", content); }
}
