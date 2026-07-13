package com.codeforge.ai.application.service;

import cn.hutool.core.util.IdUtil;
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
public class GeneratedArtifactRepairAuditService {

    public static final String ACTION_ARTIFACT_REPAIR = "ARTIFACT_REPAIR";
    public static final String REPAIR_POLICY_VERSION = "repair-policy-v2";

    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    public void recordSuccessfulRepair(Long actorUserId,
                                       Long appId,
                                       Long sourceVersionId,
                                       Long repairedVersionId,
                                       Integer repairedVersionNo) {
        auditLogWriter.insert(AuditLogEntity.builder()
                .id(IdUtil.getSnowflakeNextId())
                .actorUserId(actorUserId)
                .actionCode(ACTION_ARTIFACT_REPAIR)
                .targetType("ai_app")
                .targetId(String.valueOf(appId))
                .detailJson(buildDetail(actorUserId, appId, sourceVersionId, repairedVersionId, repairedVersionNo))
                .build());
    }

    private String buildDetail(Long actorUserId,
                               Long appId,
                               Long sourceVersionId,
                               Long repairedVersionId,
                               Integer repairedVersionNo) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("actorId", actorUserId);
        detail.put("appId", appId);
        detail.put("sourceVersionId", sourceVersionId);
        detail.put("repairedVersionId", repairedVersionId);
        detail.put("repairPolicyVersion", REPAIR_POLICY_VERSION);
        detail.put("versionNo", repairedVersionNo);
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            return "{\"actorId\":" + actorUserId
                    + ",\"appId\":" + appId
                    + ",\"sourceVersionId\":" + sourceVersionId
                    + ",\"repairedVersionId\":" + repairedVersionId
                    + ",\"repairPolicyVersion\":\"" + REPAIR_POLICY_VERSION + "\""
                    + ",\"versionNo\":" + repairedVersionNo + "}";
        }
    }
}
