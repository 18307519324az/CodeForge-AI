package com.codeforge.ai.domain.prompt.model;

import com.codeforge.ai.application.dto.prompt.PromptTemplateVariableItemResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class PromptTemplateVariableSchemaParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PromptTemplateVariableSchemaParser() {
    }

    public static List<PromptTemplateVariableItemResponse> parse(String variablesJson) {
        if (variablesJson == null || variablesJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(variablesJson);
            if (!root.isObject()) {
                return List.of();
            }
            List<PromptTemplateVariableItemResponse> items = new ArrayList<>();
            root.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();
                if (valueNode == null || valueNode.isNull()) {
                    items.add(new PromptTemplateVariableItemResponse(key, "string", true, null));
                    return;
                }
                if (valueNode.isTextual()) {
                    items.add(new PromptTemplateVariableItemResponse(key, valueNode.asText(), true, null));
                    return;
                }
                String type = valueNode.path("type").asText("string");
                boolean required = !valueNode.has("required") || valueNode.get("required").asBoolean(true);
                String description = valueNode.path("description").asText(null);
                if (description != null && description.isBlank()) {
                    description = null;
                }
                items.add(new PromptTemplateVariableItemResponse(key, type, required, description));
            });
            return Collections.unmodifiableList(items);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static Map<String, String> toMap(Map<String, String> templateVariables) {
        if (templateVariables == null || templateVariables.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(templateVariables);
    }
}
