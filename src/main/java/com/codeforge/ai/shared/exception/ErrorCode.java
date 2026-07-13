package com.codeforge.ai.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    SUCCESS(0, "ok", HttpStatus.OK),

    PARAM_ERROR(40000, "请求参数错误", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
    REQUEST_BODY_ERROR(40002, "请求体格式错误", HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(40100, "未登录或登录失效", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(40101, "Token 无效", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40102, "Token 已过期", HttpStatus.UNAUTHORIZED),

    FORBIDDEN(40300, "无权限访问", HttpStatus.FORBIDDEN),
    RESOURCE_FORBIDDEN(40301, "资源访问被拒绝", HttpStatus.FORBIDDEN),
    PUBLICATION_PERMISSION_REQUIRED(40310, "无权限发布该应用", HttpStatus.FORBIDDEN),

    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(40401, "用户不存在", HttpStatus.NOT_FOUND),
    WORKSPACE_NOT_FOUND(40402, "工作空间不存在", HttpStatus.NOT_FOUND),
    APP_NOT_FOUND(40403, "应用不存在", HttpStatus.NOT_FOUND),
    PROMPT_TEMPLATE_NOT_FOUND(40403, "提示词模板不存在", HttpStatus.NOT_FOUND),
    PUBLICATION_NOT_FOUND(40410, "发布记录不存在", HttpStatus.NOT_FOUND),
    PUBLICATION_APP_NOT_FOUND(40412, "发布应用不存在", HttpStatus.NOT_FOUND),
    PUBLICATION_VERSION_NOT_FOUND(40411, "发布版本不存在", HttpStatus.NOT_FOUND),

    CONFLICT(40900, "资源状态冲突", HttpStatus.CONFLICT),
    STATE_CONFLICT(40901, "状态不允许当前操作", HttpStatus.CONFLICT),
    DUPLICATE_RESOURCE(40902, "资源已存在", HttpStatus.CONFLICT),
    PUBLICATION_ALREADY_PUBLISHED(40910, "应用已发布", HttpStatus.CONFLICT),
    PUBLICATION_VERSION_NOT_OWNED(40911, "版本不属于该应用", HttpStatus.CONFLICT),
    PUBLICATION_ARTIFACT_NOT_READY(40912, "应用产物尚未就绪，无法发布", HttpStatus.CONFLICT),
    PUBLICATION_ENTRY_MISSING(40913, "应用产物缺少入口文件，无法发布", HttpStatus.CONFLICT),
    PUBLICATION_EXPORT_NOT_READY(40914, "导出包尚未就绪，无法开启下载", HttpStatus.CONFLICT),
    PUBLICATION_NOT_PUBLISHED(40915, "应用未公开发布", HttpStatus.CONFLICT),
    PUBLICATION_PREVIEW_DISABLED(40916, "该应用未开放在线预览", HttpStatus.CONFLICT),
    PUBLICATION_DOWNLOAD_DISABLED(40917, "该应用未开放源码下载", HttpStatus.CONFLICT),

    RATE_LIMITED(42900, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    QUOTA_NOT_ENOUGH(42901, "额度不足", HttpStatus.TOO_MANY_REQUESTS),

    SYSTEM_ERROR(50000, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR(50001, "数据库异常", HttpStatus.INTERNAL_SERVER_ERROR),

    MODEL_PROVIDER_ERROR(50200, "模型服务异常", HttpStatus.BAD_GATEWAY),
    MODEL_TIMEOUT(50201, "模型调用超时", HttpStatus.BAD_GATEWAY),

    TASK_QUEUE_UNAVAILABLE(50300, "任务队列不可用", HttpStatus.SERVICE_UNAVAILABLE),
    TASK_EXECUTION_FAILED(50301, "任务执行失败", HttpStatus.SERVICE_UNAVAILABLE),
    CREDENTIAL_ENCRYPTION_UNAVAILABLE(
            50302,
            "加密凭据存储当前不可用，请先配置服务器主密钥",
            HttpStatus.SERVICE_UNAVAILABLE),

    AI_OUTPUT_QUALITY_FAILED(50400, "AI 生成内容质量不合格，已切换规则引擎", HttpStatus.BAD_GATEWAY),
    AI_OUTPUT_TRUNCATED(50401, "AI 输出超过长度限制，生成未完成", HttpStatus.BAD_GATEWAY),
    AI_OUTPUT_INVALID_JSON(50402, "AI 输出 JSON 无效，无法解析", HttpStatus.BAD_GATEWAY),
    AI_OUTPUT_CONTRACT_INVALID(50403, "AI 输出不符合生成契约", HttpStatus.BAD_GATEWAY),
    AI_ARTIFACT_INVALID(50410, "AI 生成产物无法运行", HttpStatus.BAD_GATEWAY),
    AI_ARTIFACT_ESCAPE_CORRUPTED(50411, "AI 生成产物换行转义损坏", HttpStatus.BAD_GATEWAY),
    AI_ARTIFACT_ENTRY_MISSING(50412, "AI 生成产物缺少入口文件", HttpStatus.BAD_GATEWAY),
    AI_ARTIFACT_ASSET_MISSING(50413, "AI 生成产物缺少本地资源", HttpStatus.BAD_GATEWAY),
    AI_ARTIFACT_AMBIGUOUS_ENCODING(50414, "AI 生成产物存在无法安全解码的嵌套转义", HttpStatus.BAD_GATEWAY),

    ARTIFACT_PATH_OUTSIDE_VERSION_ROOT(40003, "产物文件路径越界", HttpStatus.BAD_REQUEST),
    ARTIFACT_BUDGET_EXCEEDED(40004, "产物文件数量或大小超出限制", HttpStatus.BAD_REQUEST),

    BUILD_FAILED(50002, "构建失败", HttpStatus.INTERNAL_SERVER_ERROR),

    ACCOUNT_ALREADY_EXISTS(40902, "账号已存在", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(40902, "邮箱已存在", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
