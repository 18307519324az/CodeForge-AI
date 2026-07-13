package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.task.GenerationTaskCreateResponse;
import com.codeforge.ai.application.dto.task.GenerationTaskDetailResponse;
import com.codeforge.ai.application.dto.task.PublicGenerationStreamEvent;
import com.codeforge.ai.application.service.GenerationTaskApplicationService;
import com.codeforge.ai.domain.task.enums.GenerationStreamStage;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.GlobalExceptionHandler;
import com.codeforge.ai.shared.web.RequestIdFilter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GenerationTaskControllerTest {

    private MockMvc mockMvc;
    private GenerationTaskApplicationService generationTaskApplicationService;

    @BeforeEach
    void setUp() {
        generationTaskApplicationService = mock(GenerationTaskApplicationService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new GenerationTaskController(generationTaskApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturnUnifiedCreateTaskResponse() throws Exception {
        given(generationTaskApplicationService.createTask(any(), any())).willReturn(new GenerationTaskCreateResponse(
                6001L, 1001L, 3001L, "APP_GENERATION", "QUEUED", "req_demo", LocalDateTime.of(2026, 6, 22, 17, 0)
        ));

        mockMvc.perform(post("/v1/generation-tasks")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": 1001,
                                  "appId": 3001,
                                  "taskType": "APP_GENERATION",
                                  "requirement": "build dashboard"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.taskId").value(6001))
                .andExpect(jsonPath("$.data.taskStatus").value("QUEUED"));
    }

    @Test
    void shouldValidateCreateTaskRequest() throws Exception {
        mockMvc.perform(post("/v1/generation-tasks")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": null,
                                  "appId": null,
                                  "taskType": "",
                                  "requirement": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldDelegateTaskStreamToApplicationService() {
        SseEmitter emitter = new SseEmitter();
        CurrentUser currentUser = new CurrentUser(2001L, "editor", List.of("USER"));
        GenerationTaskController controller = new GenerationTaskController(generationTaskApplicationService);
        given(generationTaskApplicationService.openTaskStream(currentUser, 6001L, 102L)).willReturn(emitter);

        SseEmitter response = controller.streamTask(currentUser, 6001L, "102", null);

        assertThat(response).isSameAs(emitter);
        verify(generationTaskApplicationService).openTaskStream(currentUser, 6001L, 102L);
    }

    @Test
    void shouldReturnTaskDetail() throws Exception {
        given(generationTaskApplicationService.getTask(any(), any())).willReturn(new GenerationTaskDetailResponse(
                6001L, 1001L, 3001L, "APP_GENERATION", "RUNNING", null, null,
                LocalDateTime.of(2026, 6, 22, 17, 0),
                LocalDateTime.of(2026, 6, 22, 17, 1),
                null
        ));

        mockMvc.perform(get("/v1/generation-tasks/6001")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(6001))
                .andExpect(jsonPath("$.data.taskStatus").value("RUNNING"));
    }

    @Test
    void shouldReturnPublicTaskEvents() throws Exception {
        given(generationTaskApplicationService.listTaskEvents(any(), eq(6001L), isNull())).willReturn(List.of(
                new PublicGenerationStreamEvent(
                        "7001",
                        "6001",
                        "TASK_CREATED",
                        GenerationStreamStage.TASK.name(),
                        "任务已创建",
                        LocalDateTime.of(2026, 6, 22, 17, 0),
                        false,
                        Map.of()
                )
        ));

        mockMvc.perform(get("/v1/generation-tasks/6001/events")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].type").value("TASK_CREATED"))
                .andExpect(jsonPath("$.data[0].eventId").value("7001"))
                .andExpect(jsonPath("$.data[0].eventPayloadJson").doesNotExist());
    }

    @Test
    void shouldPassAfterEventIdCursorToApplicationService() throws Exception {
        given(generationTaskApplicationService.listTaskEvents(any(), eq(6001L), eq(102L))).willReturn(List.of());

        mockMvc.perform(get("/v1/generation-tasks/6001/events")
                        .param("afterEventId", "102")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk());

        verify(generationTaskApplicationService).listTaskEvents(any(), eq(6001L), eq(102L));
    }

    @Test
    void shouldRejectInvalidAfterEventIdCursor() throws Exception {
        mockMvc.perform(get("/v1/generation-tasks/6001/events")
                        .param("afterEventId", "abc")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(get("/v1/generation-tasks/6001/events")
                        .param("afterEventId", "-1")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(get("/v1/generation-tasks/6001/events")
                        .param("afterEventId", "9223372036854775808")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void shouldCancelTask() throws Exception {
        given(generationTaskApplicationService.cancelTask(any(), any())).willReturn(new GenerationTaskDetailResponse(
                6001L, 1001L, 3001L, "APP_GENERATION", "CANCELLED", null, null,
                LocalDateTime.of(2026, 6, 22, 17, 0),
                LocalDateTime.of(2026, 6, 22, 17, 1),
                LocalDateTime.of(2026, 6, 22, 17, 2)
        ));

        mockMvc.perform(post("/v1/generation-tasks/6001/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskStatus").value("CANCELLED"));
    }

    @Test
    void shouldRetryTask() throws Exception {
        given(generationTaskApplicationService.retryTask(any(), any())).willReturn(new GenerationTaskCreateResponse(
                6002L, 1001L, 3001L, "APP_GENERATION", "QUEUED", "req_retry", LocalDateTime.of(2026, 6, 22, 17, 3)
        ));

        mockMvc.perform(post("/v1/generation-tasks/6001/retry")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(6002))
                .andExpect(jsonPath("$.data.taskStatus").value("QUEUED"));
    }

    private static final class TestingAuthenticationToken
            extends org.springframework.security.authentication.UsernamePasswordAuthenticationToken {

        private TestingAuthenticationToken(CurrentUser principal) {
            super(principal, null, List.of());
        }
    }
}
