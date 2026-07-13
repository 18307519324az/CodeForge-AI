package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.generation.entity.GenerationMessageEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationMessageEntityMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GenerationMessageApplicationService {
    private static final Logger log = LoggerFactory.getLogger(GenerationMessageApplicationService.class);
    private final GenerationMessageEntityMapper messageMapper;

    @Transactional
    public void saveMessage(Long workspaceId, Long appId, Long taskId, Long userId, String role, String content) {
        try {
            GenerationMessageEntity entity = GenerationMessageEntity.builder()
                    .workspaceId(workspaceId).appId(appId).taskId(taskId).userId(userId)
                    .messageRole(role).messageContent(content).messageType("TEXT")
                    .createdAt(LocalDateTime.now()).isDeleted(0).build();
            messageMapper.insertMessage(entity);
        } catch (Exception e) {
            log.warn("Failed to save generation message: {}", e.getMessage());
        }
    }

    public List<GenerationMessageEntity> listAppMessages(Long appId) {
        return messageMapper.findByAppId(appId);
    }

    public List<GenerationMessageEntity> listAppMessagesCursor(Long appId, Long cursor, int limit) {
        if (cursor == null || cursor <= 0) {
            return messageMapper.findByAppIdWithLimit(appId, limit);
        }
        return messageMapper.findByAppIdBeforeId(appId, cursor, limit);
    }

    public List<GenerationMessageEntity> listTaskMessages(Long taskId) {
        return messageMapper.findByTaskId(taskId);
    }
}
