package com.codeforge.ai.domain.generation.validation;

/**
 * Artifact size and count limits aligned with CEPS export boundaries (fileCount 5000)
 * and schema constraints (file_path length 1024).
 */
public final class GeneratedArtifactBudget {

    /** CEPS EX-B007 / EX-B008 export file count boundary. */
    public static final int MAX_FILE_COUNT = 5000;

    public static final int MAX_FILE_PATH_LENGTH = 1024;

    public static final int MAX_FILE_NAME_LENGTH = 255;

    /** Per text artifact UTF-8 byte limit for repair and generation persistence. */
    public static final long MAX_SINGLE_TEXT_FILE_BYTES = 2_097_152L;

    /** Total UTF-8 bytes across all text artifacts in one version. */
    public static final long MAX_TOTAL_TEXT_BYTES = 10_485_760L;

    /** Binary assets such as mascot PNG. */
    public static final long MAX_SINGLE_BINARY_FILE_BYTES = 5_242_880L;

    private GeneratedArtifactBudget() {
    }
}
