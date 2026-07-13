package com.codeforge.ai.api;

import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.infrastructure.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "mybatis-flex.configuration.map-underscore-to-camel-case=false"
})
class ModelCallLogRestPreservesMappedFieldsTest {

    private static final Long PROBE_TASK_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ModelCallLogEntityMapper modelCallLogEntityMapper;

    @BeforeEach
    void seedProbeLog() {
        ModelCallLogEntity entity = ModelCallLogEntity.builder()
                .taskId(PROBE_TASK_ID)
                .appId(1L)
                .providerId(1L)
                .providerCode("deepseek")
                .modelName("deepseek-v4-pro")
                .status("SUCCESS")
                .inputTokens(88)
                .outputTokens(99)
                .durationMs(3210L)
                .fallbackUsed(false)
                .generationSource("AI_DIRECT_INITIAL")
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .build();
        modelCallLogEntityMapper.insertCallLog(entity);
    }

    @Test
    void adminModelCallLogEndpointShouldPreserveMappedFields() throws Exception {
        mockMvc.perform(get("/v1/admin/model-call-logs")
                        .param("pageNo", "1")
                        .param("pageSize", "10")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].taskId").value("1"))
                .andExpect(jsonPath("$.data.records[0].modelName").value("deepseek-v4-pro"))
                .andExpect(jsonPath("$.data.records[0].generationSource").value("AI_DIRECT_INITIAL"))
                .andExpect(jsonPath("$.data.records[0].durationMs").value("3210"))
                .andExpect(jsonPath("$.data.records[0].inputTokens").value(88));
    }

    private String adminBearer() {
        return "Bearer " + jwtTokenProvider.createAccessToken(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")));
    }
}
