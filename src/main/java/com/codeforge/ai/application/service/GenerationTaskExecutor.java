package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.CodeGenerationFacade;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.generation.CodeGenTypeEnum;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component("asyncGenTaskExecutor")
@RequiredArgsConstructor
public class GenerationTaskExecutor {
    private static final Logger log = LoggerFactory.getLogger(GenerationTaskExecutor.class);
    private final CodeGenerationFacade codeGenerationFacade;
    private final GenerationTaskEntityMapper taskMapper;
    private final GenerationMessageApplicationService messageService;

    @org.springframework.scheduling.annotation.Async("generationTaskExecutor")
    public void executeTask(Long taskId, AiAppEntity app, String requirement, Long userId) {
        GenerationTaskEntity task = taskMapper.selectOneById(taskId);
        if (task == null) { log.error("Task {} not found", taskId); return; }
        try {
            taskMapper.updateTerminalState(taskId, GenerationTaskStatus.RUNNING.name(), null, null, null, userId);
            var ctx = new GenerationContext(requirement, app.getName(), app.getAppType(),
                    CodeGenTypeEnum.fromAppType(app.getAppType()).name(),
                    app.getId(), userId, taskId, null, "auto", null, null, null);
            var result = codeGenerationFacade.generateAndSave(ctx);

            taskMapper.updateTerminalState(taskId, GenerationTaskStatus.SUCCESS.name(), null, null, LocalDateTime.now(), userId);
            messageService.saveMessage(task.getWorkspaceId(), app.getId(), taskId, userId, "ASSISTANT",
                    "生成完成，版本 v" + result.versionNo() + "，共 " + result.fileCount() + " 个文件");
        } catch (Exception e) {
            log.error("Generation task {} failed", taskId, e);
            String errMsg = e.getMessage() != null
                    ? (e.getMessage().length() > 200 ? e.getMessage().substring(0, 200) : e.getMessage())
                    : "未知错误";
            taskMapper.updateTerminalState(taskId, GenerationTaskStatus.FAILED.name(), "GEN_ERROR", errMsg, LocalDateTime.now(), userId);
            messageService.saveMessage(task.getWorkspaceId(), app.getId(), taskId, userId, "ASSISTANT", "生成失败：" + errMsg);
        }
    }
}
