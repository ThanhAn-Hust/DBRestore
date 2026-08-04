package com.dbbackup.domain.model;

import java.util.Map;

public record DbConnectionConfig(
    String dbType,
    String host,
    int port,
    String username,
    String password,
    String databaseName,
    Map<String, String> options
) {
    public DbConnectionConfig(String dbType, String host, int port, String username, String password, String databaseName) {
        this(dbType, host, port, username, password, databaseName, Map.of());
    }
}
