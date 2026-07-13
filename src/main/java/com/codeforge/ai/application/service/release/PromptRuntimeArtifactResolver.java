package com.codeforge.ai.application.service.release;

import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskEventType;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEventEntityMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PromptRuntimeArtifactResolver {

    private final GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final ObjectMapper objectMapper;

    public TaskArtifactBindingResult resolve(Long taskId, Long appId) {
        if (taskId == null) {
            return TaskArtifactBindingResult.unresolved(appId, TaskArtifactBindingResult.ERROR_UNRESOLVED);
        }

        Set<Long> candidateVersionIds = new LinkedHashSet<>();
        String versionCreatedSource = null;
        String taskSuccessSource = null;

        List<GenerationTaskEventEntity> events = generationTaskEventEntityMapper.findByTaskId(taskId);
        for (GenerationTaskEventEntity event : events) {
            String eventType = event.getEventType();
            if (!GenerationTaskEventType.VERSION_CREATED.name().equals(eventType)
                    && !GenerationTaskEventType.TASK_SUCCESS.name().equals(eventType)) {
                continue;
            }
            Long versionId = readVersionId(event.getEventPayloadJson());
            if (versionId == null) {
                continue;
            }
            candidateVersionIds.add(versionId);
            if (GenerationTaskEventType.VERSION_CREATED.name().equals(eventType)) {
                versionCreatedSource = TaskArtifactBindingResult.SOURCE_VERSION_CREATED_EVENT;
            } else if (taskSuccessSource == null) {
                taskSuccessSource = TaskArtifactBindingResult.SOURCE_TASK_SUCCESS_EVENT;
            }
        }

        boolean hasSourceTaskVersion = false;
        List<AppVersionEntity> sourceTaskVersions = appVersionEntityMapper.findBySourceTaskId(taskId);
        for (AppVersionEntity version : sourceTaskVersions) {
            if (version.getId() != null) {
                candidateVersionIds.add(version.getId());
                hasSourceTaskVersion = true;
            }
        }

        if (candidateVersionIds.isEmpty()) {
            return TaskArtifactBindingResult.unresolved(appId, TaskArtifactBindingResult.ERROR_UNRESOLVED);
        }
        if (candidateVersionIds.size() > 1) {
            return TaskArtifactBindingResult.unresolved(appId, TaskArtifactBindingResult.ERROR_CONFLICTING);
        }

        Long appVersionId = candidateVersionIds.iterator().next();
        AppVersionEntity version = appVersionEntityMapper.findById(appVersionId);
        if (version == null) {
            return TaskArtifactBindingResult.unresolved(appId, TaskArtifactBindingResult.ERROR_VERSION_NOT_FOUND);
        }
        if (appId != null && version.getAppId() != null && !appId.equals(version.getAppId())) {
            return TaskArtifactBindingResult.unresolved(appId, TaskArtifactBindingResult.ERROR_VERSION_APP_MISMATCH);
        }

        String bindingSource = versionCreatedSource != null
                ? versionCreatedSource
                : taskSuccessSource != null
                        ? taskSuccessSource
                        : hasSourceTaskVersion
                                ? TaskArtifactBindingResult.SOURCE_SOURCE_TASK_VERSION
                                : null;
        Long resolvedAppId = appId != null ? appId : version.getAppId();
        return TaskArtifactBindingResult.resolved(resolvedAppId, appVersionId, bindingSource);
    }

    private Long readVersionId(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(payloadJson);
            JsonNode versionNode = payload.get("versionId");
            if (versionNode == null || versionNode.isNull()) {
                return null;
            }
            if (versionNode.isNumber()) {
                return versionNode.longValue();
            }
            String text = versionNode.asText();
            if (text == null || text.isBlank()) {
                return null;
            }
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }
}
