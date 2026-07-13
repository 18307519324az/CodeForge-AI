package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.business.BusinessPreset;
import com.codeforge.ai.domain.generation.business.BusinessPresetRegistry;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.generation.prompt.GenerationOutputSchema;
import com.codeforge.ai.domain.generation.scope.GenerationScope;
import com.codeforge.ai.domain.generation.scope.GenerationScopePolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds system/user/retry prompts for AI code generation.
 */
public final class AiCodegenPromptBuilder {

    private static final GenerationScopePolicy SCOPE_POLICY = new GenerationScopePolicy();

    static final String RETRY_USER_INSTRUCTION = """
            Your previous response was invalid or incomplete.
            Return valid JSON only.
            Never output markdown.
            Never explain.
            Never use ``` fences.
            First character must be { and last must be }.
            Schema: %s
            """.formatted(GenerationOutputSchema.FILES_ONLY_SCHEMA);

    static final String STRICT_COMPACT_USER_INSTRUCTION = """
            Your previous response was truncated due to output length limits.
            Regenerate from scratch with a STRICT compact contract.
            Keep only the user's core functional requirement.
            Return strict JSON only. Never output markdown or explanations.
            Prefer a single index.html file.
            Do not include description fields or long comments.
            Use at most 3 mock rows per list.
            Do not add charts unless the user explicitly requested charts.
            Do not add modules the user did not request.
            Simplify CSS and keep JavaScript minimal.
            Target total source code <= 12000 characters.
            Schema: %s
            """.formatted(GenerationOutputSchema.FILES_ONLY_SCHEMA);

    static final String ARTIFACT_REPAIR_USER_INSTRUCTION = """
            上一版生成结果无法运行：
            %s
            请返回完整、可运行、严格 JSON 的项目。
            不要解释。
            确保 HTML/CSS/JS 完整，JSON 字符串中的换行必须正确转义。
            Schema: %s
            """;

    private AiCodegenPromptBuilder() {
    }

    public static List<ModelMessage> buildInitialMessages(String systemPrompt, GenerationContext context) {
        return List.of(
                ModelMessage.system(systemPrompt),
                ModelMessage.user(resolveUserMessage(context))
        );
    }

    public static List<ModelMessage> buildRetryMessages(String systemPrompt, GenerationContext context) {
        List<ModelMessage> messages = new ArrayList<>(3);
        messages.add(ModelMessage.system(systemPrompt));
        messages.add(ModelMessage.user(resolveUserMessage(context)));
        messages.add(ModelMessage.user(RETRY_USER_INSTRUCTION));
        return messages;
    }

    public static List<ModelMessage> buildCompactMessages(String systemPrompt, GenerationContext context) {
        List<ModelMessage> messages = new ArrayList<>(3);
        messages.add(ModelMessage.system(systemPrompt));
        messages.add(ModelMessage.user(resolveCompactBaseUserMessage(context)));
        messages.add(ModelMessage.user(STRICT_COMPACT_USER_INSTRUCTION));
        return messages;
    }

    public static List<ModelMessage> buildArtifactRepairMessages(String systemPrompt,
                                                                 GenerationContext context,
                                                                 String issueSummary) {
        List<ModelMessage> messages = new ArrayList<>(3);
        messages.add(ModelMessage.system(systemPrompt));
        messages.add(ModelMessage.user(resolveUserMessage(context)));
        messages.add(ModelMessage.user(ARTIFACT_REPAIR_USER_INSTRUCTION.formatted(
                issueSummary, GenerationOutputSchema.FILES_ONLY_SCHEMA)));
        return messages;
    }

    public static String resolveUserMessage(GenerationContext context) {
        if (context.renderedUserPrompt() != null && !context.renderedUserPrompt().isBlank()) {
            return context.renderedUserPrompt();
        }
        return buildUserPrompt(context);
    }

    private static String resolveCompactBaseUserMessage(GenerationContext context) {
        if (context.usesTemplatePrompt()) {
            return context.renderedUserPrompt();
        }
        return buildCoreRequirementPrompt(context);
    }

    static String buildUserPrompt(GenerationContext context) {
        GenerationScope scope = SCOPE_POLICY.resolve(context.requirement(), context.appType());
        BusinessPreset preset = BusinessPresetRegistry.resolve(context.appType(), context.requirement());
        return """
                Role
                Senior Product Designer and Frontend Engineer.

                Goal
                Generate a business-faithful %s prototype for app "%s".

                Generation Scope
                %s

                Business Context
                User requirement: %s
                App Type: %s
                Business Type: %s
                Code Generation Type: %s

                Business Modules
                %s

                Business Workflow
                %s

                Business Entities
                %s

                Business Fields
                %s

                Business Interaction
                %s

                Must Include
                %s

                Must Avoid
                %s

                Output Budget
                Target total source code <= %d characters.

                Output JSON
                Follow the system prompt schema exactly.
                """.formatted(
                preset.businessType(),
                nullToBlank(context.appName()),
                scope.name(),
                nullToBlank(context.requirement()),
                nullToBlank(context.appType()),
                preset.businessType(),
                context.codeGenType() != null ? context.codeGenType() : "HTML",
                bulletList(scopeModules(scope, preset)),
                numberedList(scopeWorkflow(scope, preset)),
                bulletList(scopeEntities(scope, preset)),
                bulletList(scopeFields(scope, preset)),
                bulletList(scopeInteractions(scope, preset)),
                bulletList(mustInclude(scope, context.requirement())),
                bulletList(mustAvoid(scope, preset)),
                SCOPE_POLICY.targetSourceChars(scope));
    }

    static String buildCoreRequirementPrompt(GenerationContext context) {
        return """
                Core Requirement
                %s

                App Name
                %s

                App Type
                %s
                """.formatted(
                nullToBlank(context.requirement()),
                nullToBlank(context.appName()),
                nullToBlank(context.appType()));
    }

    static GenerationScope resolveScope(GenerationContext context) {
        return SCOPE_POLICY.resolve(context.requirement(), context.appType());
    }

    private static List<String> mustInclude(GenerationScope scope, String requirement) {
        List<String> rules = new ArrayList<>();
        rules.add("- Generate exactly one compact index.html file.");
        rules.add("- Implement only the core features from the user requirement.");
        rules.add("- Use at most " + SCOPE_POLICY.maxMockRows(scope) + " mock rows per list.");
        if (scope == GenerationScope.MINIMAL) {
            rules.add("- Do not require charts, statistics cards, or multiple CRUD modules.");
            rules.add("- Keep CSS concise and JavaScript minimal.");
            rules.add("- No large SVG graphics or complex animations.");
        } else if (scope == GenerationScope.STANDARD) {
            rules.add("- Generate only modules explicitly mentioned in the requirement.");
            rules.add("- Add search or filter controls only when the requirement asks for them.");
            if (SCOPE_POLICY.requiresCharts(scope, requirement)) {
                rules.add("- Include one lightweight chart-like visualization only because the user requested it.");
            } else {
                rules.add("- Do not add charts unless the user explicitly requested charts.");
            }
            rules.add("- Do not add modules the user did not request.");
        } else {
            rules.add("- At least three distinct page modules or sections.");
            rules.add("- At least two statistics or metric cards.");
            rules.add("- At least one lightweight chart-like visualization built with div bars or simple CSS.");
            rules.add("- At least two CRUD interactions.");
            rules.add("- At least one search control.");
            rules.add("- At least one filter control.");
            rules.add("- Clear navigation that matches the business type.");
        }
        rules.add("- No external libraries, images, icon fonts, or remote assets.");
        rules.add("- Do not generate separate files other than index.html.");
        return rules;
    }

    private static List<String> mustAvoid(GenerationScope scope, BusinessPreset preset) {
        List<String> avoid = new ArrayList<>(preset.mustAvoid());
        if (scope != GenerationScope.RICH) {
            avoid.add("Unrequested dashboard modules");
            avoid.add("Unrequested charts or statistics cards");
            avoid.add("Unrequested extra CRUD modules");
        }
        avoid.add("Long comments or explanatory text");
        avoid.add("Copying the full user prompt as the page title");
        return avoid;
    }

    private static List<String> scopeModules(GenerationScope scope, BusinessPreset preset) {
        if (scope == GenerationScope.MINIMAL) {
            return List.of("Core requirement only");
        }
        if (scope == GenerationScope.STANDARD) {
            return preset.modules().size() > 3 ? preset.modules().subList(0, 3) : preset.modules();
        }
        return preset.modules();
    }

    private static List<String> scopeWorkflow(GenerationScope scope, BusinessPreset preset) {
        if (scope == GenerationScope.MINIMAL) {
            return List.of("Implement the core user flow only");
        }
        if (scope == GenerationScope.STANDARD) {
            return preset.workflow().size() > 3 ? preset.workflow().subList(0, 3) : preset.workflow();
        }
        return preset.workflow();
    }

    private static List<String> scopeEntities(GenerationScope scope, BusinessPreset preset) {
        if (scope == GenerationScope.MINIMAL) {
            return List.of("Only entities required by the user requirement");
        }
        if (scope == GenerationScope.STANDARD) {
            return preset.entities().size() > 3 ? preset.entities().subList(0, 3) : preset.entities();
        }
        return preset.entities();
    }

    private static List<String> scopeFields(GenerationScope scope, BusinessPreset preset) {
        if (scope == GenerationScope.MINIMAL) {
            return List.of("Only fields required by the user requirement");
        }
        if (scope == GenerationScope.STANDARD) {
            return preset.fields().size() > 4 ? preset.fields().subList(0, 4) : preset.fields();
        }
        return preset.fields();
    }

    private static List<String> scopeInteractions(GenerationScope scope, BusinessPreset preset) {
        List<String> merged = merge(preset.buttons(), preset.interactions());
        if (scope == GenerationScope.MINIMAL) {
            return merged.size() > 3 ? merged.subList(0, 3) : merged;
        }
        if (scope == GenerationScope.STANDARD) {
            return merged.size() > 4 ? merged.subList(0, 4) : merged;
        }
        return merged;
    }

    static String resolvePromptFile(String codeGenType) {
        if ("MULTI_FILE".equalsIgnoreCase(codeGenType)) {
            return "codegen-multi-file-system-prompt.txt";
        }
        if ("VUE_PROJECT".equalsIgnoreCase(codeGenType)) {
            return "codegen-vue-project-system-prompt.txt";
        }
        return "codegen-html-system-prompt.txt";
    }

    private static List<String> merge(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<>(left.size() + right.size());
        merged.addAll(left);
        merged.addAll(right);
        return merged;
    }

    private static String bulletList(List<String> items) {
        return items.stream()
                .map(item -> "- " + item)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("-");
    }

    private static String numberedList(List<String> items) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(i + 1).append(". ").append(items.get(i));
        }
        return builder.toString();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
