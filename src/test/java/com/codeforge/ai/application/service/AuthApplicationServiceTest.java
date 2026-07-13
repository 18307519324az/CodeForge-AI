package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.auth.UserRegisterRequest;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.auth.entity.UserRoleEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserRoleEntityMapper;
import com.codeforge.ai.infrastructure.security.JwtTokenProvider;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuthApplicationServiceTest {

    private UserEntityMapper userEntityMapper;
    private UserRoleEntityMapper userRoleEntityMapper;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private WorkspaceApplicationService workspaceApplicationService;
    private AuthApplicationService authApplicationService;

    @BeforeEach
    void setUp() {
        userEntityMapper = mock(UserEntityMapper.class);
        userRoleEntityMapper = mock(UserRoleEntityMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        workspaceApplicationService = mock(WorkspaceApplicationService.class);
        authApplicationService = new AuthApplicationService(
                userEntityMapper,
                userRoleEntityMapper,
                passwordEncoder,
                jwtTokenProvider,
                workspaceApplicationService
        );
    }

    @Test
    void shouldReturnGenericConflictMessageWhenAccountExists() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setAccount("tester");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setDisplayName("Tester");
        request.setEmail("tester@example.com");
        given(userEntityMapper.findByAccount("tester")).willReturn(UserEntity.builder().id(1L).account("tester").build());

        assertThatThrownBy(() -> authApplicationService.register(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
                    assertThat(businessException.getMessage()).isEqualTo("账号或邮箱已存在");
                });
    }

    @Test
    void shouldReturnGenericConflictMessageWhenEmailExists() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setAccount("tester");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setDisplayName("Tester");
        request.setEmail("tester@example.com");
        given(userEntityMapper.findByAccount("tester")).willReturn(null);
        given(userEntityMapper.findByEmail("tester@example.com"))
                .willReturn(UserEntity.builder().id(2L).email("tester@example.com").build());

        assertThatThrownBy(() -> authApplicationService.register(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
                    assertThat(businessException.getMessage()).isEqualTo("账号或邮箱已存在");
                });
    }

    @Test
    void shouldGrantPlatformAdminToFirstRegisteredUser() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setAccount("admin");
        request.setPassword("admin12345");
        request.setConfirmPassword("admin12345");
        request.setDisplayName("System Admin");
        request.setEmail("admin@codeforge.ai");

        UserEntity persistedUser = UserEntity.builder()
                .id(1L)
                .account("admin")
                .displayName("System Admin")
                .email("admin@codeforge.ai")
                .status("ACTIVE")
                .build();

        given(userEntityMapper.findByAccount("admin")).willReturn(null, persistedUser);
        given(userEntityMapper.findByEmail("admin@codeforge.ai")).willReturn(null);
        given(userEntityMapper.countAllUsers()).willReturn(0L);
        given(passwordEncoder.encode("admin12345")).willReturn("encoded-admin12345");

        var response = authApplicationService.register(request);

        assertThat(response.user().account()).isEqualTo("admin");
        assertThat(response.platformRoles()).containsExactly("USER", "PLATFORM_ADMIN");
        verify(userRoleEntityMapper, times(2)).insert(any(UserRoleEntity.class));
        verify(workspaceApplicationService).getOrCreateDefaultWorkspace(any());
    }
}
