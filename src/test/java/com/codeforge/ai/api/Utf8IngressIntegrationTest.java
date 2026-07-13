package com.codeforge.ai.api;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceEntityMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Utf8IngressIntegrationTest {

    private static final String DISPLAY_NAME = "\u4E2D\u6587\u6D4B\u8BD5\u7528\u6237";
    private static final String WORKSPACE_NAME = "\u4E2D\u6587\u5DE5\u4F5C\u7A7A\u95F4";
    private static final String WORKSPACE_DESCRIPTION = "\u4E2D\u6587\u63CF\u8FF0";
    private static final String APP_NAME = "\u5F85\u529E\u6E05\u5355\u9875\u9762";
    private static final String APP_DESCRIPTION = "\u5305\u542B\u4E2D\u6587\u5B57\u6BB5";
    private static final String APP_TYPE = "Web \u5E94\u7528";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserEntityMapper userEntityMapper;

    @Autowired
    private WorkspaceEntityMapper workspaceEntityMapper;

    @Autowired
    private AiAppEntityMapper aiAppEntityMapper;

    @Test
    void shouldPreserveUtf8AcrossRegisterWorkspaceAndAppCreation() throws Exception {
        String suffix = Long.toString(System.currentTimeMillis());
        String account = "utf8user" + suffix;
        String password = "password123";
        String email = "utf8-" + suffix + "@example.com";

        JsonNode registerNode = readJson(mockMvc.perform(post("/v1/auth/register")
                        .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(writeJson(Map.of(
                                "account", account,
                                "password", password,
                                "confirmPassword", password,
                                "displayName", DISPLAY_NAME,
                                "email", email
                        ))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(registerNode.path("data").path("user").path("displayName").asText()).isEqualTo(DISPLAY_NAME);

        UserEntity createdUser = userEntityMapper.findByAccount(account);
        assertThat(createdUser).isNotNull();
        assertThat(createdUser.getDisplayName()).isEqualTo(DISPLAY_NAME);

        JsonNode loginNode = readJson(mockMvc.perform(post("/v1/auth/login")
                        .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(writeJson(Map.of(
                                "account", account,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn());

        String accessToken = loginNode.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();

        JsonNode workspaceNode = readJson(mockMvc.perform(post("/v1/workspaces")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(writeJson(Map.of(
                                "name", WORKSPACE_NAME,
                                "description", WORKSPACE_DESCRIPTION
                        ))))
                .andExpect(status().isOk())
                .andReturn());

        Long workspaceId = workspaceNode.path("data").path("id").asLong();
        assertThat(workspaceNode.path("data").path("name").asText()).isEqualTo(WORKSPACE_NAME);
        assertThat(workspaceNode.path("data").path("description").asText()).isEqualTo(WORKSPACE_DESCRIPTION);

        WorkspaceEntity workspaceEntity = workspaceEntityMapper.selectOneById(workspaceId);
        assertThat(workspaceEntity).isNotNull();
        assertThat(workspaceEntity.getName()).isEqualTo(WORKSPACE_NAME);
        assertThat(workspaceEntity.getDescription()).isEqualTo(WORKSPACE_DESCRIPTION);

        JsonNode appNode = readJson(mockMvc.perform(post("/v1/apps")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(writeJson(Map.of(
                                "workspaceId", workspaceId,
                                "name", APP_NAME,
                                "description", APP_DESCRIPTION,
                                "appType", APP_TYPE
                        ))))
                .andExpect(status().isOk())
                .andReturn());

        Long appId = appNode.path("data").path("id").asLong();
        assertThat(appNode.path("data").path("name").asText()).isEqualTo(APP_NAME);
        assertThat(appNode.path("data").path("description").asText()).isEqualTo(APP_DESCRIPTION);
        assertThat(appNode.path("data").path("appType").asText()).isEqualTo(APP_TYPE);

        AiAppEntity aiAppEntity = aiAppEntityMapper.selectOneById(appId);
        assertThat(aiAppEntity).isNotNull();
        assertThat(aiAppEntity.getName()).isEqualTo(APP_NAME);
        assertThat(aiAppEntity.getDescription()).isEqualTo(APP_DESCRIPTION);
        assertThat(aiAppEntity.getAppType()).isEqualTo(APP_TYPE);
    }

    private String writeJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(MvcResult mvcResult) throws Exception {
        return objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
