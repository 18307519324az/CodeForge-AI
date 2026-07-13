package com.codeforge.ai.domain.generation;

import com.codeforge.ai.application.generator.RuleBasedAppGenerator;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedModelGateway implements ModelGateway {
    private final RuleBasedAppGenerator generator = new RuleBasedAppGenerator();

    @Override
    public boolean supports(String providerCode) {
        return "rule".equalsIgnoreCase(providerCode) || providerCode == null || providerCode.isBlank();
    }

    @Override
    public String generate(GenerationContext context) {
        RuleBasedAppGenerator.GeneratedProject proj = generator.generate(
                context.appName(), context.appType(), context.requirement());
        StringBuilder sb = new StringBuilder();
        for (var f : proj.files()) {
            sb.append("FILE:").append(f.filePath()).append("\n")
              .append(f.content()).append("\n---END---\n");
        }
        return sb.toString();
    }
}
