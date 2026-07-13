package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.codeforge.ai.domain.app.enums.AiAppStatus;
import com.codeforge.ai.domain.app.enums.AppPublicationStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketplacePublicationAccessGuard {

    private final AiAppEntityMapper aiAppEntityMapper;

    public AppPublicationEntity requirePubliclyAccessible(AppPublicationEntity publication) {
        if (publication == null || !AppPublicationStatus.PUBLISHED.equals(publication.getStatus())) {
            throw new BusinessException(ErrorCode.PUBLICATION_NOT_FOUND);
        }
        ErrorCode appError = resolveAppPublicAccessError(publication.getAppId());
        if (appError != null) {
            throw new BusinessException(appError);
        }
        return publication;
    }

    public ErrorCode resolvePublicAccessError(AppPublicationEntity publication) {
        if (publication == null) {
            return ErrorCode.PUBLICATION_NOT_FOUND;
        }
        if (!AppPublicationStatus.PUBLISHED.equals(publication.getStatus())) {
            return ErrorCode.PUBLICATION_NOT_PUBLISHED;
        }
        return resolveAppPublicAccessError(publication.getAppId());
    }

    public ErrorCode resolveAppPublicAccessError(Long appId) {
        if (appId == null) {
            return ErrorCode.PUBLICATION_NOT_FOUND;
        }
        AiAppEntity appEntity = aiAppEntityMapper.selectOneById(appId);
        if (appEntity == null || isDeleted(appEntity)) {
            return ErrorCode.PUBLICATION_NOT_FOUND;
        }
        if (AiAppStatus.ARCHIVED.name().equals(appEntity.getStatus())) {
            return ErrorCode.PUBLICATION_NOT_FOUND;
        }
        return null;
    }

    private static boolean isDeleted(AiAppEntity appEntity) {
        return appEntity.getIsDeleted() != null && appEntity.getIsDeleted() != 0;
    }
}
