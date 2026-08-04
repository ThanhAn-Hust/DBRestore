package com.dbbackup.domain.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainModelTest {
    @Test
    void testBackupHistoryRecordCreation() {
        BackupHistoryRecord record = new BackupHistoryRecord(
            "b-001", "prod-db", BackupType.FULL, DumpFormat.PLAIN_SQL,
            null, "chain-001", List.of("users"),
            LocalDateTime.now(), LocalDateTime.now().plusMinutes(2),
            120000L, 1048576L, "file:///backups/b-001.sql.gz",
            "SUCCESS", null
        );
        assertEquals("b-001", record.id());
        assertEquals("prod-db", record.dbName());
        assertEquals(BackupType.FULL, record.backupType());
        assertEquals(DumpFormat.PLAIN_SQL, record.dumpFormat());
        assertNull(record.parentId());
        assertEquals("chain-001", record.chainId());
        assertEquals(List.of("users"), record.tables());
        assertEquals(120000L, record.durationMs());
        assertEquals(1048576L, record.sizeBytes());
        assertEquals("file:///backups/b-001.sql.gz", record.storageUri());
        assertEquals("SUCCESS", record.status());
        assertNull(record.errorMessage());
    }

    @Test
    void testDbConnectionConfigCreation() {
        DbConnectionConfig config = new DbConnectionConfig(
            "postgresql", "localhost", 5432, "postgres", "pass", "mydb", null
        );
        assertEquals("postgresql", config.dbType());
        assertEquals("localhost", config.host());
        assertEquals(5432, config.port());
        assertEquals("postgres", config.username());
        assertEquals("pass", config.password());
        assertEquals("mydb", config.databaseName());
    }

    @Test
    void testBackupConfigCreation() {
        DbConnectionConfig dbConn = new DbConnectionConfig("mysql", "127.0.0.1", 3306, "root", "secret", "app_db", null);
        BackupConfig config = new BackupConfig(
            dbConn, BackupType.FULL, DumpFormat.PLAIN_SQL, List.of("users", "orders"),
            true, true, "secretPassphrase", "s3://bucket/backup.sql.gz", null, "chain-1", null
        );
        assertEquals(dbConn, config.connectionConfig());
        assertEquals(BackupType.FULL, config.backupType());
        assertEquals(DumpFormat.PLAIN_SQL, config.dumpFormat());
        assertEquals(2, config.tables().size());
        assertTrue(config.compressed());
        assertTrue(config.encrypted());
        assertEquals("secretPassphrase", config.encryptionPassphrase());
    }

    @Test
    void testRestoreConfigCreation() {
        DbConnectionConfig dbConn = new DbConnectionConfig("mysql", "127.0.0.1", 3306, "root", "secret", "app_db", null);
        RestoreConfig config = new RestoreConfig(
            dbConn, "s3://bucket/backup.sql.gz", DumpFormat.PLAIN_SQL, List.of("users"),
            true, true, "secretPassphrase", null
        );
        assertEquals(dbConn, config.connectionConfig());
        assertEquals("s3://bucket/backup.sql.gz", config.sourceStorageUri());
        assertEquals(DumpFormat.PLAIN_SQL, config.dumpFormat());
        assertTrue(config.compressed());
        assertTrue(config.encrypted());
    }

    @Test
    void testStateRecordCreation() {
        StateRecord state = new StateRecord("prod-db", "b-001", "b-002", "chain-001", LocalDateTime.now(), "pos-100", null);
        assertEquals("prod-db", state.databaseName());
        assertEquals("b-001", state.lastFullBackupId());
        assertEquals("b-002", state.lastBackupId());
        assertEquals("chain-001", state.currentChainId());
        assertEquals("pos-100", state.lastPosition());
    }
}
