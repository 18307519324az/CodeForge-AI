package com.codeforge.ai.application.service.repair;

public record PreparedRepairedFile(
        String relativePath,
        String textContent,
        long byteSize,
        boolean textFile,
        byte[] binaryContent) {

    public PreparedRepairedFile(String relativePath, String textContent, long byteSize, boolean textFile) {
        this(relativePath, textContent, byteSize, textFile, null);
    }
}
