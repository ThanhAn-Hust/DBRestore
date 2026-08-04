package com.dbbackup.domain.model;

import java.time.LocalDateTime;
import java.util.List;

public record BackupHistoryRecord(
    String id,
    String dbName,
    BackupType backupType,
    DumpFormat dumpFormat,
    String parentId,
    String chainId,
    List<String> tables,
    LocalDateTime startTime,
    LocalDateTime endTime,
    Long durationMs,
    Long sizeBytes,
    String storageUri,
    String status,
    String errorMessage
) {
}
