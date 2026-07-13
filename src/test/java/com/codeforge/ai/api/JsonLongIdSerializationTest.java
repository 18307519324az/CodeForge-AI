package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.app.AiAppDetailResponse;
import com.codeforge.ai.application.dto.app.AiAppListItemResponse;
import com.codeforge.ai.application.dto.task.GenerationTaskCreateResponse;
import com.codeforge.ai.application.service.AiAppApplicationService;
import com.codeforge.ai.application.service.GenerationTaskApplicationService;
import com.codeforge.ai.infrastructure.config.JacksonConfig;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.GlobalExceptionHandler;
import com.codeforge.ai.shared.response.PageResponse;
import com.codeforge.ai.shared.web.RequestIdFilter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JsonLongIdSerializationTest {

    private static final long LARGE_APP_ID = 2072279215214051328L;
    private static final long LARGE_WORKSPACE_ID = 2072279215214051327L;
    private static final long LARGE_TASK_ID = 2072279215214051329L;

    private MockMvc appMockMvc;
    private MockMvc taskMockMvc;
    private AiAppApplicationService aiAppApplicationService;
    private GenerationTaskApplicationService generationTaskApplicationService;

    @BeforeEach
    void setUp() {
        aiAppApplicationService = mock(AiAppApplicationService.class);
        generationTaskApplicationService = mock(GenerationTaskApplicationService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(buildObjectMapper());

        appMockMvc = MockMvcBuilders.standaloneSetup(new AiAppController(
                        aiAppApplicationService,
                        generationTaskApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(converter)
                .addFilters(new RequestIdFilter())
                .build();

        taskMockMvc = MockMvcBuilders.standaloneSetup(new GenerationTaskController(generationTaskApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(converter)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldSerializeAppIdsAsStrings() throws Exception {
        given(aiAppApplicationService.createApp(any(), any())).willReturn(new AiAppDetailResponse(
                LARGE_APP_ID, LARGE_WORKSPACE_ID, "App A", "demo", null, "WEB_APP", "DRAFT", "PRIVATE",
                LARGE_TASK_ID, null, LocalDateTime.of(2026, 7, 1, 10, 0), LocalDateTime.of(2026, 7, 1, 10, 0),
                null, null, null, null, null, null, null, null
        ));
        given(aiAppApplicationService.listApps(any(), any())).willReturn(PageResponse.<AiAppListItemResponse>builder()
                .records(List.of(new AiAppListItemResponse(
                        LARGE_APP_ID, LARGE_WORKSPACE_ID, "App A", "demo", null, "WEB_APP", "DRAFT", "PRIVATE",
                        LARGE_TASK_ID, null, LocalDateTime.of(2026, 7, 1, 10, 0), LocalDateTime.of(2026, 7, 1, 10, 0),
                        null, null, null, null, null, null, null, null
                )))
                .pageNo(1)
                .pageSize(10)
                .total(1)
                .build());

        appMockMvc.perform(post("/v1/apps")
                        .with(userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": "2072279215214051327",
                                  "name": "App A",
                                  "description": "demo",
                                  "appType": "WEB_APP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(LARGE_APP_ID)))
                .andExpect(jsonPath("$.data.workspaceId").isString())
                .andExpect(jsonPath("$.data.workspaceId").value(String.valueOf(LARGE_WORKSPACE_ID)));

        appMockMvc.perform(get("/v1/apps").with(userAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").isString())
                .andExpect(jsonPath("$.data.records[0].id").value(String.valueOf(LARGE_APP_ID)))
                .andExpect(jsonPath("$.data.records[0].workspaceId").isString())
                .andExpect(jsonPath("$.data.records[0].workspaceId").value(String.valueOf(LARGE_WORKSPACE_ID)));
    }

    @Test
    void shouldSerializeTaskIdsAsStrings() throws Exception {
        given(generationTaskApplicationService.createTask(any(), any())).willReturn(new GenerationTaskCreateResponse(
                LARGE_TASK_ID,
                LARGE_WORKSPACE_ID,
                LARGE_APP_ID,
                "APP_GENERATION",
                "QUEUED",
                "req_demo",
                LocalDateTime.of(2026, 7, 1, 10, 0)
        ));

        taskMockMvc.perform(post("/v1/generation-tasks")
                        .with(userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": "2072279215214051327",
                                  "appId": "2072279215214051328",
                                  "taskType": "APP_GENERATION",
                                  "requirement": "build dashboard"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").isString())
                .andExpect(jsonPath("$.data.taskId").value(String.valueOf(LARGE_TASK_ID)))
                .andExpect(jsonPath("$.data.workspaceId").isString())
                .andExpect(jsonPath("$.data.workspaceId").value(String.valueOf(LARGE_WORKSPACE_ID)))
                .andExpect(jsonPath("$.data.appId").isString())
                .andExpect(jsonPath("$.data.appId").value(String.valueOf(LARGE_APP_ID)));
    }

    private ObjectMapper buildObjectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().longToStringCustomizer().customize(builder);
        return builder.build();
    }

    private RequestPostProcessor userAuth() {
        return SecurityMockMvcRequestPostProcessors.authentication(
                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))));
    }

    private static final class TestingAuthenticationToken
            extends org.springframework.security.authentication.UsernamePasswordAuthenticationToken {

        private TestingAuthenticationToken(CurrentUser principal) {
            super(principal, null, List.of());
        }
    }
}
