package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.user.CurrentUserResponse;
import com.codeforge.ai.application.dto.user.UserUpdateRequest;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.auth.entity.UserRoleEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserRoleEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private static final String EMAIL_CONFLICT_MESSAGE = "邮箱已存在";

    private final UserEntityMapper userEntityMapper;
    private final UserRoleEntityMapper userRoleEntityMapper;
    private final AuthApplicationService authApplicationService;

    public CurrentUserResponse getCurrentUser(CurrentUser currentUser) {
        UserEntity userEntity = userEntityMapper.selectOneById(currentUser.requiredUserId());
        if (userEntity == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return authApplicationService.toCurrentUserResponse(userEntity, loadPlatformRoles(userEntity.getId()));
    }

    @Transactional
    public CurrentUserResponse updateCurrentUser(CurrentUser currentUser, UserUpdateRequest request) {
        UserEntity existingUser = userEntityMapper.selectOneById(currentUser.requiredUserId());
        if (existingUser == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            UserEntity emailUser = userEntityMapper.findByEmail(request.getEmail());
            if (emailUser != null && !emailUser.getId().equals(existingUser.getId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, EMAIL_CONFLICT_MESSAGE);
            }
        }
        UserEntity updateEntity = UserEntity.builder()
                .id(existingUser.getId())
                .displayName(request.getDisplayName())
                .avatarUrl(request.getAvatarUrl())
                .email(request.getEmail())
                .phone(request.getPhone())
                .build();
        updateEntity.setUpdatedBy(existingUser.getId());
        userEntityMapper.updateProfile(updateEntity);
        UserEntity refreshed = userEntityMapper.selectOneById(existingUser.getId());
        return authApplicationService.toCurrentUserResponse(refreshed, loadPlatformRoles(existingUser.getId()));
    }

    private List<String> loadPlatformRoles(Long userId) {
        return userRoleEntityMapper.findByUserId(userId).stream()
                .map(UserRoleEntity::getRoleCode)
                .distinct()
                .toList();
    }
}
