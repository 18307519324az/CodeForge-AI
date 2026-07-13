package com.codeforge.ai.domain.prompt.model;

import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.task.entity.GenerationRecordEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PromptTemplateTraceResolver {

    private final GenerationRecordEntityMapper generationRecordEntityMapper;
    private final GenerationTaskEntityMapper generationTaskEntityMapper;
    private final PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private final PromptTemplateEntityMapper promptTemplateEntityMapper;

    public PromptTemplateTrace resolveByTaskId(Long taskId) {
        if (taskId == null) {
            return PromptTemplateTrace.empty();
        }
        GenerationTaskEntity task = generationTaskEntityMapper.selectOneById(taskId);
        if (task != null && task.getPromptTemplateVersionId() != null) {
            return resolveByVersionId(task.getPromptTemplateVersionId());
        }
        GenerationRecordEntity record = generationRecordEntityMapper.findLatestByTaskId(taskId);
        if (record == null || record.getPromptTemplateVersionId() == null) {
            return PromptTemplateTrace.empty();
        }
        return resolveByVersionId(record.getPromptTemplateVersionId());
    }

    public PromptTemplateTrace resolveByVersionEntity(PromptTemplateVersionEntity versionEntity,
                                                      PromptTemplateEntity templateEntity) {
        if (versionEntity == null) {
            return PromptTemplateTrace.empty();
        }
        String templateCode = templateEntity != null ? templateEntity.getTemplateName() : null;
        return new PromptTemplateTrace(versionEntity.getId(), templateCode, versionEntity.getVersionNo());
    }

    public PromptTemplateTrace resolveByVersionId(Long versionId) {
        if (versionId == null) {
            return PromptTemplateTrace.empty();
        }
        PromptTemplateVersionEntity versionEntity = promptTemplateVersionEntityMapper.selectOneById(versionId);
        if (versionEntity == null) {
            return PromptTemplateTrace.empty();
        }
        PromptTemplateEntity templateEntity = promptTemplateEntityMapper.selectOneById(versionEntity.getTemplateId());
        return resolveByVersionEntity(versionEntity, templateEntity);
    }
}
