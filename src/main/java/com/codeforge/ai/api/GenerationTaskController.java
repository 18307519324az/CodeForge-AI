package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.task.GenerationTaskCreateRequest;
import com.codeforge.ai.application.dto.task.GenerationTaskCreateResponse;
import com.codeforge.ai.application.dto.task.GenerationTaskDetailResponse;
import com.codeforge.ai.application.dto.task.PublicGenerationStreamEvent;
import com.codeforge.ai.application.service.GenerationTaskApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v1/generation-tasks")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class GenerationTaskController {

    private final GenerationTaskApplicationService generationTaskApplicationService;

    @PostMapping
    public ApiResponse<GenerationTaskCreateResponse> createTask(@AuthenticationPrincipal CurrentUser currentUser,
                                                                @Valid @RequestBody GenerationTaskCreateRequest request) {
        return ResultUtils.success(generationTaskApplicationService.createTask(currentUser, request));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<GenerationTaskDetailResponse> getTask(@AuthenticationPrincipal CurrentUser currentUser,
                                                             @PathVariable Long taskId) {
        return ResultUtils.success(generationTaskApplicationService.getTask(currentUser, taskId));
    }

    @GetMapping("/{taskId}/events")
    public ApiResponse<List<PublicGenerationStreamEvent>> listTaskEvents(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long taskId,
            @RequestParam(required = false) String afterEventId) {
        return ResultUtils.success(generationTaskApplicationService.listTaskEvents(
                currentUser,
                taskId,
                parseAfterEventId(afterEventId)));
    }

    @GetMapping("/{taskId}/stream")
    public SseEmitter streamTask(@AuthenticationPrincipal CurrentUser currentUser,
                                @PathVariable Long taskId,
                                @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
                                @RequestParam(required = false) String afterEventId) {
        Long afterEventIdValue = parseAfterEventId(lastEventIdHeader);
        if (afterEventIdValue == null) {
            afterEventIdValue = parseAfterEventId(afterEventId);
        }
        return generationTaskApplicationService.openTaskStream(currentUser, taskId, afterEventIdValue);
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<GenerationTaskDetailResponse> cancelTask(@AuthenticationPrincipal CurrentUser currentUser,
                                                                @PathVariable Long taskId) {
        return ResultUtils.success(generationTaskApplicationService.cancelTask(currentUser, taskId));
    }

    @PostMapping("/{taskId}/retry")
    public ApiResponse<GenerationTaskCreateResponse> retryTask(@AuthenticationPrincipal CurrentUser currentUser,
                                                               @PathVariable Long taskId) {
        return ResultUtils.success(generationTaskApplicationService.retryTask(currentUser, taskId));
    }

    private Long parseAfterEventId(String rawAfterEventId) {
        if (rawAfterEventId == null || rawAfterEventId.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(rawAfterEventId.trim());
            if (parsed <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "afterEventId 必须是有效的事件 ID");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "afterEventId 必须是有效的事件 ID");
        }
    }
}
