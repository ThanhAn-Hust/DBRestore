package com.dbbackup.domain.port;

import com.dbbackup.domain.model.BackupConfig;
import com.dbbackup.domain.model.DbConnectionConfig;
import com.dbbackup.domain.model.DumpFormat;
import com.dbbackup.domain.model.RestoreConfig;

import java.io.InputStream;

public interface DbClientEngine {
    boolean supports(String dbType);
    ProcessBuilder testConnection(DbConnectionConfig config);
    ProcessBuilder buildBackupProcess(BackupConfig config, StateTracker stateTracker);
    default ProcessBuilder buildBackupProcess(BackupConfig config) {
        return buildBackupProcess(config, (StateTracker) null);
    }
    ProcessBuilder buildRestoreProcess(RestoreConfig config, InputStream inputStream);
    default ProcessBuilder buildRestoreProcess(RestoreConfig config) {
        return buildRestoreProcess(config, null);
    }
    DumpFormat getDumpFormat(BackupConfig config);
}
