package com.codeforge.ai.api;

import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ResultUtils.success(Map.of(
                "service", "codeforge-ai",
                "status", "UP"
        ));
    }
}
