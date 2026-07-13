package com.codeforge.ai.infrastructure.persistence;

import com.codeforge.ai.application.dto.task.GenerationTaskCreateRequest;
import com.codeforge.ai.application.service.AiDirectGenerationApplicationService;
import com.codeforge.ai.application.service.AiDirectGenerationApplicationService.GenerationExecutionResult;
import com.codeforge.ai.application.service.GenerationTaskApplicationService;
import com.codeforge.ai.domain.generation.GenerationSource;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest(properties = "codeforge.build.previewdir=target/test-generation-task-create-previews")
@ActiveProfiles("test")
class GenerationTaskCreationPersistenceTest {

    private static final Long SEEDED_WORKSPACE_ID = 1L;
    private static final Long SEEDED_APP_ID = 1L;
    private static final Long ADMIN_USER_ID = 1L;

    @Autowired
    private GenerationTaskApplicationService generationTaskApplicationService;

    @Autowired
    private GenerationTaskEntityMapper generationTaskEntityMapper;

    @MockBean
    private AiDirectGenerationApplicationService aiDirectGenerationApplicationService;

    @BeforeEach
    void stubAiGeneration() {
        given(aiDirectGenerationApplicationService.executeSync(
                any(), any(), anyString(), anyLong(), anyString()))
                .willReturn(new GenerationExecutionResult(
                        GenerationSource.AI_DIRECT,
                        false,
                        "deepseek",
                        "deepseek-chat",
                        10001L,
                        3,
                        true,
                        null));
    }

    @Test
    void shouldCreateTaskWithBrowserEquivalentPayload() {
        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(SEEDED_WORKSPACE_ID);
        request.setAppId(SEEDED_APP_ID);
        request.setTaskType("RULE_GENERATION");
        request.setRequirement("生成一个客户管理后台，包含客户列表、客户详情和客户编辑表单。");

        var response = generationTaskApplicationService.createTask(
                new CurrentUser(ADMIN_USER_ID, "admin", List.of("PLATFORM_ADMIN")),
                request);

        assertThat(response.taskId()).isNotNull();
        assertThat(response.taskType()).isEqualTo("RULE_GENERATION");
        assertThat(response.taskStatus()).isNotBlank();

        GenerationTaskEntity persisted = generationTaskEntityMapper.selectOneById(response.taskId());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getAppId()).isEqualTo(SEEDED_APP_ID);
        assertThat(persisted.getWorkspaceId()).isEqualTo(SEEDED_WORKSPACE_ID);
        assertThat(persisted.getRequestPayloadJson()).contains("客户管理后台");
    }
}