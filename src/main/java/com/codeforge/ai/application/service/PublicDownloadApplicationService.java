package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.ExportPackageEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.security.DownloadAccessTokenService;
import com.codeforge.ai.infrastructure.security.DownloadGrantRedemptionService;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.util.DownloadResponseSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicDownloadApplicationService {

    private final DownloadAccessTokenService downloadAccessTokenService;
    private final DownloadGrantRedemptionService downloadGrantRedemptionService;
    private final AppPublicationEntityMapper appPublicationEntityMapper;
    private final ExportPackageEntityMapper exportPackageEntityMapper;

    @Transactional
    public ResponseEntity<Resource> downloadByToken(String downloadToken) throws IOException {
        if (downloadToken == null || downloadToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        ErrorCode tokenError = downloadAccessTokenService.resolveDownloadTokenError(
                downloadToken, appPublicationEntityMapper);
        if (tokenError != null) {
            throw new BusinessException(tokenError);
        }

        Long exportPackageId = downloadAccessTokenService.readExportPackageId(downloadToken);
        Long publicationId = downloadAccessTokenService.readPublicationId(downloadToken);
        ExportPackageEntity exportPackage = exportPackageEntityMapper.selectOneById(exportPackageId);
        if (exportPackage == null || !"READY".equals(exportPackage.getStatus())) {
            throw new BusinessException(ErrorCode.PUBLICATION_EXPORT_NOT_READY);
        }
        Long tokenAppId = downloadAccessTokenService.readAppId(downloadToken);
        Long tokenVersionId = downloadAccessTokenService.readVersionId(downloadToken);
        if (tokenAppId == null
                || tokenVersionId == null
                || !tokenAppId.equals(exportPackage.getAppId())
                || !tokenVersionId.equals(exportPackage.getAppVersionId())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }

        Path zipPath = Path.of(exportPackage.getStoragePath()).normalize();
        if (!Files.exists(zipPath) || !Files.isRegularFile(zipPath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出文件不存在或已被清理");
        }

        String grantId = downloadAccessTokenService.readGrantId(downloadToken);
        if (downloadGrantRedemptionService.tryRedeem(
                grantId, downloadAccessTokenService.getDownloadTokenExpireSeconds())) {
            appPublicationEntityMapper.incrementDownloadCount(publicationId);
        }
        byte[] content = Files.readAllBytes(zipPath);
        ByteArrayResource resource = new ByteArrayResource(content);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        DownloadResponseSupport.contentDispositionAttachment(
                                DownloadResponseSupport.safeAttachmentFilename(zipPath)))
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(resource);
    }
}
