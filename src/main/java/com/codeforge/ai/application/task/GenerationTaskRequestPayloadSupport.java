package com.codeforge.ai.application.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GenerationTaskRequestPayloadSupport {

    private GenerationTaskRequestPayloadSupport() {
    }

    public static ParsedPayload parse(ObjectMapper objectMapper, String requestPayloadJson) {
        if (requestPayloadJson == null || requestPayloadJson.isBlank()) {
            return new ParsedPayload(null, null, null, Map.of());
        }
        try {
            JsonNode payloadNode = objectMapper.readTree(requestPayloadJson);
            String requirement = readText(payloadNode, "requirement");
            Long promptTemplateId = readLong(payloadNode, "promptTemplateId");
            Long promptTemplateVersionId = readLong(payloadNode, "promptTemplateVersionId");
            Map<String, String> templateVariables = readTemplateVariables(objectMapper, payloadNode.get("templateVariables"));
            return new ParsedPayload(requirement, promptTemplateId, promptTemplateVersionId, templateVariables);
        } catch (JsonProcessingException exception) {
            return new ParsedPayload(requestPayloadJson, null, null, Map.of());
        }
    }

    private static Map<String, String> readTemplateVariables(ObjectMapper objectMapper, JsonNode variablesNode) {
        if (variablesNode == null || variablesNode.isNull() || !variablesNode.isObject()) {
            return Map.of();
        }
        Map<String, String> variables = new LinkedHashMap<>();
        variablesNode.fields().forEachRemaining(entry -> {
            JsonNode valueNode = entry.getValue();
            if (valueNode != null && !valueNode.isNull()) {
                variables.put(entry.getKey(), valueNode.asText());
            }
        });
        return Collections.unmodifiableMap(variables);
    }

    private static String readText(JsonNode payloadNode, String fieldName) {
        JsonNode node = payloadNode.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    private static Long readLong(JsonNode payloadNode, String fieldName) {
        JsonNode node = payloadNode.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        String text = node.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        return Long.parseLong(text);
    }

    public record ParsedPayload(
            String requirement,
            Long promptTemplateId,
            Long promptTemplateVersionId,
            Map<String, String> templateVariables
    ) {
    }
}
