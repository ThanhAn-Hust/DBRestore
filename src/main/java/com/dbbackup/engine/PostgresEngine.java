package com.dbbackup.engine;

import com.dbbackup.domain.model.BackupConfig;
import com.dbbackup.domain.model.DbConnectionConfig;
import com.dbbackup.domain.model.DumpFormat;
import com.dbbackup.domain.model.RestoreConfig;
import com.dbbackup.domain.port.DbClientEngine;
import com.dbbackup.domain.port.StateTracker;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PostgresEngine implements DbClientEngine {

    @Override
    public boolean supports(String dbType) {
        if (dbType == null) {
            return false;
        }
        String type = dbType.trim().toLowerCase();
        return type.equals("postgresql") || type.equals("postgres");
    }

    @Override
    public DumpFormat getDumpFormat(BackupConfig config) {
        if (config != null && config.tables() != null && !config.tables().isEmpty()) {
            return DumpFormat.CUSTOM_FC;
        }
        if (config != null && config.dumpFormat() != null) {
            return config.dumpFormat();
        }
        return DumpFormat.PLAIN_SQL;
    }

    @Override
    public ProcessBuilder testConnection(DbConnectionConfig config) {
        List<String> command = new ArrayList<>();
        command.add("pg_isready");
        if (config != null) {
            if (config.host() != null && !config.host().isBlank()) {
                command.add("-h");
                command.add(config.host());
            }
            if (config.port() > 0) {
                command.add("-p");
                command.add(String.valueOf(config.port()));
            }
            if (config.username() != null && !config.username().isBlank()) {
                command.add("-U");
                command.add(config.username());
            }
            if (config.databaseName() != null && !config.databaseName().isBlank()) {
                command.add("-d");
                command.add(config.databaseName());
            }
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (config != null && config.password() != null && !config.password().isEmpty()) {
            pb.environment().put("PGPASSWORD", config.password());
        }
        return pb;
    }

    @Override
    public ProcessBuilder buildBackupProcess(BackupConfig config, StateTracker stateTracker) {
        DbConnectionConfig dbConn = config.connectionConfig();
        List<String> command = new ArrayList<>();
        command.add("pg_dump");

        if (dbConn != null) {
            if (dbConn.host() != null && !dbConn.host().isBlank()) {
                command.add("-h");
                command.add(dbConn.host());
            }
            if (dbConn.port() > 0) {
                command.add("-p");
                command.add(String.valueOf(dbConn.port()));
            }
            if (dbConn.username() != null && !dbConn.username().isBlank()) {
                command.add("-U");
                command.add(dbConn.username());
            }
        }

        boolean isSelective = config.tables() != null && !config.tables().isEmpty();
        if (isSelective) {
            command.add("-Fc");
            for (String table : config.tables()) {
                command.add("-t");
                command.add(table);
            }
        }

        if (dbConn != null && dbConn.databaseName() != null && !dbConn.databaseName().isBlank()) {
            command.add("-d");
            command.add(dbConn.databaseName());
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (dbConn != null && dbConn.password() != null && !dbConn.password().isEmpty()) {
            pb.environment().put("PGPASSWORD", dbConn.password());
        }
        return pb;
    }

    @Override
    public ProcessBuilder buildRestoreProcess(RestoreConfig config, InputStream inputStream) {
        DbConnectionConfig dbConn = config.connectionConfig();
        DumpFormat format = config.dumpFormat();
        boolean isCustomFormat = format == DumpFormat.CUSTOM_FC || format == DumpFormat.CUSTOM;

        List<String> command = new ArrayList<>();
        if (isCustomFormat) {
            command.add("pg_restore");
            if (dbConn != null) {
                if (dbConn.host() != null && !dbConn.host().isBlank()) {
                    command.add("-h");
                    command.add(dbConn.host());
                }
                if (dbConn.port() > 0) {
                    command.add("-p");
                    command.add(String.valueOf(dbConn.port()));
                }
                if (dbConn.username() != null && !dbConn.username().isBlank()) {
                    command.add("-U");
                    command.add(dbConn.username());
                }
                if (dbConn.databaseName() != null && !dbConn.databaseName().isBlank()) {
                    command.add("-d");
                    command.add(dbConn.databaseName());
                }
            }
            if (config.tables() != null && !config.tables().isEmpty()) {
                for (String table : config.tables()) {
                    command.add("-t");
                    command.add(table);
                }
            }
        } else {
            command.add("psql");
            if (dbConn != null) {
                if (dbConn.host() != null && !dbConn.host().isBlank()) {
                    command.add("-h");
                    command.add(dbConn.host());
                }
                if (dbConn.port() > 0) {
                    command.add("-p");
                    command.add(String.valueOf(dbConn.port()));
                }
                if (dbConn.username() != null && !dbConn.username().isBlank()) {
                    command.add("-U");
                    command.add(dbConn.username());
                }
                if (dbConn.databaseName() != null && !dbConn.databaseName().isBlank()) {
                    command.add("-d");
                    command.add(dbConn.databaseName());
                }
            }
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (dbConn != null && dbConn.password() != null && !dbConn.password().isEmpty()) {
            pb.environment().put("PGPASSWORD", dbConn.password());
        }
        return pb;
    }
}
