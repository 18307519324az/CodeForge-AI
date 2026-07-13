package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.prompt.PromptTemplateCreateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateDetailResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateListItemResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplatePublishedVersionResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateQueryRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUpdateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUserDetailResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUserListItemResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionCreateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionUpdateRequest;
import com.codeforge.ai.application.dto.prompt.PublishedPromptTemplateQueryRequest;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.domain.prompt.model.PromptTemplateRenderer;
import com.codeforge.ai.domain.prompt.model.PromptTemplateSceneCatalog;
import com.codeforge.ai.domain.prompt.model.PromptTemplateVariableSchemaParser;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateStatus;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateVersionStatus;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.request.PageRequest;
import com.codeforge.ai.shared.response.PageResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromptTemplateApplicationService {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final PromptTemplateEntityMapper promptTemplateEntityMapper;
    private final PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private final GenerationRecordEntityMapper generationRecordEntityMapper;
    private final ModelCallLogEntityMapper modelCallLogEntityMapper;
    private final WorkspaceAccessService workspaceAccessService;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    @Transactional
    public PromptTemplateDetailResponse createTemplate(CurrentUser currentUser, PromptTemplateCreateRequest request) {
        workspaceAccessService.requireEditorAccess(currentUser, request.getWorkspaceId());
        if (promptTemplateEntityMapper.findByWorkspaceIdAndTemplateName(request.getWorkspaceId(), request.getTemplateName()) != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "templateName 已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        PromptTemplateEntity templateEntity = PromptTemplateEntity.builder()
                .workspaceId(request.getWorkspaceId())
                .templateName(request.getTemplateName())
                .templateScene(request.getTemplateScene())
                .status(PromptTemplateStatus.DRAFT.name())
                .currentVersionNo(1)
                .remark(request.getRemark())
                .build();
        templateEntity.setCreatedBy(currentUser.requiredUserId());
        templateEntity.setUpdatedBy(currentUser.requiredUserId());
        templateEntity.setCreatedAt(now);
        templateEntity.setUpdatedAt(now);
        templateEntity.setIsDeleted(0);
        promptTemplateEntityMapper.insertTemplate(templateEntity);

        PromptTemplateVersionEntity versionEntity = PromptTemplateVersionEntity.builder()
                .templateId(templateEntity.getId())
                .versionNo(1)
                .systemPrompt(request.getSystemPrompt())
                .userPrompt(request.getUserPrompt())
                .status(PromptTemplateVersionStatus.DRAFT.name())
                .build();
        versionEntity.setCreatedBy(currentUser.requiredUserId());
        versionEntity.setUpdatedBy(currentUser.requiredUserId());
        versionEntity.setCreatedAt(now);
        versionEntity.setUpdatedAt(now);
        versionEntity.setIsDeleted(0);
        promptTemplateVersionEntityMapper.insertVersion(versionEntity);
        writeTemplateAudit(currentUser.requiredUserId(), templateEntity.getWorkspaceId(),
                "PROMPT_TEMPLATE_CREATE", templateEntity.getId(), versionEntity.getId());
        return toDetailResponse(templateEntity, versionEntity);
    }

    public PageResponse<PromptTemplateUserListItemResponse> listPublishedTemplates(
            CurrentUser currentUser, PublishedPromptTemplateQueryRequest request) {
        validatePageRequest(request);
        List<Long> workspaceIds = resolveWorkspaceIds(currentUser, null);
        if (workspaceIds.isEmpty()) {
            return emptyUserPage(request);
        }
        List<PromptTemplateEntity> publishedTemplates = promptTemplateEntityMapper.findAccessibleTemplates(
                        workspaceIds, request.getKeyword(), request.getTemplateScene(), PromptTemplateStatus.PUBLISHED.name())
                .stream()
                .filter(entity -> PromptTemplateStatus.PUBLISHED.name().equals(entity.getStatus()))
                .toList();
        if (publishedTemplates.isEmpty()) {
            return emptyUserPage(request);
        }
        List<Long> templateIds = publishedTemplates.stream().map(PromptTemplateEntity::getId).toList();
        Map<String, PromptTemplateVersionEntity> publishedVersionIndex = promptTemplateVersionEntityMapper
                .findPublishedVersionsByTemplateIds(templateIds)
                .stream()
                .collect(Collectors.toMap(
                        version -> versionKey(version.getTemplateId(), version.getVersionNo()),
                        Function.identity(),
                        (left, right) -> left));
        List<PromptTemplateUserListItemResponse> allRecords = publishedTemplates.stream()
                .map(entity -> toUserListItem(entity, publishedVersionIndex.get(versionKey(entity.getId(), entity.getCurrentVersionNo()))))
                .filter(item -> item.publishedVersionId() != null)
                .toList();
        return paginateUser(allRecords, request);
    }

    public PromptTemplateUserDetailResponse getPublishedTemplate(CurrentUser currentUser, Long templateId) {
        PromptTemplateEntity templateEntity = requireReadableTemplate(currentUser, templateId);
        if (!PromptTemplateStatus.PUBLISHED.name().equals(templateEntity.getStatus())) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        PromptTemplateVersionEntity versionEntity = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(
                templateEntity.getId(), templateEntity.getCurrentVersionNo());
        if (versionEntity == null || !versionEntity.isEffectivelyPublished()) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "模板尚未发布可用版本");
        }
        return toUserDetailResponse(templateEntity, versionEntity);
    }

    public PromptTemplateVersionEntity requirePublishedVersionForUser(CurrentUser currentUser, Long templateId, Long versionId) {
        PromptTemplateEntity templateEntity = requireReadableTemplate(currentUser, templateId);
        if (!PromptTemplateStatus.PUBLISHED.name().equals(templateEntity.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Prompt 模板未发布，无法用于生产生成");
        }
        PromptTemplateVersionEntity versionEntity = promptTemplateVersionEntityMapper.selectOneById(versionId);
        if (versionEntity == null || !templateId.equals(versionEntity.getTemplateId())) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "Prompt 模板版本不存在");
        }
        if (!versionEntity.isEffectivelyPublished()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅已发布版本可用于生产生成");
        }
        return versionEntity;
    }

    public void validateTemplateVariables(PromptTemplateVersionEntity versionEntity, Map<String, String> templateVariables) {
        if (versionEntity == null) {
            return;
        }
        PromptTemplateRenderer.validateRequiredVariables(
                versionEntity.getSystemPrompt(),
                versionEntity.getUserPrompt(),
                templateVariables);
    }

    public PromptTemplateUserListItemResponse getPublishedTemplateListItem(CurrentUser currentUser, Long templateId) {
        PromptTemplateEntity templateEntity = requireReadableTemplate(currentUser, templateId);
        if (!PromptTemplateStatus.PUBLISHED.name().equals(templateEntity.getStatus())) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        PromptTemplateVersionEntity versionEntity = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(
                templateEntity.getId(), templateEntity.getCurrentVersionNo());
        PromptTemplateUserListItemResponse item = toUserListItem(templateEntity, versionEntity);
        if (item.publishedVersionId() == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "模板尚未发布可用版本");
        }
        return item;
    }

    public PageResponse<PromptTemplateListItemResponse> listTemplates(CurrentUser currentUser, PromptTemplateQueryRequest request) {
        validatePageRequest(request);
        List<Long> workspaceIds = resolveWorkspaceIds(currentUser, request.getWorkspaceId());
        if (workspaceIds.isEmpty()) {
            return PageResponse.<PromptTemplateListItemResponse>builder()
                    .records(List.of())
                    .pageNo(request.getPageNo())
                    .pageSize(request.getPageSize())
                    .total(0)
                    .build();
        }
        List<PromptTemplateListItemResponse> allRecords = promptTemplateEntityMapper.findAccessibleTemplates(
                        workspaceIds, request.getKeyword(), request.getTemplateScene(), request.getStatus())
                .stream()
                .map(this::toListItemResponse)
                .toList();
        return paginate(allRecords, request);
    }

    public PromptTemplateDetailResponse getTemplate(CurrentUser currentUser, Long templateId) {
        PromptTemplateEntity templateEntity = requireReadableTemplate(currentUser, templateId);
        PromptTemplateVersionEntity currentVersion = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(
                templateEntity.getId(), templateEntity.getCurrentVersionNo());
        return toDetailResponse(templateEntity, currentVersion);
    }

    public List<PromptTemplatePublishedVersionResponse> listPublishedTemplateVersions(CurrentUser currentUser,
                                                                                      Long templateId) {
        PromptTemplateEntity templateEntity = requireReadableTemplate(currentUser, templateId);
        if (!PromptTemplateStatus.PUBLISHED.name().equals(templateEntity.getStatus())) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        return promptTemplateVersionEntityMapper.findByTemplateId(templateId).stream()
                .filter(PromptTemplateVersionEntity::isEffectivelyPublished)
                .sorted((left, right) -> Integer.compare(right.getVersionNo(), left.getVersionNo()))
                .map(version -> new PromptTemplatePublishedVersionResponse(
                        version.getId(), version.getVersionNo(), version.getPublishedAt()))
                .toList();
    }

    public List<PromptTemplateVersionResponse> listTemplateVersions(CurrentUser currentUser, Long templateId) {
        requireEditableTemplate(currentUser, templateId);
        return promptTemplateVersionEntityMapper.findByTemplateId(templateId).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional
    public PromptTemplateDetailResponse updateTemplate(CurrentUser currentUser, Long templateId, PromptTemplateUpdateRequest request) {
        PromptTemplateEntity templateEntity = requireEditableTemplate(currentUser, templateId);
        PromptTemplateEntity duplicate = promptTemplateEntityMapper.findByWorkspaceIdAndTemplateNameExcludingId(
                templateEntity.getWorkspaceId(), request.getTemplateName(), templateEntity.getId());
        if (duplicate != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "templateName 已存在");
        }
        PromptTemplateEntity updateEntity = PromptTemplateEntity.builder()
                .id(templateEntity.getId())
                .templateName(request.getTemplateName())
                .templateScene(request.getTemplateScene())
                .remark(request.getRemark())
                .build();
        updateEntity.setUpdatedBy(currentUser.requiredUserId());
        promptTemplateEntityMapper.updateTemplate(updateEntity);
        PromptTemplateEntity refreshed = promptTemplateEntityMapper.selectOneById(templateId);
        PromptTemplateVersionEntity currentVersion = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(
                refreshed.getId(), refreshed.getCurrentVersionNo());
        return toDetailResponse(refreshed, currentVersion);
    }

    @Transactional
    public PromptTemplateVersionResponse createTemplateVersion(
            CurrentUser currentUser, Long templateId, PromptTemplateVersionCreateRequest request) {
        PromptTemplateEntity templateEntity = requireEditableTemplate(currentUser, templateId);
        validateOptionalJson(request.getVariablesJson(), "variablesJson");
        validateOptionalJson(request.getModelStrategyJson(), "modelStrategyJson");
        int nextVersionNo = promptTemplateVersionEntityMapper.findMaxVersionNo(templateId) + 1;
        LocalDateTime now = LocalDateTime.now();
        PromptTemplateVersionEntity versionEntity = PromptTemplateVersionEntity.builder()
                .templateId(templateEntity.getId())
                .versionNo(nextVersionNo)
                .systemPrompt(request.getSystemPrompt())
                .userPrompt(request.getUserPrompt())
                .variablesJson(request.getVariablesJson())
                .modelStrategyJson(request.getModelStrategyJson())
                .status(PromptTemplateVersionStatus.DRAFT.name())
                .build();
        versionEntity.setCreatedBy(currentUser.requiredUserId());
        versionEntity.setUpdatedBy(currentUser.requiredUserId());
        versionEntity.setCreatedAt(now);
        versionEntity.setUpdatedAt(now);
        versionEntity.setIsDeleted(0);
        promptTemplateVersionEntityMapper.insertVersion(versionEntity);
        writeTemplateAudit(currentUser.requiredUserId(), templateEntity.getWorkspaceId(),
                "PROMPT_TEMPLATE_VERSION_CREATE", templateId, versionEntity.getId());
        return toVersionResponse(versionEntity);
    }

    @Transactional
    public PromptTemplateDetailResponse publishTemplateVersion(CurrentUser currentUser, Long templateId, Integer versionNo) {
        PromptTemplateEntity templateEntity = requireAdminTemplate(currentUser, templateId);
        PromptTemplateVersionEntity versionEntity = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(
                templateId, versionNo);
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        if (!templateId.equals(versionEntity.getTemplateId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "promptTemplateVersionId 与 promptTemplateId 不匹配");
        }
        Long actorUserId = currentUser.requiredUserId();
        boolean legacyInconsistent = !versionEntity.isCanonicallyPublished() && versionEntity.hasLegacyPublishedMarker();
        boolean freshDraft = !versionEntity.isCanonicallyPublished() && !versionEntity.hasLegacyPublishedMarker();

        if (freshDraft || legacyInconsistent) {
            LocalDateTime publishAt = legacyInconsistent ? versionEntity.getPublishedAt() : LocalDateTime.now();
            Long publishBy = versionEntity.getPublishedBy() != null ? versionEntity.getPublishedBy() : actorUserId;
            promptTemplateVersionEntityMapper.markPublished(
                    versionEntity.getId(), publishBy, publishAt, actorUserId);
            int latestPointer = promptTemplateVersionEntityMapper.findMaxEffectivelyPublishedVersionNo(templateId);
            promptTemplateEntityMapper.updatePublishedVersion(
                    templateEntity.getId(), PromptTemplateStatus.PUBLISHED.name(), latestPointer, actorUserId);
            String actionCode = legacyInconsistent
                    ? "PROMPT_TEMPLATE_VERSION_RECONCILED"
                    : "PROMPT_TEMPLATE_VERSION_PUBLISH";
            writeTemplateAudit(actorUserId, templateEntity.getWorkspaceId(),
                    actionCode, templateId, versionEntity.getId(), versionNo, legacyInconsistent);
            PromptTemplateEntity refreshedTemplate = promptTemplateEntityMapper.selectOneById(templateId);
            PromptTemplateVersionEntity refreshedVersion = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(
                    templateId, versionNo);
            return toDetailResponse(refreshedTemplate, refreshedVersion);
        }

        int latestPointer = promptTemplateVersionEntityMapper.findMaxEffectivelyPublishedVersionNo(templateId);
        boolean pointerCorrect = PromptTemplateStatus.PUBLISHED.name().equals(templateEntity.getStatus())
                && templateEntity.getCurrentVersionNo() != null
                && templateEntity.getCurrentVersionNo() == latestPointer;
        if (pointerCorrect) {
            return toDetailResponse(templateEntity, versionEntity);
        }
        promptTemplateEntityMapper.updatePublishedVersion(
                templateEntity.getId(), PromptTemplateStatus.PUBLISHED.name(), latestPointer, actorUserId);
        PromptTemplateEntity refreshedTemplate = promptTemplateEntityMapper.selectOneById(templateId);
        return toDetailResponse(refreshedTemplate, versionEntity);
    }

    public PromptTemplateVersionResponse getTemplateVersion(CurrentUser currentUser, Long templateId, Integer versionNo) {
        requireReadableTemplate(currentUser, templateId);
        PromptTemplateVersionEntity versionEntity = requireExistingVersion(templateId, versionNo);
        return toVersionResponse(versionEntity);
    }

    @Transactional
    public PromptTemplateVersionResponse updateTemplateVersion(
            CurrentUser currentUser, Long templateId, Integer versionNo, PromptTemplateVersionUpdateRequest request) {
        requireEditableTemplate(currentUser, templateId);
        PromptTemplateVersionEntity versionEntity = requireExistingVersion(templateId, versionNo);
        ensureDraftVersion(versionEntity);
        validateOptionalJson(request.getVariablesJson(), "variablesJson");
        validateOptionalJson(request.getModelStrategyJson(), "modelStrategyJson");
        PromptTemplateVersionEntity updateEntity = PromptTemplateVersionEntity.builder()
                .id(versionEntity.getId())
                .systemPrompt(request.getSystemPrompt())
                .userPrompt(request.getUserPrompt())
                .variablesJson(request.getVariablesJson())
                .modelStrategyJson(request.getModelStrategyJson())
                .build();
        updateEntity.setUpdatedBy(currentUser.requiredUserId());
        int updated = promptTemplateVersionEntityMapper.updateDraftVersion(updateEntity);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "仅未发布版本允许编辑");
        }
        return toVersionResponse(promptTemplateVersionEntityMapper.selectOneById(versionEntity.getId()));
    }

    @Transactional
    public void deleteTemplateVersion(CurrentUser currentUser, Long templateId, Integer versionNo) {
        requireEditableTemplate(currentUser, templateId);
        PromptTemplateVersionEntity versionEntity = requireExistingVersion(templateId, versionNo);
        ensureDraftVersion(versionEntity);
        ensureVersionNotReferenced(versionEntity.getId());
        int versionCount = promptTemplateVersionEntityMapper.countByTemplateId(templateId);
        if (versionCount <= 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "模板至少保留一个版本");
        }
        promptTemplateVersionEntityMapper.deleteById(versionEntity.getId());
    }

    @Transactional
    public PromptTemplateDetailResponse archiveTemplate(CurrentUser currentUser, Long templateId) {
        PromptTemplateEntity templateEntity = requireAdminTemplate(currentUser, templateId);
        if (!PromptTemplateStatus.PUBLISHED.name().equals(templateEntity.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "仅已发布模板允许归档");
        }
        promptTemplateEntityMapper.updateStatus(
                templateEntity.getId(), PromptTemplateStatus.ARCHIVED.name(), currentUser.requiredUserId());
        PromptTemplateEntity refreshed = promptTemplateEntityMapper.selectOneById(templateId);
        PromptTemplateVersionEntity currentVersion = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(
                refreshed.getId(), refreshed.getCurrentVersionNo());
        writeTemplateAudit(currentUser.requiredUserId(), templateEntity.getWorkspaceId(),
                "PROMPT_TEMPLATE_ARCHIVE", templateId, currentVersion == null ? null : currentVersion.getId());
        return toDetailResponse(refreshed, currentVersion);
    }

    @Transactional
    public void deleteTemplate(CurrentUser currentUser, Long templateId) {
        PromptTemplateEntity templateEntity = requireAdminTemplate(currentUser, templateId);
        if (!PromptTemplateStatus.DRAFT.name().equals(templateEntity.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "仅草稿模板允许删除，已发布模板请归档");
        }
        ensureTemplateNotReferenced(templateId);
        List<PromptTemplateVersionEntity> versions = promptTemplateVersionEntityMapper.findByTemplateId(templateId);
        for (PromptTemplateVersionEntity version : versions) {
            ensureVersionNotReferenced(version.getId());
            promptTemplateVersionEntityMapper.deleteById(version.getId());
        }
        promptTemplateEntityMapper.deleteById(templateId);
    }

    private PromptTemplateEntity requireReadableTemplate(CurrentUser currentUser, Long templateId) {
        PromptTemplateEntity templateEntity = promptTemplateEntityMapper.selectOneById(templateId);
        if (templateEntity == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        workspaceAccessService.requireReadAccess(currentUser, templateEntity.getWorkspaceId());
        return templateEntity;
    }

    private PromptTemplateEntity requireEditableTemplate(CurrentUser currentUser, Long templateId) {
        PromptTemplateEntity templateEntity = promptTemplateEntityMapper.selectOneById(templateId);
        if (templateEntity == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        workspaceAccessService.requireEditorAccess(currentUser, templateEntity.getWorkspaceId());
        return templateEntity;
    }

    private PromptTemplateEntity requireAdminTemplate(CurrentUser currentUser, Long templateId) {
        PromptTemplateEntity templateEntity = promptTemplateEntityMapper.selectOneById(templateId);
        if (templateEntity == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        workspaceAccessService.requireAdminAccess(currentUser, templateEntity.getWorkspaceId());
        return templateEntity;
    }

    private List<Long> resolveWorkspaceIds(CurrentUser currentUser, Long workspaceId) {
        if (workspaceId != null) {
            workspaceAccessService.requireReadAccess(currentUser, workspaceId);
            return List.of(workspaceId);
        }
        return workspaceAccessService.listReadableWorkspaceIds(currentUser);
    }

    private void validatePageRequest(PageRequest pageRequest) {
        if (pageRequest.getPageNo() <= 0 || pageRequest.getPageSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageNo/pageSize 非法");
        }
    }

    private PageResponse<PromptTemplateListItemResponse> paginate(
            List<PromptTemplateListItemResponse> allRecords, PageRequest pageRequest) {
        int fromIndex = (int) ((pageRequest.getPageNo() - 1) * pageRequest.getPageSize());
        if (fromIndex >= allRecords.size()) {
            return PageResponse.<PromptTemplateListItemResponse>builder()
                    .records(List.of())
                    .pageNo(pageRequest.getPageNo())
                    .pageSize(pageRequest.getPageSize())
                    .total(allRecords.size())
                    .build();
        }
        int toIndex = Math.min(allRecords.size(), fromIndex + (int) pageRequest.getPageSize());
        return PageResponse.<PromptTemplateListItemResponse>builder()
                .records(allRecords.subList(fromIndex, toIndex))
                .pageNo(pageRequest.getPageNo())
                .pageSize(pageRequest.getPageSize())
                .total(allRecords.size())
                .build();
    }

    private PromptTemplateListItemResponse toListItemResponse(PromptTemplateEntity entity) {
        return new PromptTemplateListItemResponse(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getTemplateName(),
                entity.getTemplateScene(),
                entity.getStatus(),
                entity.getCurrentVersionNo(),
                promptTemplateVersionEntityMapper.countByTemplateId(entity.getId()),
                entity.getUpdatedAt()
        );
    }

    private PromptTemplateDetailResponse toDetailResponse(
            PromptTemplateEntity templateEntity, PromptTemplateVersionEntity versionEntity) {
        return new PromptTemplateDetailResponse(
                templateEntity.getId(),
                templateEntity.getWorkspaceId(),
                templateEntity.getTemplateName(),
                templateEntity.getTemplateScene(),
                templateEntity.getStatus(),
                templateEntity.getCurrentVersionNo(),
                templateEntity.getRemark(),
                versionEntity == null ? null : toVersionResponse(versionEntity)
        );
    }

    private PromptTemplateVersionResponse toVersionResponse(PromptTemplateVersionEntity versionEntity) {
        String versionStatus = versionEntity.isCanonicallyPublished()
                ? PromptTemplateVersionStatus.PUBLISHED.name()
                : PromptTemplateVersionStatus.DRAFT.name();
        return new PromptTemplateVersionResponse(
                versionEntity.getId(),
                versionEntity.getTemplateId(),
                versionEntity.getVersionNo(),
                versionStatus,
                versionEntity.getSystemPrompt(),
                versionEntity.getUserPrompt(),
                versionEntity.getVariablesJson(),
                versionEntity.getModelStrategyJson(),
                versionEntity.getPublishedBy(),
                versionEntity.getPublishedAt()
        );
    }

    private PageResponse<PromptTemplateUserListItemResponse> emptyUserPage(PublishedPromptTemplateQueryRequest request) {
        return PageResponse.<PromptTemplateUserListItemResponse>builder()
                .records(List.of())
                .pageNo(request.getPageNo())
                .pageSize(request.getPageSize())
                .total(0)
                .build();
    }

    private PageResponse<PromptTemplateUserListItemResponse> paginateUser(
            List<PromptTemplateUserListItemResponse> allRecords, PublishedPromptTemplateQueryRequest pageRequest) {
        int fromIndex = (int) ((pageRequest.getPageNo() - 1) * pageRequest.getPageSize());
        if (fromIndex >= allRecords.size()) {
            return PageResponse.<PromptTemplateUserListItemResponse>builder()
                    .records(List.of())
                    .pageNo(pageRequest.getPageNo())
                    .pageSize(pageRequest.getPageSize())
                    .total(allRecords.size())
                    .build();
        }
        int toIndex = Math.min(allRecords.size(), fromIndex + (int) pageRequest.getPageSize());
        return PageResponse.<PromptTemplateUserListItemResponse>builder()
                .records(allRecords.subList(fromIndex, toIndex))
                .pageNo(pageRequest.getPageNo())
                .pageSize(pageRequest.getPageSize())
                .total(allRecords.size())
                .build();
    }

    private PromptTemplateUserListItemResponse toUserListItem(
            PromptTemplateEntity entity, PromptTemplateVersionEntity versionEntity) {
        Long publishedVersionId = versionEntity != null && versionEntity.isEffectivelyPublished()
                ? versionEntity.getId()
                : null;
        return new PromptTemplateUserListItemResponse(
                entity.getId(),
                entity.getTemplateName(),
                entity.getRemark(),
                entity.getTemplateScene(),
                PromptTemplateSceneCatalog.labelOf(entity.getTemplateScene()),
                PromptTemplateSceneCatalog.applicableAppTypeOf(entity.getTemplateScene()),
                entity.getCurrentVersionNo(),
                publishedVersionId,
                entity.getUpdatedAt()
        );
    }

    private PromptTemplateUserDetailResponse toUserDetailResponse(
            PromptTemplateEntity templateEntity, PromptTemplateVersionEntity versionEntity) {
        return new PromptTemplateUserDetailResponse(
                templateEntity.getId(),
                templateEntity.getTemplateName(),
                templateEntity.getRemark(),
                templateEntity.getTemplateScene(),
                PromptTemplateSceneCatalog.labelOf(templateEntity.getTemplateScene()),
                PromptTemplateSceneCatalog.applicableAppTypeOf(templateEntity.getTemplateScene()),
                PromptTemplateRenderer.truncatePreview(versionEntity.getUserPrompt(), 240),
                PromptTemplateVariableSchemaParser.parse(versionEntity.getVariablesJson()),
                new PromptTemplatePublishedVersionResponse(
                        versionEntity.getId(),
                        versionEntity.getVersionNo(),
                        versionEntity.getPublishedAt())
        );
    }

    private String versionKey(Long templateId, Integer versionNo) {
        return templateId + ":" + versionNo;
    }

    private PromptTemplateVersionEntity requireExistingVersion(Long templateId, Integer versionNo) {
        PromptTemplateVersionEntity versionEntity = promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(
                templateId, versionNo);
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "模板版本不存在");
        }
        return versionEntity;
    }

    private void ensureDraftVersion(PromptTemplateVersionEntity versionEntity) {
        if (versionEntity.isEffectivelyPublished()) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "已发布版本不可修改，请创建新版本");
        }
    }

    private void ensureVersionNotReferenced(Long versionId) {
        if (generationRecordEntityMapper.countByPromptTemplateVersionId(versionId) > 0
                || modelCallLogEntityMapper.countByPromptTemplateVersionId(versionId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "模板已被使用，无法删除，请归档。");
        }
    }

    private void ensureTemplateNotReferenced(Long templateId) {
        if (generationRecordEntityMapper.countByTemplateId(templateId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "模板已被使用，无法删除，请归档。");
        }
    }

    private void validateOptionalJson(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JSON_MAPPER.readTree(json);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, fieldName + " 不是合法 JSON");
        }
    }

    private void writeTemplateAudit(Long actorUserId,
                                    Long workspaceId,
                                    String actionCode,
                                    Long templateId,
                                    Long templateVersionId) {
        writeTemplateAudit(actorUserId, workspaceId, actionCode, templateId, templateVersionId, null, false);
    }

    private void writeTemplateAudit(Long actorUserId,
                                    Long workspaceId,
                                    String actionCode,
                                    Long templateId,
                                    Long templateVersionId,
                                    Integer versionNo,
                                    boolean legacyStateReconciled) {
        auditLogWriter.insert(AuditLogEntity.builder()
                .workspaceId(workspaceId)
                .actorUserId(actorUserId)
                .actionCode(actionCode)
                .targetType("PROMPT_TEMPLATE")
                .targetId(String.valueOf(templateId))
                .requestId(ResultUtils.currentRequestId())
                .detailJson(buildTemplateAuditDetail(
                        templateId, templateVersionId, actionCode, versionNo, legacyStateReconciled))
                .build());
    }

    private String buildTemplateAuditDetail(Long templateId,
                                            Long templateVersionId,
                                            String actionCode,
                                            Integer versionNo,
                                            boolean legacyStateReconciled) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("templateId", templateId);
            detail.put("templateVersionId", templateVersionId);
            if (versionNo != null) {
                detail.put("versionNo", versionNo);
            }
            detail.put("action", actionCode);
            if (legacyStateReconciled) {
                detail.put("legacyStateReconciled", true);
            }
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Prompt template audit detail serialization failed", exception);
        }
    }
}
