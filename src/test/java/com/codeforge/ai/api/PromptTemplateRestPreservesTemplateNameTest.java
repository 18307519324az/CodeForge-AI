package com.codeforge.ai.api;

import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
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
class PromptTemplateRestPreservesTemplateNameTest {

    private static final String PROBE_TEMPLATE_NAME = "REST Probe Template";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PromptTemplateEntityMapper promptTemplateEntityMapper;

    @BeforeEach
    void seedProbeTemplate() {
        LocalDateTime now = LocalDateTime.now();
        PromptTemplateEntity entity = PromptTemplateEntity.builder()
                .workspaceId(1L)
                .templateName(PROBE_TEMPLATE_NAME)
                .templateScene("CODE_GEN")
                .status("DRAFT")
                .currentVersionNo(1)
                .build();
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        promptTemplateEntityMapper.insertTemplate(entity);
    }

    @Test
    void promptTemplateEndpointShouldPreserveTemplateName() throws Exception {
        mockMvc.perform(get("/v1/prompt-templates")
                        .param("pageNo", "1")
                        .param("pageSize", "20")
                        .param("workspaceId", "1")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[?(@.templateName == 'REST Probe Template')]").exists())
                .andExpect(jsonPath("$.data.records[?(@.templateName == 'REST Probe Template')].updatedAt").isNotEmpty());
    }

    private String adminBearer() {
        return "Bearer " + jwtTokenProvider.createAccessToken(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")));
    }
}
