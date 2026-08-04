package com.dbbackup.domain.port;

import com.dbbackup.domain.model.BackupHistoryRecord;

import java.util.List;

public interface AuditLogService {
    void initSchema();
    void recordCompletion(BackupHistoryRecord record);
    List<BackupHistoryRecord> getHistory(int limit, String dbName);
    BackupHistoryRecord getRecordById(String id);
}
