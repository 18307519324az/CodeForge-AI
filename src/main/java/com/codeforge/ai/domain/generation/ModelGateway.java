package com.codeforge.ai.domain.generation;

public interface ModelGateway {
    boolean supports(String providerCode);
    String generate(GenerationContext context);
}
