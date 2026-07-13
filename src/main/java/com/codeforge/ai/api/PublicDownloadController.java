package com.codeforge.ai.api;

import com.codeforge.ai.application.service.PublicDownloadApplicationService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/public/downloads")
@RequiredArgsConstructor
public class PublicDownloadController {

    private final PublicDownloadApplicationService publicDownloadApplicationService;

    @GetMapping("/file")
    public ResponseEntity<Resource> downloadPublicExport(@RequestParam String downloadToken) throws IOException {
        return publicDownloadApplicationService.downloadByToken(downloadToken);
    }
}
