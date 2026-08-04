package com.dbbackup.domain.model;

import java.util.List;
import java.util.Map;

public record BackupConfig(
    DbConnectionConfig connectionConfig,
    BackupType backupType,
    DumpFormat dumpFormat,
    List<String> tables,
    boolean compressed,
    boolean encrypted,
    String encryptionPassphrase,
    String storageUri,
    String parentBackupId,
    String chainId,
    Map<String, Object> extraParams
) {
    public BackupConfig(
        DbConnectionConfig connectionConfig,
        BackupType backupType,
        List<String> tables,
        boolean compressed,
        Map<String, Object> extraParams
    ) {
        this(connectionConfig, backupType, DumpFormat.PLAIN_SQL, tables, compressed, false, null, null, null, null, extraParams);
    }
}
