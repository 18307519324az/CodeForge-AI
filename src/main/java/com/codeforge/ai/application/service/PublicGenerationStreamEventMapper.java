package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.task.PublicGenerationStreamEvent;
import com.codeforge.ai.domain.generation.model.ProviderErrorSanitizer;
import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.codeforge.ai.domain.task.enums.GenerationStreamStage;
import com.codeforge.ai.domain.task.enums.GenerationTaskEventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicGenerationStreamEventMapper {

    private static final String HEARTBEAT_TYPE = "HEARTBEAT";

    private final ObjectMapper objectMapper;

    public PublicGenerationStreamEvent heartbeat(Long taskId) {
        return new PublicGenerationStreamEvent(
                null,
                String.valueOf(taskId),
                HEARTBEAT_TYPE,
                GenerationStreamStage.TASK.name(),
                "stream heartbeat",
                LocalDateTime.now(),
                false,
                Map.of()
        );
    }

    public PublicGenerationStreamEvent fromEntity(GenerationTaskEventEntity entity) {
        GenerationTaskEventType eventType = parseEventType(entity.getEventType());
        LocalDateTime timestamp = entity.getCreatedAt() == null ? LocalDateTime.now() : entity.getCreatedAt();
        JsonNode payload = readPayload(entity.getEventPayloadJson());
        return new PublicGenerationStreamEvent(
                entity.getId() == null ? null : String.valueOf(entity.getId()),
                entity.getTaskId() == null ? null : String.valueOf(entity.getTaskId()),
                eventType.name(),
                resolveStage(eventType).name(),
                resolveMessage(eventType, entity.getEventMessage(), payload),
                timestamp,
                isTerminal(eventType),
                resolveSafeData(eventType, payload)
        );
    }

    private GenerationTaskEventType parseEventType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return GenerationTaskEventType.TASK_CREATED;
        }
        try {
            return GenerationTaskEventType.valueOf(rawType);
        } catch (IllegalArgumentException exception) {
            return GenerationTaskEventType.TASK_CREATED;
        }
    }

    private GenerationStreamStage resolveStage(GenerationTaskEventType eventType) {
        return switch (eventType) {
            case TASK_CREATED, TASK_STARTED -> GenerationStreamStage.TASK;
            case PROMPT_RENDERED -> GenerationStreamStage.PROMPT;
            case MODEL_CALL_STARTED, MODEL_DELTA, MODEL_CALL_FINISHED -> GenerationStreamStage.AI_MODEL;
            case FILES_GENERATED -> GenerationStreamStage.FILES;
            case VERSION_CREATED -> GenerationStreamStage.VERSION;
            case TASK_SUCCESS, TASK_FAILED, TASK_CANCELLED -> GenerationStreamStage.TERMINAL;
        };
    }

    private boolean isTerminal(GenerationTaskEventType eventType) {
        return eventType == GenerationTaskEventType.TASK_SUCCESS
                || eventType == GenerationTaskEventType.TASK_FAILED
                || eventType == GenerationTaskEventType.TASK_CANCELLED;
    }

    private String resolveMessage(GenerationTaskEventType eventType, String rawMessage, JsonNode payload) {
        return switch (eventType) {
            case TASK_CREATED -> "任务已创建";
            case TASK_STARTED -> "开始生成应用";
            case PROMPT_RENDERED -> "生成需求已准备";
            case MODEL_CALL_STARTED -> "正在调用 AI 模型";
            case MODEL_DELTA -> "AI 正在生成项目内容";
            case MODEL_CALL_FINISHED -> "AI 模型调用完成";
            case FILES_GENERATED -> "已生成项目文件";
            case VERSION_CREATED -> "已创建应用版本";
            case TASK_SUCCESS -> "生成完成";
            case TASK_FAILED -> sanitizeFailureMessage(rawMessage, payload);
            case TASK_CANCELLED -> "任务已取消";
        };
    }

    private String sanitizeFailureMessage(String rawMessage, JsonNode payload) {
        if (payload != null && payload.hasNonNull("error")) {
            String error = payload.get("error").asText("");
            if (!error.isBlank()) {
                return truncateSafeMessage(error);
            }
        }
        if (rawMessage == null || rawMessage.isBlank()) {
            return "生成失败";
        }
        String normalized = rawMessage.startsWith("生成失败：")
                ? rawMessage.substring("生成失败：".length())
                : rawMessage;
        return truncateSafeMessage(normalized);
    }

    private String truncateSafeMessage(String message) {
        return ProviderErrorSanitizer.toPublicMessage(message);
    }

    private Map<String, Object> resolveSafeData(GenerationTaskEventType eventType, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return Map.of();
        }
        return switch (eventType) {
            case TASK_CREATED, TASK_STARTED, PROMPT_RENDERED, TASK_SUCCESS, TASK_CANCELLED -> Map.of();
            case MODEL_CALL_STARTED -> safeModelCallStartedData(payload);
            case MODEL_DELTA -> safeModelDeltaData(payload);
            case MODEL_CALL_FINISHED -> safeModelCallFinishedData(payload);
            case FILES_GENERATED -> safeFilesGeneratedData(payload);
            case VERSION_CREATED -> safeVersionCreatedData(payload);
            case TASK_FAILED -> safeTaskFailedData(payload);
        };
    }

    private Map<String, Object> safeModelCallStartedData(JsonNode payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        putStringIfPresent(data, "providerDisplayName", firstNonBlank(
                text(payload, "providerDisplayName"),
                text(payload, "providerName")));
        putStringIfPresent(data, "modelName", text(payload, "modelName"));
        return Map.copyOf(data);
    }

    private Map<String, Object> safeModelDeltaData(JsonNode payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        Long attempt = longValue(payload, "attempt");
        if (attempt != null && attempt > 0) {
            data.put("attempt", attempt);
        }
        Long receivedChars = longValue(payload, "receivedChars");
        if (receivedChars != null && receivedChars >= 0) {
            data.put("receivedChars", receivedChars);
        }
        Long chunkCount = longValue(payload, "chunkCount");
        if (chunkCount != null && chunkCount > 0) {
            data.put("chunkCount", chunkCount);
        }
        Long elapsedMs = longValue(payload, "elapsedMs");
        if (elapsedMs != null && elapsedMs >= 0) {
            data.put("elapsedMs", elapsedMs);
        }
        return Map.copyOf(data);
    }

    private Map<String, Object> safeModelCallFinishedData(JsonNode payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        Long durationMs = longValue(payload, "durationMs");
        if (durationMs != null && durationMs >= 0) {
            data.put("durationMs", durationMs);
        }
        Long inputTokens = longValue(payload, "inputTokens");
        if (inputTokens != null && inputTokens >= 0) {
            data.put("inputTokens", inputTokens);
        }
        Long outputTokens = longValue(payload, "outputTokens");
        if (outputTokens != null && outputTokens >= 0) {
            data.put("outputTokens", outputTokens);
        }
        return Map.copyOf(data);
    }

    private Map<String, Object> safeFilesGeneratedData(JsonNode payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        Long fileCount = longValue(payload, "fileCount");
        if (fileCount != null && fileCount >= 0) {
            data.put("fileCount", fileCount);
        }
        return Map.copyOf(data);
    }

    private Map<String, Object> safeVersionCreatedData(JsonNode payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        String versionId = normalizePublicLongId(payload, "versionId");
        if (versionId == null) {
            versionId = normalizePublicLongId(payload, "appVersionId");
        }
        if (versionId != null) {
            data.put("versionId", versionId);
        }
        Long versionNo = longValue(payload, "versionNo");
        if (versionNo != null && versionNo >= 0) {
            data.put("versionNo", versionNo);
        }
        return Map.copyOf(data);
    }

    String normalizePublicLongId(JsonNode payload, String fieldName) {
        if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return null;
        }
        JsonNode node = payload.get(fieldName);
        if (node.isIntegralNumber()) {
            return String.valueOf(node.longValue());
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            return text.matches("\\d+") ? text : null;
        }
        if (node.isNumber()) {
            BigDecimal decimal = node.decimalValue();
            if (decimal.stripTrailingZeros().scale() <= 0) {
                return decimal.toBigInteger().toString();
            }
        }
        return null;
    }

    private Map<String, Object> safeTaskFailedData(JsonNode payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        ProviderErrorSanitizer.PublicTaskError publicError = ProviderErrorSanitizer.sanitizeStoredTaskError(
                text(payload, "errorCode"),
                text(payload, "error"));
        putStringIfPresent(data, "errorCode", publicError.errorCode());
        return Map.copyOf(data);
    }

    private JsonNode readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode payload, String fieldName) {
        if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return null;
        }
        String value = payload.get(fieldName).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private Long longValue(JsonNode payload, String fieldName) {
        if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return null;
        }
        JsonNode node = payload.get(fieldName);
        if (node.isNumber()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private void putStringIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
