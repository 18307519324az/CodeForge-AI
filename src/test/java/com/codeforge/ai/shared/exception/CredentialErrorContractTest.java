package com.codeforge.ai.shared.exception;

import com.codeforge.ai.shared.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialErrorContractTest {

    @Test
    void missingMasterKeyReturnsSafeServiceUnavailableTest() {
        BusinessException exception = new BusinessException(ErrorCode.CREDENTIAL_ENCRYPTION_UNAVAILABLE);
        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler().handleBusinessException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(50302);
        assertThat(response.getBody().getMessage())
                .isEqualTo("加密凭据存储当前不可用，请先配置服务器主密钥");
    }

    @Test
    void missingMasterKeyDoesNotEchoSubmittedSecretTest() {
        String submittedSecret = "sk-synth-http-contract-secret";
        BusinessException exception = new BusinessException(ErrorCode.CREDENTIAL_ENCRYPTION_UNAVAILABLE);
        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler().handleBusinessException(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).doesNotContain(submittedSecret);
        assertThat(response.getBody().toString()).doesNotContain(submittedSecret);
    }
}
