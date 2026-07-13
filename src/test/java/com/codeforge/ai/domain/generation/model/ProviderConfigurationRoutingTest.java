package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.application.dto.admin.ProviderCredentialUpsertRequest;
import com.codeforge.ai.application.service.AdminModelProviderApplicationService;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderCredentialEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
import com.codeforge.ai.infrastructure.security.CredentialEncryptionService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProviderConfigurationRoutingTest {

    @Test
    void autoModeIsNotProviderEntityTest() {
        assertThat(ProviderCatalogSupport.isPseudoProvider(
                ModelProviderEntity.builder().providerCode("auto").build())).isTrue();
        assertThat(ProviderRoutingMode.fromConfigValue("auto")).isEqualTo(ProviderRoutingMode.AUTO);
        assertThat(ProviderRoutingMode.pinnedProviderCode("auto")).isNull();
    }

    @Test
    void ruleProviderCannotStoreApiKeyTest() {
        ModelProviderEntity rule = ModelProviderEntity.builder()
                .id(99L)
                .providerCode("rule")
                .authMode("NONE")
                .apiProtocol("RULE_BASED")
                .status("ACTIVE")
                .build();
        ModelProviderEntityMapper providerMapper = org.mockito.Mockito.mock(ModelProviderEntityMapper.class);
        ModelProviderRoutingConfigService routingConfigService =
                org.mockito.Mockito.mock(ModelProviderRoutingConfigService.class);
        given(routingConfigService.isRuleProvider(rule)).willReturn(true);
        AdminModelProviderApplicationService service = new AdminModelProviderApplicationService(
                providerMapper,
                org.mockito.Mockito.mock(ModelProviderCredentialEntityMapper.class),
                org.mockito.Mockito.mock(AuditLogWriter.class),
                new ObjectMapper(),
                org.mockito.Mockito.mock(ProviderConfigCacheInvalidator.class),
                routingConfigService,
                org.mockito.Mockito.mock(ProviderCredentialResolver.class),
                org.mockito.Mockito.mock(CredentialEncryptionService.class),
                new ProviderHealthCheckService(routingConfigService)
        );
        given(providerMapper.findById(99L)).willReturn(rule);
        ProviderCredentialUpsertRequest request = new ProviderCredentialUpsertRequest();
        request.setApiKey("invalid-for-rule");
        assertThatThrownBy(() -> service.upsertProviderCredential(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")),
                99L,
                request
        )).isInstanceOf(BusinessException.class);
    }
}
