package com.codeforge.ai.shared.exception;

import com.codeforge.ai.shared.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerDuplicateKeyTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unknownDuplicateKeyDoesNotMapToUserAlreadyExists() {
        DuplicateKeyException exception = new DuplicateKeyException(
                "Duplicate entry '8-default' for key 'uk_workspace_owner_name'",
                new java.sql.SQLIntegrityConstraintViolationException(
                        "Duplicate entry '8-default' for key 'uk_workspace_owner_name'"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleDuplicateKeyException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("数据冲突，请检查重复数据");
        assertThat(response.getBody().getMessage()).doesNotContain("账号或邮箱已存在");
    }

    @Test
    void duplicateEmailStillMapsToUserAlreadyExistsViaBusinessException() {
        BusinessException exception = new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "邮箱已存在");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("邮箱已存在");
    }
}
