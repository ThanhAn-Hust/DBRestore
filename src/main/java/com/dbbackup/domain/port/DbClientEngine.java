package com.dbbackup.domain.port;

import com.dbbackup.domain.model.BackupConfig;
import com.dbbackup.domain.model.DumpFormat;
import com.dbbackup.domain.model.RestoreConfig;

import java.io.InputStream;
import java.io.OutputStream;

public interface DbClientEngine {
    boolean supports(String dbType);
    ProcessBuilder buildBackupProcess(BackupConfig config, OutputStream outputStream);
    ProcessBuilder buildRestoreProcess(RestoreConfig config, InputStream inputStream);
    DumpFormat getDumpFormat(BackupConfig config);
}
