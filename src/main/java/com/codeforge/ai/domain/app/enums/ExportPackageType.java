package com.codeforge.ai.domain.app.enums;

import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;

public enum ExportPackageType {

    ZIP("zip"),
    SOURCE_ZIP("source_zip");

    private final String fileNamePrefix;

    ExportPackageType(String fileNamePrefix) {
        this.fileNamePrefix = fileNamePrefix;
    }

    public String fileNamePrefix() {
        return fileNamePrefix;
    }

    public String code() {
        return name();
    }

    public static ExportPackageType fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "packageType 不能为空");
        }
        String normalized = rawValue.trim().toUpperCase();
        for (ExportPackageType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "packageType 仅支持 ZIP / SOURCE_ZIP");
    }
}
