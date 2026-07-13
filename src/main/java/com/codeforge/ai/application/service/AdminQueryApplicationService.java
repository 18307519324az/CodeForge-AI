package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.app.AiAppListItemResponse;
import com.codeforge.ai.application.dto.admin.AdminUserListItemResponse;
import com.codeforge.ai.application.dto.admin.AuditLogResponse;
import com.codeforge.ai.application.dto.admin.MetricSummaryResponse;
import com.codeforge.ai.application.dto.admin.ModelCallLogResponse;
import com.codeforge.ai.application.dto.admin.ModelProviderResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.auth.entity.UserRoleEntity;
import com.codeforge.ai.domain.generation.model.CredentialSource;
import com.codeforge.ai.domain.generation.model.ModelProviderRoutingConfigService;
import com.codeforge.ai.domain.generation.model.ProviderCredentialResolver;
import com.codeforge.ai.domain.metrics.entity.MetricDailyAggEntity;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AuditLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.MetricDailyAggEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserRoleEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.request.PageRequest;
import com.codeforge.ai.shared.response.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminQueryApplicationService {

    private static final long MAX_PAGE_SIZE = 200;
    private static final String REQUEST_COUNT = "requestCount";
    private static final String SUCCESS_COUNT = "successCount";
    private static final String FAILED_COUNT = "failedCount";
    private static final String SUCCESS_RATE = "successRate";
    private static final String TOKEN_INPUT = "tokenInput";
    private static final String TOKEN_OUTPUT = "tokenOutput";
    private static final String AVG_DURATION_MS = "avgDurationMs";

    private final AuditLogEntityMapper auditLogEntityMapper;
    private final AiAppEntityMapper aiAppEntityMapper;
    private final MetricDailyAggEntityMapper metricDailyAggEntityMapper;
    private final AdminMetricsApplicationService adminMetricsApplicationService;
    private final UserEntityMapper userEntityMapper;
    private final UserRoleEntityMapper userRoleEntityMapper;
    private final ModelProviderEntityMapper modelProviderEntityMapper;
    private final ModelCallLogEntityMapper modelCallLogEntityMapper;
    private final ModelProviderRoutingConfigService routingConfigService;
    private final ProviderCredentialResolver credentialResolver;

    public PageResponse<AdminUserListItemResponse> listUsers(CurrentUser currentUser, PageRequest pageRequest) {
        requirePlatformAdmin(currentUser);
        validatePageRequest(pageRequest);
        String keywordPattern = toKeywordPattern(pageRequest.getKeyword());
        long total = keywordPattern == null
                ? userEntityMapper.countAllUsers()
                : userEntityMapper.countByKeyword(keywordPattern);
        long offset = (pageRequest.getPageNo() - 1) * pageRequest.getPageSize();
        if (offset >= total) {
            return emptyPage(pageRequest, total);
        }
        List<UserEntity> users = userEntityMapper.findPage(offset, pageRequest.getPageSize(), keywordPattern);
        Map<Long, List<String>> userRoleMap = buildUserRoleMap(users);
        List<AdminUserListItemResponse> records = users.stream()
                .map(user -> new AdminUserListItemResponse(
                        user.getId(),
                        user.getAccount(),
                        user.getDisplayName(),
                        user.getEmail(),
                        user.getStatus(),
                        userRoleMap.getOrDefault(user.getId(), List.of()),
                        user.getCreatedAt(),
                        user.getLastLoginAt()
                ))
                .toList();
        return PageResponse.<AdminUserListItemResponse>builder()
                .records(records)
                .pageNo(pageRequest.getPageNo())
                .pageSize(pageRequest.getPageSize())
                .total(total)
                .build();
    }

    public PageResponse<AiAppListItemResponse> listAdminApps(CurrentUser currentUser, PageRequest pageRequest) {
        requirePlatformAdmin(currentUser);
        validatePageRequest(pageRequest);
        String keywordPattern = toKeywordPattern(pageRequest.getKeyword());
        long total = keywordPattern == null
                ? aiAppEntityMapper.countAllApps()
                : aiAppEntityMapper.countAdminApps(keywordPattern);
        long offset = (pageRequest.getPageNo() - 1) * pageRequest.getPageSize();
        if (offset >= total) {
            return emptyPage(pageRequest, total);
        }
        List<AiAppListItemResponse> records = aiAppEntityMapper
                .findAdminPage(offset, pageRequest.getPageSize(), keywordPattern)
                .stream()
                .map(this::toAdminAppListItem)
                .toList();
        return PageResponse.<AiAppListItemResponse>builder()
                .records(records)
                .pageNo(pageRequest.getPageNo())
                .pageSize(pageRequest.getPageSize())
                .total(total)
                .build();
    }

    public PageResponse<AuditLogResponse> listAuditLogs(CurrentUser currentUser, PageRequest pageRequest) {
        requirePlatformAdmin(currentUser);
        validatePageRequest(pageRequest);
        long total = auditLogEntityMapper.countAll();
        long offset = (pageRequest.getPageNo() - 1) * pageRequest.getPageSize();
        if (offset >= total) {
            return emptyPage(pageRequest, total);
        }
        List<AuditLogResponse> records = auditLogEntityMapper.findPage(offset, pageRequest.getPageSize()).stream()
                .map(entity -> new AuditLogResponse(
                        entity.getId(),
                        entity.getWorkspaceId(),
                        entity.getActorUserId(),
                        entity.getActionCode(),
                        entity.getTargetType(),
                        entity.getTargetId(),
                        entity.getRequestId(),
                        entity.getCreatedAt()
                ))
                .toList();
        return PageResponse.<AuditLogResponse>builder()
                .records(records)
                .pageNo(pageRequest.getPageNo())
                .pageSize(pageRequest.getPageSize())
                .total(total)
                .build();
    }

    public List<ModelProviderResponse> listModelProviders(CurrentUser currentUser) {
        requirePlatformAdmin(currentUser);
        return modelProviderEntityMapper.findAllProviders().stream()
                .filter(entity -> !com.codeforge.ai.domain.generation.model.ProviderCatalogSupport.isPseudoProvider(entity))
                .map(this::toModelProviderResponse)
                .toList();
    }

    public PageResponse<ModelCallLogResponse> listModelCallLogs(CurrentUser currentUser, PageRequest pageRequest) {
        requirePlatformAdmin(currentUser);
        validatePageRequest(pageRequest);
        String keywordPattern = toKeywordPattern(pageRequest.getKeyword());
        long total = keywordPattern == null
                ? modelCallLogEntityMapper.countAllLogs()
                : modelCallLogEntityMapper.countByKeyword(keywordPattern);
        long offset = (pageRequest.getPageNo() - 1) * pageRequest.getPageSize();
        if (offset >= total) {
            return emptyPage(pageRequest, total);
        }
        List<ModelCallLogResponse> records = modelCallLogEntityMapper.findPage(offset, pageRequest.getPageSize(), keywordPattern).stream()
                .map(this::toModelCallLogResponse)
                .toList();
        return PageResponse.<ModelCallLogResponse>builder()
                .records(records)
                .pageNo(pageRequest.getPageNo())
                .pageSize(pageRequest.getPageSize())
                .total(total)
                .build();
    }

    public MetricSummaryResponse getMetricsSummary(CurrentUser currentUser) {
        return adminMetricsApplicationService.getMetricsSummary(currentUser);
    }

    private void requirePlatformAdmin(CurrentUser currentUser) {
        if (!currentUser.isPlatformAdmin()) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }

    private Map<Long, List<String>> buildUserRoleMap(List<UserEntity> users) {
        if (users.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserRoleEntity> userRoles = userRoleEntityMapper.findByUserIds(users.stream()
                .map(UserEntity::getId)
                .toList());
        return userRoles.stream()
                .collect(Collectors.groupingBy(
                        UserRoleEntity::getUserId,
                        Collectors.mapping(UserRoleEntity::getRoleCode, Collectors.toList())
                ));
    }

    private AiAppListItemResponse toAdminAppListItem(AiAppEntity entity) {
        return new AiAppListItemResponse(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCoverUrl(),
                entity.getAppType(),
                entity.getStatus(),
                entity.getVisibility(),
                entity.getCurrentVersionId(),
                entity.getLatestTaskId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private ModelProviderResponse toModelProviderResponse(ModelProviderEntity entity) {
        CredentialSource source = CredentialSource.fromValue(entity.getCredentialSource());
        return new ModelProviderResponse(
                entity.getId(),
                entity.getProviderCode(),
                entity.getProviderName(),
                entity.getBaseUrl(),
                entity.getAuthMode(),
                entity.getSecretRef(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getDefaultModel(),
                source.code(),
                credentialResolver.isConfigured(entity),
                credentialResolver.maskedHint(entity),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ModelCallLogResponse toModelCallLogResponse(ModelCallLogEntity entity) {
        return new ModelCallLogResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getAppId(),
                entity.getProviderId(),
                entity.getProviderCode(),
                entity.getModelName(),
                entity.getRequestId(),
                entity.getStatus(),
                entity.getInputTokens(),
                entity.getOutputTokens(),
                entity.getDurationMs(),
                entity.getFallbackUsed(),
                entity.getGenerationSource(),
                entity.getPromptTemplateVersionId(),
                entity.getPromptTemplateCode(),
                entity.getPromptTemplateVersionNo(),
                entity.getErrorMessage(),
                entity.getCreatedAt()
        );
    }

    private Long longMetric(List<MetricDailyAggEntity> metrics, String metricKey) {
        return metrics.stream()
                .filter(entity -> metricKey.equals(entity.getMetricKey()))
                .map(MetricDailyAggEntity::getMetricValue)
                .findFirst()
                .map(BigDecimal::longValue)
                .orElse(0L);
    }

    private BigDecimal decimalMetric(List<MetricDailyAggEntity> metrics, String metricKey) {
        return metrics.stream()
                .filter(entity -> metricKey.equals(entity.getMetricKey()))
                .map(MetricDailyAggEntity::getMetricValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private void validatePageRequest(PageRequest pageRequest) {
        if (pageRequest.getPageNo() <= 0 || pageRequest.getPageSize() <= 0 || pageRequest.getPageSize() > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageNo/pageSize 非法");
        }
    }

    private String toKeywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim() + "%";
    }

    private <T> PageResponse<T> emptyPage(PageRequest pageRequest, long total) {
        return PageResponse.<T>builder()
                .records(List.of())
                .pageNo(pageRequest.getPageNo())
                .pageSize(pageRequest.getPageSize())
                .total(total)
                .build();
    }
}
