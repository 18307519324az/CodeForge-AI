package com.codeforge.ai.application.service;

import cn.hutool.core.util.IdUtil;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketplaceAuditService {

    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    public void recordPublish(Long actorUserId, AppPublicationEntity publication) {
        write(actorUserId, "MARKETPLACE_PUBLISH", publication);
    }

    public void recordRepublish(Long actorUserId, AppPublicationEntity publication) {
        write(actorUserId, "MARKETPLACE_REPUBLISH", publication);
    }

    public void recordUnpublish(Long actorUserId, AppPublicationEntity publication) {
        write(actorUserId, "MARKETPLACE_UNPUBLISH", publication);
    }

    public void recordArchive(Long actorUserId, AppPublicationEntity publication) {
        write(actorUserId, "MARKETPLACE_ARCHIVE", publication);
    }

    private void write(Long actorUserId, String actionCode, AppPublicationEntity publication) {
        auditLogWriter.insert(AuditLogEntity.builder()
                .id(IdUtil.getSnowflakeNextId())
                .actorUserId(actorUserId)
                .actionCode(actionCode)
                .targetType("app_publication")
                .targetId(String.valueOf(publication.getId()))
                .detailJson(buildDetail(publication))
                .build());
    }

    private String buildDetail(AppPublicationEntity publication) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("publicationId", publication.getId());
        detail.put("appId", publication.getAppId());
        detail.put("versionId", publication.getVersionId());
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            return "{\"publicationId\":" + publication.getId()
                    + ",\"appId\":" + publication.getAppId()
                    + ",\"versionId\":" + publication.getVersionId() + "}";
        }
    }
}
