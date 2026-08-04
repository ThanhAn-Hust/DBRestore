package com.dbbackup.domain.model;

import java.util.List;
import java.util.Map;

public record RestoreConfig(
    DbConnectionConfig connectionConfig,
    String sourceStorageUri,
    DumpFormat dumpFormat,
    List<String> tables,
    boolean compressed,
    boolean encrypted,
    String encryptionPassphrase,
    Map<String, Object> extraParams
) {
}
