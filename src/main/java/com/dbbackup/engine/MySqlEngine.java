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
import java.util.Map;

public class MySqlEngine implements DbClientEngine {

    @Override
    public boolean supports(String dbType) {
        return dbType != null && "mysql".equalsIgnoreCase(dbType.trim());
    }

    @Override
    public DumpFormat getDumpFormat(BackupConfig config) {
        if (config != null && config.dumpFormat() != null) {
            return config.dumpFormat();
        }
        return DumpFormat.PLAIN_SQL;
    }

    @Override
    public ProcessBuilder testConnection(DbConnectionConfig config) {
        List<String> command = new ArrayList<>();
        command.add("mysqladmin");
        if (config.host() != null && !config.host().isBlank()) {
            command.add("-h");
            command.add(config.host());
        }
        if (config.port() > 0) {
            command.add("-P");
            command.add(String.valueOf(config.port()));
        }
        if (config.username() != null && !config.username().isBlank()) {
            command.add("-u");
            command.add(config.username());
        }
        command.add("ping");

        ProcessBuilder pb = new ProcessBuilder(command);
        if (config.password() != null && !config.password().isEmpty()) {
            pb.environment().put("MYSQL_PWD", config.password());
        }
        return pb;
    }

    @Override
    public ProcessBuilder buildBackupProcess(BackupConfig config, StateTracker stateTracker) {
        DbConnectionConfig dbConn = config.connectionConfig();
        List<String> command = new ArrayList<>();
        command.add("mysqldump");

        if (dbConn != null) {
            if (dbConn.host() != null && !dbConn.host().isBlank()) {
                command.add("-h");
                command.add(dbConn.host());
            }
            if (dbConn.port() > 0) {
                command.add("-P");
                command.add(String.valueOf(dbConn.port()));
            }
            if (dbConn.username() != null && !dbConn.username().isBlank()) {
                command.add("-u");
                command.add(dbConn.username());
            }
        }

        if (isSingleTransactionRequested(config)) {
            command.add("--single-transaction");
        }

        if (dbConn != null && dbConn.databaseName() != null && !dbConn.databaseName().isBlank()) {
            command.add(dbConn.databaseName());
        }

        if (config.tables() != null && !config.tables().isEmpty()) {
            command.addAll(config.tables());
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (dbConn != null && dbConn.password() != null && !dbConn.password().isEmpty()) {
            pb.environment().put("MYSQL_PWD", dbConn.password());
        }
        return pb;
    }

    @Override
    public ProcessBuilder buildRestoreProcess(RestoreConfig config, InputStream inputStream) {
        DbConnectionConfig dbConn = config.connectionConfig();
        List<String> command = new ArrayList<>();
        command.add("mysql");

        if (dbConn != null) {
            if (dbConn.host() != null && !dbConn.host().isBlank()) {
                command.add("-h");
                command.add(dbConn.host());
            }
            if (dbConn.port() > 0) {
                command.add("-P");
                command.add(String.valueOf(dbConn.port()));
            }
            if (dbConn.username() != null && !dbConn.username().isBlank()) {
                command.add("-u");
                command.add(dbConn.username());
            }
            if (dbConn.databaseName() != null && !dbConn.databaseName().isBlank()) {
                command.add(dbConn.databaseName());
            }
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (dbConn != null && dbConn.password() != null && !dbConn.password().isEmpty()) {
            pb.environment().put("MYSQL_PWD", dbConn.password());
        }
        return pb;
    }

    private boolean isSingleTransactionRequested(BackupConfig config) {
        if (config != null && config.extraParams() != null) {
            Map<String, Object> params = config.extraParams();
            Object st = params.get("single-transaction");
            if (st == null) {
                st = params.get("singleTransaction");
            }
            if (st instanceof Boolean b) {
                return b;
            }
            if (st instanceof String s) {
                return Boolean.parseBoolean(s);
            }
        }
        return false;
    }
}
