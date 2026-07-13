package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.auth.LoginResponse;
import com.codeforge.ai.application.dto.auth.RegisterResponse;
import com.codeforge.ai.application.dto.auth.UserLoginRequest;
import com.codeforge.ai.application.dto.auth.UserRegisterRequest;
import com.codeforge.ai.application.dto.user.CurrentUserResponse;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.auth.entity.UserRoleEntity;
import com.codeforge.ai.domain.auth.enums.PlatformRole;
import com.codeforge.ai.domain.auth.enums.UserStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserRoleEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.infrastructure.security.JwtTokenProvider;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private static final String REGISTER_CONFLICT_MESSAGE = "账号或邮箱已存在";

    private final UserEntityMapper userEntityMapper;
    private final UserRoleEntityMapper userRoleEntityMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final WorkspaceApplicationService workspaceApplicationService;

    @Transactional
    public RegisterResponse register(UserRegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "password 与 confirmPassword 不一致");
        }
        if (userEntityMapper.findByAccount(request.getAccount()) != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, REGISTER_CONFLICT_MESSAGE);
        }
        if (request.getEmail() != null && !request.getEmail().isBlank() && userEntityMapper.findByEmail(request.getEmail()) != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, REGISTER_CONFLICT_MESSAGE);
        }
        boolean firstUser = userEntityMapper.countAllUsers() == 0;

        UserEntity userEntity = UserEntity.builder()
                .account(request.getAccount())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .email(request.getEmail())
                .status(UserStatus.ACTIVE.name())
                .build();
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setUpdatedAt(LocalDateTime.now());
        userEntityMapper.insertUser(userEntity);
        // Reload to get generated ID
        userEntity = userEntityMapper.findByAccount(request.getAccount());

        UserRoleEntity userRoleEntity = UserRoleEntity.builder()
                .userId(userEntity.getId())
                .roleCode(PlatformRole.USER.name())
                .build();
        userRoleEntity.setCreatedBy(userEntity.getId());
        userRoleEntity.setUpdatedBy(userEntity.getId());
        userRoleEntity.setCreatedAt(LocalDateTime.now());
        userRoleEntity.setUpdatedAt(LocalDateTime.now());
        userRoleEntityMapper.insert(userRoleEntity);
        List<String> platformRoles = new ArrayList<>();
        platformRoles.add(PlatformRole.USER.name());

        if (firstUser) {
            UserRoleEntity adminRoleEntity = UserRoleEntity.builder()
                    .userId(userEntity.getId())
                    .roleCode(PlatformRole.PLATFORM_ADMIN.name())
                    .build();
            adminRoleEntity.setCreatedBy(userEntity.getId());
            adminRoleEntity.setUpdatedBy(userEntity.getId());
            adminRoleEntity.setCreatedAt(LocalDateTime.now());
            adminRoleEntity.setUpdatedAt(LocalDateTime.now());
            userRoleEntityMapper.insert(adminRoleEntity);
            platformRoles.add(PlatformRole.PLATFORM_ADMIN.name());
        }

        workspaceApplicationService.getOrCreateDefaultWorkspace(
                new CurrentUser(userEntity.getId(), userEntity.getAccount(), platformRoles));

        return new RegisterResponse(toCurrentUserResponse(userEntity, platformRoles), platformRoles);
    }

    @Transactional
    public LoginResponse login(UserLoginRequest request) {
        UserEntity userEntity = userEntityMapper.findByAccount(request.getAccount());
        if (userEntity == null || !passwordEncoder.matches(request.getPassword(), userEntity.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (!UserStatus.ACTIVE.name().equals(userEntity.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN, "用户已被禁用");
        }

        List<String> platformRoles = userRoleEntityMapper.findByUserId(userEntity.getId()).stream()
                .map(UserRoleEntity::getRoleCode)
                .distinct()
                .toList();
        CurrentUser currentUser = new CurrentUser(userEntity.getId(), userEntity.getAccount(), platformRoles);
        String accessToken = jwtTokenProvider.createAccessToken(currentUser);
        LocalDateTime lastLoginAt = LocalDateTime.now();
        userEntityMapper.updateLastLogin(userEntity.getId(), lastLoginAt, userEntity.getId());
        userEntity.setLastLoginAt(lastLoginAt);
        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpireSeconds(),
                toCurrentUserResponse(userEntity, platformRoles),
                platformRoles
        );
    }

    public CurrentUserResponse toCurrentUserResponse(UserEntity userEntity, List<String> platformRoles) {
        return new CurrentUserResponse(
                userEntity.getId(),
                userEntity.getAccount(),
                userEntity.getDisplayName(),
                userEntity.getAvatarUrl(),
                userEntity.getEmail(),
                userEntity.getPhone(),
                userEntity.getStatus(),
                userEntity.getLastLoginAt(),
                platformRoles
        );
    }
}
