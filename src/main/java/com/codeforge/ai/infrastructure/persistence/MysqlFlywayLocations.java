package com.codeforge.ai.infrastructure.persistence;

public final class MysqlFlywayLocations {

    public static final String MYSQL_PROFILE_LOCATIONS =
            "filesystem:sql/migrations,filesystem:sql/mysql-local,filesystem:sql/mysql-baseline";

    public static final String BASELINE_SCRIPT = "B33__codeforge_mysql_schema.sql";

    private MysqlFlywayLocations() {
    }
}
