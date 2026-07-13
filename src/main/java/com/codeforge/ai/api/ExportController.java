package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.export.ExportPackageCreateRequest;
import com.codeforge.ai.application.dto.export.ExportPackageCreateResponse;
import com.codeforge.ai.application.dto.export.ExportPackageListItemResponse;
import com.codeforge.ai.application.service.ExportPackageApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import com.codeforge.ai.shared.util.DownloadResponseSupport;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ExportController {

    private final ExportPackageApplicationService exportPackageApplicationService;

    @PostMapping("/export-packages")
    public ApiResponse<ExportPackageCreateResponse> createExportPackage(@AuthenticationPrincipal CurrentUser currentUser,
                                                                        @Valid @RequestBody ExportPackageCreateRequest request) {
        return ResultUtils.success(exportPackageApplicationService.createExportPackage(currentUser, request));
    }

    @GetMapping("/apps/{appId}/export-packages")
    public ApiResponse<List<ExportPackageListItemResponse>> listExportPackages(@AuthenticationPrincipal CurrentUser currentUser,
                                                                               @PathVariable Long appId) {
        return ResultUtils.success(exportPackageApplicationService.listExportPackages(currentUser, appId));
    }

    @GetMapping("/export-packages/{packageId}/download")
    public ResponseEntity<Resource> downloadExportPackage(@AuthenticationPrincipal CurrentUser currentUser,
                                                          @PathVariable Long packageId) throws IOException {
        Path zipPath = exportPackageApplicationService.getPackagePath(currentUser, packageId);
        byte[] content = Files.readAllBytes(zipPath);
        ByteArrayResource resource = new ByteArrayResource(content);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        DownloadResponseSupport.contentDispositionAttachment(
                                DownloadResponseSupport.safeAttachmentFilename(zipPath)))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
