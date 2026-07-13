package com.codeforge.ai.infrastructure.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeDatabaseGuard implements ApplicationListener<ApplicationReadyEvent> {

    static final String RUNTIME_IDENTITY_FILE = ".run/runtime-identity.json";

    private final Environment environment;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        RuntimeDatabaseIdentity identity = resolveIdentity();
        log.info(
                "CodeForge Runtime: profile={} databaseType={} databaseHost={} databaseName={} databasePath={} jdbcUrl={}",
                identity.profile(),
                identity.databaseType(),
                identity.databaseHost(),
                identity.databaseName(),
                identity.databasePath(),
                identity.jdbcUrlSanitized());
        writeIdentityFile(identity);
    }

    RuntimeDatabaseIdentity resolveIdentity() {
        String profile = String.join(",", environment.getActiveProfiles());
        if (profile.isBlank()) {
            profile = "default";
        }
        String jdbcUrl = readJdbcUrl();
        String sanitized = sanitizeJdbcUrl(jdbcUrl);
        return new RuntimeDatabaseIdentity(
                profile,
                detectDatabaseType(jdbcUrl),
                extractHost(jdbcUrl),
                extractDatabaseName(jdbcUrl),
                extractDatabasePath(jdbcUrl),
                sanitized);
    }

    private String readJdbcUrl() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        } catch (Exception exception) {
            log.warn("Unable to read JDBC URL from DataSource: {}", exception.getMessage());
            return environment.getProperty("spring.datasource.url", "");
        }
    }

    static String sanitizeJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "unknown";
        }
        return jdbcUrl.replaceAll("(?i)(password=)[^;&]*", "$1***");
    }

    static String detectDatabaseType(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "UNKNOWN";
        }
        if (jdbcUrl.startsWith("jdbc:mysql:")) {
            return "MYSQL";
        }
        if (jdbcUrl.startsWith("jdbc:h2:mem:")) {
            return "H2_MEM";
        }
        if (jdbcUrl.startsWith("jdbc:h2:file:")) {
            return "H2_FILE";
        }
        if (jdbcUrl.startsWith("jdbc:h2:")) {
            return "H2";
        }
        return "OTHER";
    }

    static String extractHost(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql:")) {
            return "-";
        }
        int hostStart = jdbcUrl.indexOf("//");
        if (hostStart < 0) {
            return "-";
        }
        int hostEnd = jdbcUrl.indexOf('/', hostStart + 2);
        if (hostEnd < 0) {
            return jdbcUrl.substring(hostStart + 2);
        }
        return jdbcUrl.substring(hostStart + 2, hostEnd);
    }

    static String extractDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql:")) {
            return "-";
        }
        int hostStart = jdbcUrl.indexOf("//");
        int dbStart = jdbcUrl.indexOf('/', hostStart + 2);
        if (dbStart < 0) {
            return "-";
        }
        String tail = jdbcUrl.substring(dbStart + 1);
        int queryIndex = tail.indexOf('?');
        return queryIndex >= 0 ? tail.substring(0, queryIndex) : tail;
    }

    static String extractDatabasePath(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:h2:file:")) {
            return "-";
        }
        String prefix = "jdbc:h2:file:";
        String remainder = jdbcUrl.substring(prefix.length());
        int semicolonIndex = remainder.indexOf(';');
        return semicolonIndex >= 0 ? remainder.substring(0, semicolonIndex) : remainder;
    }

    private void writeIdentityFile(RuntimeDatabaseIdentity identity) {
        try {
            Path identityPath = Path.of(RUNTIME_IDENTITY_FILE);
            Files.createDirectories(identityPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(identityPath.toFile(), identity);
        } catch (Exception exception) {
            log.warn("Unable to write runtime identity file: {}", exception.getMessage());
        }
    }
}
