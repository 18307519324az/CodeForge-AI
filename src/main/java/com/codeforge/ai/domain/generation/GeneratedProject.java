package com.codeforge.ai.domain.generation;

import java.util.List;

public record GeneratedProject(
    String summary,
    String appName,
    String appType,
    String requirement,
    List<GeneratedProjectFile> files
) {
    public record GeneratedProjectFile(String filePath, String fileName, String content) {}
}
