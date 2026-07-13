package com.codeforge.ai.infrastructure.persistence;

import java.sql.SQLException;
import org.apache.ibatis.exceptions.PersistenceException;

/**
 * Unwraps nested JDBC/MyBatis exceptions for sanitized persistence diagnostics.
 */
public final class SqlExceptionDiagnostics {

    private SqlExceptionDiagnostics() {
    }

    public static String summarize(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        SqlExceptionDetails details = extract(throwable);
        StringBuilder builder = new StringBuilder();
        builder.append("type=").append(details.exceptionType());
        if (details.sqlState() != null) {
            builder.append(", sqlState=").append(details.sqlState());
        }
        if (details.vendorCode() != null) {
            builder.append(", vendorCode=").append(details.vendorCode());
        }
        if (details.message() != null && !details.message().isBlank()) {
            builder.append(", message=").append(sanitize(details.message()));
        }
        if (details.failedSql() != null && !details.failedSql().isBlank()) {
            builder.append(", failedSql=").append(sanitize(details.failedSql()));
        }
        return builder.toString();
    }

    public static SqlExceptionDetails extract(Throwable throwable) {
        Throwable current = throwable;
        SQLException sqlException = null;
        String failedSql = null;
        String exceptionType = throwable.getClass().getSimpleName();

        while (current != null) {
            if (current instanceof SQLException found) {
                sqlException = found;
            }
            if (current instanceof PersistenceException persistenceException
                    && persistenceException.getMessage() != null
                    && failedSql == null) {
                failedSql = persistenceException.getMessage();
            }
            current = current.getCause();
        }

        if (sqlException != null) {
            return new SqlExceptionDetails(
                    exceptionType,
                    sqlException.getSQLState(),
                    sqlException.getErrorCode() == 0 ? null : String.valueOf(sqlException.getErrorCode()),
                    sqlException.getMessage(),
                    failedSql
            );
        }
        return new SqlExceptionDetails(
                exceptionType,
                null,
                null,
                throwable.getMessage(),
                failedSql
        );
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("(?i)(api[_-]?key|authorization|bearer)\\s*[:=]\\s*\\S+", "$1=[REDACTED]")
                .replaceAll("(?i)(sk-[a-zA-Z0-9_-]{8,})", "[REDACTED_TOKEN]")
                .trim();
    }

    public record SqlExceptionDetails(
            String exceptionType,
            String sqlState,
            String vendorCode,
            String message,
            String failedSql
    ) {
    }
}
