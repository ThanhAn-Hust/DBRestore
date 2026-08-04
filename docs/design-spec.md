# Design Specification: CLI Database Backup Utility (`db-backup`)

**Date:** 2026-08-03  
**Status:** Approved / Revision 6 (Final Complete Specification)  
**Target Stack:** Java 21, Spring Boot 3.3+, Spring Shell 3.x  

---

## 1. Overview & Objectives

The **CLI Database Backup Utility (`db-backup`)** is an enterprise-grade command-line tool built using Java Spring. It orchestrates database backup and restore operations for various DBMS (MySQL, PostgreSQL/Neon, MongoDB, MS SQL Server, etc.), featuring zero-buffering streaming, authenticated AES-256-GCM encryption, local/cloud storage sinks, chain-aware retention policies, cron scheduling daemon, structured audit logging, and multi-channel notifications (Telegram, Slack, Discord).

---

## 2. System Architecture

```
                                  ┌────────────────────────┐
                                  │    Spring Shell CLI    │
                                  └───────────┬────────────┘
                                              │
                         ┌────────────────────┴────────────────────┐
                         ▼                                         ▼
            ┌────────────────────────┐                ┌────────────────────────┐
            │   BackupOrchestrator   │                │  RestoreOrchestrator   │
            └────────────┬───────────┘                └────────────┬───────────┘
                         │                                         │
       ┌─────────────────┼─────────────────────────┬───────────────┴─────────────────┬────────────────────────┐
       ▼                 ▼                         ▼                                 ▼                        ▼
┌──────────────┐ ┌───────────────┐ ┌──────────────────────────────┐ ┌───────────────────────────────┐ ┌────────────────────┐
│Compressor /  │ │BackupStrategy │ │       StorageProvider        │ │      NotificationService      │ │   AuditLogService  │
│Encryptor     │ │(Full, Incr,   │ │(Local, AWS S3, Azure Blob,   │ │(Telegram, Slack, Discord,     │ │ & StateTracker     │
│(Gzip + AES)  │ │ Selective)    │ │ GCS, + Chain-Aware Cleanup)  │ │ Email)                        │ │(SQLite / JSON)     │
└──────────────┘ └───────────────┘ └──────────────────────────────┘ └───────────────────────────────┘ └────────────────────┘
```

---

## 3. Detailed Component & Technical Specifications

### 3.1 Encryption Stream Header & Length-Prefixed Segmented GCM

To allow zero-buffering streaming without needing to know total file size in advance and to support unambiguous stream parsing at restore time:

#### Header Layout (32-Byte File Header):
- `Bytes 0-3` (4 bytes): Magic Bytes (`0x44 0x42 0x42 0x4B` -> ASCII "DBBK")
- `Bytes 4-19` (16 bytes): PBKDF2 Salt (16 bytes)
- `Bytes 20-31` (12 bytes): Base IV / Nonce (12 bytes)

#### Length-Prefixed Segment Framing & IV Derivation:
- Data is written as a sequence of self-describing **Length-Prefixed Segments**.
- **Segment Header (4 bytes)**: Big-endian 32-bit integer indicating the length $N$ of the encrypted payload that follows.
- **Segment Payload ($N$ bytes)**: Encrypted ciphertext including GCM authentication tag (16 bytes). A segment length $N = 0$ marks End-Of-Stream (EOS).
- **IV Derivation Rule**:
  - `derivedIV = BaseIV.clone()`
  - For segment index $i$ (0-based 32-bit integer), update the **last 4 bytes** of `derivedIV`:  
    `derivedIV[8..11] = BaseIV[8..11] ^ ByteBuffer.allocate(4).putInt(i).array()`
- **Segment Size**: Default segment size is 64MB unencrypted payload (max 16GB), ensuring constant memory usage, deterministic segment framing, and zero IV reuse risks.

---

### 3.2 Selective Restore & Compression Pipelines

1. **PostgreSQL & Compression Optimization**:
   - **Full Backups**: `pg_dump` (plain SQL) $\rightarrow$ `GZIPOutputStream` $\rightarrow$ `CipherOutputStream` $\rightarrow$ `StorageProvider`.
   - **Selective-Capable Backups**: Uses `pg_dump -Fc` (Custom format). Since `-Fc` includes internal zlib compression, the pipeline **skips `GZIPOutputStream`** to prevent double-compression:
     `pg_dump -Fc` $\rightarrow$ `CipherOutputStream` $\rightarrow$ `StorageProvider`.
   - Restored via `pg_restore -t <table>`.

2. **MySQL**:
   - Selective backups execute `mysqldump` per-table into dedicated archive files or pass exact table list arguments (`mysqldump <db> tbl1 tbl2`).
   - Selective restore from full single-file SQL dumps is disabled; full dumps are restored in whole.

3. **MongoDB**:
   - `mongodump --collection=<coll>` and `mongorestore --collection=<coll>`.

---

### 3.3 State Tracking (`StateTracker`) & Audit Schema (`BackupHistoryRecord`)

#### `DumpFormat` Enum:
```java
public enum DumpFormat {
    PLAIN_SQL,
    CUSTOM_FC,
    BINLOG,
    WAL,
    OPLOG
}
```

#### Complete `BackupHistoryRecord`:
```java
public record BackupHistoryRecord(
    String id,
    String profileName,
    BackupType backupType,
    DumpFormat dumpFormat,
    String parentBackupId,      // NULL for FULL, contains Base/Parent ID for INCREMENTAL
    String backupChainId,       // Identifies the entire chain group
    List<String> targetTables,  // Empty = entire DB
    LocalDateTime startTime,
    LocalDateTime endTime,
    long durationMs,
    long fileSizeBytes,
    String destinationUri,
    String status,              // SUCCESS, FAILED, CANCELLED
    String errorMessage
) {}
```

#### `StateTracker` Interface:
```java
public interface StateTracker {
    void saveState(String profileName, String positionId, Map<String, Object> metadata);
    Optional<StateRecord> getState(String profileName);
}

public record StateRecord(
    String profileName,
    String positionId,
    LocalDateTime updatedAt,
    Map<String, Object> metadata
) {}
```

---

### 3.4 Mixed Restore Chains & Chain Scope Validation

#### Restore Chain Execution
When executing `RestoreOrchestrator.restoreChain(String targetBackupId)`:
1. `AuditLogService` resolves chain hierarchy: `[FULL_BASE, INC_1, INC_2, ..., INC_TARGET]`.
2. For each step, `RestoreOrchestrator` inspects `record.dumpFormat()`:
   - `PLAIN_SQL`: Pipes decompressed stream to `psql` / `mysql`.
   - `CUSTOM_FC`: Invokes `pg_restore`.
   - `BINLOG`: Replays binlog files via `mysqlbinlog`.
   - `WAL`: Applies WAL segment files.
   - `OPLOG`: Invokes `mongorestore --oplogReplay`.

#### Chain Scope Consistency Rule
- An `INCREMENTAL` or `DIFFERENTIAL` backup **MUST MATCH** the exact scope (`targetTables`) of its parent `FULL` base backup.
- `BackupOrchestrator` validates table scopes prior to triggering backup processes and rejects mismatched attempts.

---

### 3.5 Chain-Aware Retention Policy (`RetentionService`)

- **Cohort Deletion Rule**: A parent `FULL` backup cannot be deleted while any dependent `INCREMENTAL` or `DIFFERENTIAL` child backup in its chain remains active.
- Retention triggers delete operations only when **all records in an entire `backupChainId` cohort** cross the expiration threshold.

---

### 3.6 Cross-Platform Security & Two-Layer Cleanup

1. **Credential Isolation**: Primary pass via environment variables (`MYSQL_PWD`, `PGPASSWORD`, `MONGO_PASSWORD`).
2. **Temporary Credential Files (`.my.cnf`)**:
   - POSIX `0600` on Linux/macOS.
   - User-restricted ACLs in `%TEMP%\.db-backup\` on Windows.
3. **Two-Layer Cleanup Defense**:
   - **Layer 1 (Runtime)**: Code execution wrapped in `AutoCloseable` resources + JVM Shutdown Hooks (`addShutdownHook`).
   - **Layer 2 (Startup Sweep)**: On application startup, `db-backup` sweeps the temporary directory (`%TEMP%\.db-backup\` or `/tmp/.db-backup/`) and deletes any orphaned credential file older than 15 minutes to guarantee cleanup after hard JVM crashes (`kill -9`, power loss).

---

### 3.7 Concurrency-Safe Audit Logging (`AuditLogService`)

- **SQLite Backend**: SQLite configured in **WAL Mode (`PRAGMA journal_mode=WAL;`)** with busy timeout (`PRAGMA busy_timeout=5000;`) to safely handle multi-threaded cron daemon writes.
- **JSONL Backend**: Protected via file locks (`FileChannel.lock()`) and JVM `ReentrantReadWriteLock`.

---

### 3.8 Daemon Scheduling & Overlap Policies (`schedule.yml`)

```yaml
jobs:
  - id: daily-mysql-full
    profile: prod-mysql
    cron: "0 0 2 * * *"          # Every day at 2:00 AM
    backup-type: FULL
    single-transaction: true
    on-overlap: SKIP             # Options: SKIP, QUEUE, CANCEL_PREVIOUS
    misfire-instruction: FIRE_NOW # Options: FIRE_NOW, IGNORE
    retention:
      keep-last: 7
    notifications: [telegram]
```

---

## 4. Full Profile & Configuration Schema (`config.yml`)

```yaml
profiles:
  prod-mysql:
    db-type: mysql
    host: 127.0.0.1
    port: 3306
    username: root
    password: "${MYSQL_PASSWORD}"
    database: prod_db

  prod-postgres:
    db-type: postgresql
    host: neon.tech.host
    port: 5432
    username: neon_user
    password: "${NEON_PASSWORD}"
    database: main_db

notifications:
  telegram:
    enabled: true
    bot-token: "${TELEGRAM_BOT_TOKEN}"
    chat-id: "-100123456789"
  slack:
    enabled: true
    webhook-url: "${SLACK_WEBHOOK_URL}"
```

---

## 5. CLI Command Interface

```bash
# Test DB Connection
db-backup test-connection --profile=prod-mysql

# Backup with AES Encryption & Telegram Alert
db-backup backup --profile=prod-mysql --type=FULL --encrypt --output=s3://my-bucket/backups/ --notify=telegram

# Restore Incremental Chain Automatically
db-backup restore --profile=prod-mysql --backup-id=inc-2026-08-03-001

# View Audit History & Chains
db-backup history --profile=prod-mysql --limit=20

# Start Daemon Mode
db-backup daemon start --config=~/.db-backup/schedule.yml
```

---

## 6. Verification & Test Strategy

1. **Unit Tests**:
   - `CipherStreamTest`: Verify 32-byte Header + Length-Prefixed Segment framing roundtrip encryption & decryption.
   - `RetentionServiceTest`: Verify chain cohort retention locking.
   - `StartupCleanupSweepTest`: Verify removal of stale temp credential files older than 15 minutes.
2. **Integration Tests**:
   - Concurrent daemon write test for `AuditLogService` with SQLite WAL mode.
   - Testcontainers (MySQL, Postgres, S3 LocalStack, Azurite) testing full chain restore.
