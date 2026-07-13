package com.codeforge.ai.infrastructure.runtime;

public record RuntimeDatabaseIdentity(
        String profile,
        String databaseType,
        String databaseHost,
        String databaseName,
        String databasePath,
        String jdbcUrlSanitized) {}
