package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.projection.AppLatestTaskStatusRow;
import com.codeforge.ai.infrastructure.persistence.projection.VersionExportStatusRow;
import com.codeforge.ai.infrastructure.persistence.projection.VersionFileCountRow;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppListSummaryAggregator {

    private final AppVersionEntityMapper appVersionEntityMapper;
    private final AppPublicationEntityMapper appPublicationEntityMapper;
    private final GeneratedFileEntityMapper generatedFileEntityMapper;
    private final ExportPackageEntityMapper exportPackageEntityMapper;
    private final GenerationTaskEntityMapper generationTaskEntityMapper;

    public Map<Long, AppListItemSummary> aggregate(List<AiAppEntity> apps) {
        if (apps == null || apps.isEmpty()) {
            return Map.of();
        }
        List<Long> appIds = apps.stream().map(AiAppEntity::getId).filter(Objects::nonNull).toList();
        List<Long> versionIds = apps.stream()
                .map(AiAppEntity::getCurrentVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, AppVersionEntity> versionsById = loadVersions(versionIds);
        Map<Long, Integer> fileCountByVersionId = loadFileCounts(versionIds);
        Map<Long, String> exportStatusByVersionId = loadExportStatuses(versionIds);
        Set<Long> runningAppIds = loadRunningAppIds(appIds);
        Map<Long, String> latestTaskStatusByAppId = loadLatestTaskStatuses(appIds);
        Map<Long, AppPublicationEntity> publicationsByAppId = loadPublications(appIds);

        Map<Long, AppListItemSummary> summaries = new HashMap<>();
        for (AiAppEntity app : apps) {
            Long versionId = app.getCurrentVersionId();
            AppVersionEntity version = versionId == null ? null : versionsById.get(versionId);
            Integer fileCount = versionId == null ? null : fileCountByVersionId.getOrDefault(versionId, 0);
            String exportStatus = versionId == null ? null : exportStatusByVersionId.get(versionId);
            String generationSource = version == null ? null : version.getVersionSource();
            Integer versionNo = version == null ? null : version.getVersionNo();
            boolean hasRunningTask = runningAppIds.contains(app.getId());
            String latestTaskStatus = latestTaskStatusByAppId.get(app.getId());
            AppPublicationEntity publication = publicationsByAppId.get(app.getId());
            String publicationStatus = publication == null ? "NONE" : publication.getStatus();
            summaries.put(app.getId(), AppListItemSummary.builder()
                    .currentVersionNo(versionNo)
                    .latestGenerationSource(generationSource)
                    .generatedFileCount(fileCount)
                    .latestExportStatus(exportStatus)
                    .publicationStatus(publicationStatus)
                    .publicationSlug(publication == null ? null : publication.getSlug())
                    .publicationId(publication == null ? null : publication.getId())
                    .displayStatus(AppDisplayStatusDeriver.derive(
                            app, hasRunningTask, fileCount, latestTaskStatus, publicationStatus))
                    .build());
        }
        return summaries;
    }

    private Map<Long, AppVersionEntity> loadVersions(List<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return appVersionEntityMapper.findByIds(versionIds).stream()
                .collect(Collectors.toMap(AppVersionEntity::getId, version -> version));
    }

    private Map<Long, Integer> loadFileCounts(List<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (VersionFileCountRow row : generatedFileEntityMapper.countByVersionIds(versionIds)) {
            counts.put(row.getAppVersionId(), row.getFileCount() == null ? 0 : row.getFileCount().intValue());
        }
        return counts;
    }

    private Map<Long, String> loadExportStatuses(List<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> statuses = new HashMap<>();
        for (VersionExportStatusRow row : exportPackageEntityMapper.findLatestStatusByVersionIds(versionIds)) {
            statuses.put(row.getAppVersionId(), row.getStatus());
        }
        return statuses;
    }

    private Set<Long> loadRunningAppIds(List<Long> appIds) {
        if (appIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(generationTaskEntityMapper.findRunningAppIds(appIds));
    }

    private Map<Long, String> loadLatestTaskStatuses(List<Long> appIds) {
        if (appIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> statuses = new HashMap<>();
        for (AppLatestTaskStatusRow row : generationTaskEntityMapper.findLatestTaskStatusByAppIds(appIds)) {
            statuses.put(row.getAppId(), row.getTaskStatus());
        }
        return statuses;
    }

    private Map<Long, AppPublicationEntity> loadPublications(List<Long> appIds) {
        if (appIds.isEmpty()) {
            return Map.of();
        }
        return appPublicationEntityMapper.findByAppIds(appIds).stream()
                .collect(Collectors.toMap(AppPublicationEntity::getAppId, publication -> publication, (left, right) -> left));
    }
}
