package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.PromptTemplateTestRunRequest;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTraceResolver;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminPromptTemplateApplicationServiceTest {

    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private ModelCallLogEntityMapper modelCallLogEntityMapper;
    private AuditLogWriter auditLogWriter;
    private ModelProviderSelector modelProviderSelector;
    private PromptTemplateTraceResolver promptTemplateTraceResolver;
    private AdminPromptTemplateApplicationService service;

    @BeforeEach
    void setUp() {
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        modelCallLogEntityMapper = mock(ModelCallLogEntityMapper.class);
        auditLogWriter = mock(AuditLogWriter.class);
        modelProviderSelector = mock(ModelProviderSelector.class);
        promptTemplateTraceResolver = new PromptTemplateTraceResolver(
                mock(com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper.class),
                mock(com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper.class),
                promptTemplateVersionEntityMapper,
                promptTemplateEntityMapper
        );
        service = new AdminPromptTemplateApplicationService(
                promptTemplateEntityMapper,
                promptTemplateVersionEntityMapper,
                modelCallLogEntityMapper,
                auditLogWriter,
                modelProviderSelector,
                promptTemplateTraceResolver,
                new ObjectMapper()
        );
    }

    @Test
    void shouldRunDraftVersionWithoutWritingGenerationRecord() {
        PromptTemplateTestRunRequest request = new PromptTemplateTestRunRequest();
        request.setVersionNo(2);
        request.setMockVariables(Map.of("appName", "CRM"));
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .templateName("APP_PAGE_GEN")
                .currentVersionNo(1)
                .build());
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(4001L, 2)).willReturn(
                PromptTemplateVersionEntity.builder()
                        .id(5002L)
                        .templateId(4001L)
                        .versionNo(2)
                        .userPrompt("生成 {{appName}}")
                        .build()
        );
        given(modelProviderSelector.selectRuleProvider()).willReturn(ModelProviderEntity.builder()
                .id(1L)
                .providerCode("rule")
                .defaultModel("rule-based")
                .apiProtocol("RULE_BASED")
                .build());

        var response = service.testRunTemplate(
                new CurrentUser(1L, "admin", java.util.List.of("PLATFORM_ADMIN")),
                4001L,
                request
        );

        assertThat(response.testRun()).isTrue();
        assertThat(response.promptTemplateVersionId()).isEqualTo(5002L);
        assertThat(response.outputPreview()).contains("CRM");
        verify(modelCallLogEntityMapper).insertCallLog(any(ModelCallLogEntity.class));
        verify(auditLogWriter).insert(any(AuditLogEntity.class));
    }

    @Test
    void shouldNotIncludePromptTextInAuditDetail() throws Exception {
        PromptTemplateTestRunRequest request = new PromptTemplateTestRunRequest();
        request.setMockVariables(Map.of("appName", "CRM"));
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .templateName("APP_PAGE_GEN")
                .currentVersionNo(1)
                .build());
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(4001L, 1)).willReturn(
                PromptTemplateVersionEntity.builder()
                        .id(5001L)
                        .templateId(4001L)
                        .versionNo(1)
                        .userPrompt("生成 {{appName}}")
                        .build()
        );
        given(modelProviderSelector.selectRuleProvider()).willReturn(null);

        service.testRunTemplate(
                new CurrentUser(1L, "admin", java.util.List.of("PLATFORM_ADMIN")),
                4001L,
                request
        );

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogWriter).insert(captor.capture());
        assertThat(captor.getValue().getDetailJson()).contains("promptTemplateVersionId");
        assertThat(captor.getValue().getDetailJson()).doesNotContain("生成 CRM");
    }

    @Test
    void shouldRejectNonAdminUser() {
        PromptTemplateTestRunRequest request = new PromptTemplateTestRunRequest();
        request.setMockVariables(Map.of("appName", "CRM"));

        assertThatThrownBy(() -> service.testRunTemplate(
                new CurrentUser(2L, "user", java.util.List.of("USER")),
                4001L,
                request
        )).isInstanceOf(BusinessException.class);

        verify(promptTemplateEntityMapper, never()).selectOneById(any());
    }
}
