package com.dbbackup.domain.model;

import java.time.LocalDateTime;
import java.util.Map;

public record StateRecord(
    String databaseName,
    String lastFullBackupId,
    String lastBackupId,
    String currentChainId,
    LocalDateTime lastBackupTime,
    String lastPosition,
    Map<String, Object> metadata
) {
}
