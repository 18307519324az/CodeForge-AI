package com.codeforge.ai.infrastructure.persistence;

final class MysqlIndexLimitSupport {

    private static final int MYSQL_INNODB_MAX_INDEX_BYTES = 3072;

    private MysqlIndexLimitSupport() {
    }

    static int maxUtf8mb4IndexBytes(String mysqlType, int prefixLength) {
        int dataBytes = switch (mysqlType.toLowerCase()) {
            case "bigint" -> 8;
            case "int", "integer" -> 4;
            case "varchar", "char" -> prefixLength * 4;
            default -> throw new IllegalArgumentException("Unsupported index type: " + mysqlType);
        };
        return dataBytes;
    }

    static int mysqlInnodbMaxIndexBytes() {
        return MYSQL_INNODB_MAX_INDEX_BYTES;
    }
}
