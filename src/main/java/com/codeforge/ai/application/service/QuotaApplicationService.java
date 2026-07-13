package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.QuotaAdjustRequest;
import com.codeforge.ai.application.dto.quota.QuotaUsageLogResponse;
import com.codeforge.ai.application.dto.quota.UserQuotaResponse;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.quota.entity.QuotaUsageLogEntity;
import com.codeforge.ai.domain.quota.entity.UserQuotaEntity;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.QuotaUsageLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserQuotaEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.response.ResultUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuotaApplicationService {

    private final UserQuotaEntityMapper userQuotaEntityMapper;
    private final QuotaUsageLogEntityMapper quotaUsageLogEntityMapper;
    private final AuditLogWriter auditLogWriter;
    private final UserEntityMapper userEntityMapper;
    private final WorkspaceAccessService workspaceAccessService;
    private final ObjectMapper objectMapper;

    public List<UserQuotaResponse> getMyQuotas(CurrentUser currentUser) {
        return userQuotaEntityMapper.findByUserId(currentUser.requiredUserId()).stream()
                .map(this::toQuotaResponse)
                .toList();
    }

    @Transactional
    public UserQuotaResponse adjustQuota(CurrentUser currentUser, QuotaAdjustRequest request) {
        requirePlatformAdmin(currentUser);
        UserEntity userEntity = userEntityMapper.selectOneById(request.getUserId());
        if (userEntity == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        workspaceAccessService.requireWorkspace(request.getWorkspaceId());

        UserQuotaEntity existingQuota = userQuotaEntityMapper.findByUserIdAndWorkspaceId(request.getUserId(), request.getWorkspaceId());
        if (existingQuota == null) {
            existingQuota = UserQuotaEntity.builder()
                    .userId(request.getUserId())
                    .workspaceId(request.getWorkspaceId())
                    .dailyRequestLimit(defaultInt(request.getDailyRequestLimit()))
                    .dailyTokenLimit(defaultInt(request.getDailyTokenLimit()))
                    .monthlyCostLimit(defaultDecimal(request.getMonthlyCostLimit()))
                    .status("ACTIVE")
                    .effectiveFrom(LocalDateTime.now())
                    .build();
            existingQuota.setCreatedBy(currentUser.requiredUserId());
            existingQuota.setUpdatedBy(currentUser.requiredUserId());
            userQuotaEntityMapper.insert(existingQuota);
        } else {
            existingQuota.setDailyRequestLimit(defaultInt(request.getDailyRequestLimit()));
            existingQuota.setDailyTokenLimit(defaultInt(request.getDailyTokenLimit()));
            existingQuota.setMonthlyCostLimit(defaultDecimal(request.getMonthlyCostLimit()));
            existingQuota.setStatus("ACTIVE");
            existingQuota.setEffectiveFrom(existingQuota.getEffectiveFrom() == null ? LocalDateTime.now() : existingQuota.getEffectiveFrom());
            existingQuota.setUpdatedBy(currentUser.requiredUserId());
            userQuotaEntityMapper.updateQuota(existingQuota);
        }

        quotaUsageLogEntityMapper.insert(QuotaUsageLogEntity.builder()
                .quotaId(existingQuota.getId())
                .taskId(null)
                .usageType("ADMIN_ADJUST")
                .requestCount(0)
                .tokenCount(0)
                .costAmount(BigDecimal.ZERO)
                .build());

        auditLogWriter.insert(AuditLogEntity.builder()
                .workspaceId(request.getWorkspaceId())
                .actorUserId(currentUser.requiredUserId())
                .actionCode("QUOTA_ADJUST")
                .targetType("USER_QUOTA")
                .targetId(String.valueOf(existingQuota.getId()))
                .requestId(ResultUtils.currentRequestId())
                .detailJson(buildAuditDetailJson(existingQuota))
                .build());

        return toQuotaResponse(existingQuota);
    }

    public List<QuotaUsageLogResponse> listQuotaUsageLogs(CurrentUser currentUser) {
        requirePlatformAdmin(currentUser);
        return quotaUsageLogEntityMapper.findLatestLogs().stream()
                .map(entity -> new QuotaUsageLogResponse(
                        entity.getId(),
                        entity.getQuotaId(),
                        entity.getTaskId(),
                        entity.getUsageType(),
                        entity.getRequestCount(),
                        entity.getTokenCount(),
                        entity.getCostAmount(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    public void assertQuotaAvailable(Long userId, Long workspaceId) {
        UserQuotaEntity quotaEntity = userQuotaEntityMapper.findByUserIdAndWorkspaceId(userId, workspaceId);
        if (quotaEntity == null || !"ACTIVE".equals(quotaEntity.getStatus())) {
            return;
        }
        if (quotaEntity.getDailyRequestLimit() == null || quotaEntity.getDailyRequestLimit() <= 0) {
            return;
        }
        int usedRequestCount = quotaUsageLogEntityMapper.findByQuotaIdSince(
                        quotaEntity.getId(),
                        LocalDateTime.now().toLocalDate().atStartOfDay())
                .stream()
                .mapToInt(log -> log.getRequestCount() == null ? 0 : log.getRequestCount())
                .sum();
        if (usedRequestCount + 1 > quotaEntity.getDailyRequestLimit()) {
            throw new BusinessException(ErrorCode.QUOTA_NOT_ENOUGH);
        }
    }

    public void recordTaskQuotaUsage(Long userId, Long workspaceId, Long taskId) {
        UserQuotaEntity quotaEntity = userQuotaEntityMapper.findByUserIdAndWorkspaceId(userId, workspaceId);
        if (quotaEntity == null || !"ACTIVE".equals(quotaEntity.getStatus())) {
            return;
        }
        quotaUsageLogEntityMapper.insert(QuotaUsageLogEntity.builder()
                .quotaId(quotaEntity.getId())
                .taskId(taskId)
                .usageType("GENERATION_TASK_CREATE")
                .requestCount(1)
                .tokenCount(0)
                .costAmount(BigDecimal.ZERO)
                .build());
    }

    private void requirePlatformAdmin(CurrentUser currentUser) {
        if (!currentUser.isPlatformAdmin()) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }

    private UserQuotaResponse toQuotaResponse(UserQuotaEntity entity) {
        return new UserQuotaResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getWorkspaceId(),
                entity.getDailyRequestLimit(),
                entity.getDailyTokenLimit(),
                entity.getMonthlyCostLimit(),
                entity.getStatus(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                entity.getUpdatedAt()
        );
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String buildAuditDetailJson(UserQuotaEntity quotaEntity) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "quotaId", quotaEntity.getId(),
                    "userId", quotaEntity.getUserId(),
                    "workspaceId", quotaEntity.getWorkspaceId(),
                    "dailyRequestLimit", quotaEntity.getDailyRequestLimit(),
                    "dailyTokenLimit", quotaEntity.getDailyTokenLimit(),
                    "monthlyCostLimit", quotaEntity.getMonthlyCostLimit(),
                    "status", quotaEntity.getStatus()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Quota audit detail serialization failed", exception);
        }
    }
}
