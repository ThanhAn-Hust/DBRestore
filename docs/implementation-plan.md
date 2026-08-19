# CLI Database Backup Utility (`db-backup`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-grade Java Spring Shell CLI utility (`db-backup`) supporting streaming database backups, AES-256-GCM encryption, local/S3/Azure/GCS storage, chain-aware retention, SQLite WAL audit logging, cron scheduling daemon, and Telegram/Slack notifications.

**Architecture:** Spring Shell CLI layer orchestrating streaming database engines (`ProcessBuilder`), encryption (`CipherOutputStream`), storage sinks (`StorageProvider`), audit logging (`AuditLogService`), and notification clients. 

**Tech Stack:** Java 25, Spring Boot 3.3+, Spring Shell 3.x, SQLite (JDBC), AWS SDK v2, Azure Storage Blob SDK, Google Cloud Storage SDK, JUnit 5, Mockito, Testcontainers.

## Global Constraints
- Java 25 LTS compatibility.
- Stream stdout directly without memory buffering (`ProcessBuilder` $\rightarrow$ `GZIPOutputStream` $\rightarrow$ `CipherOutputStream` $\rightarrow$ `StorageProvider`).
- Pass credentials via environment variables (`MYSQL_PWD`, `PGPASSWORD`) or OS-restricted ACL file descriptors.
- SQLite WAL mode for thread-safe concurrent audit logging.
- Strict TDD (failing test first, minimal implementation, passing test, commit).

---

### Task 1: Project Setup & Core Domain Contracts

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/dbbackup/domain/model/BackupType.java`
- Create: `src/main/java/com/dbbackup/domain/model/DumpFormat.java`
- Create: `src/main/java/com/dbbackup/domain/model/BackupConfig.java`
- Create: `src/main/java/com/dbbackup/domain/model/BackupHistoryRecord.java`
- Create: `src/main/java/com/dbbackup/domain/port/DbClientEngine.java`
- Create: `src/main/java/com/dbbackup/domain/port/StorageProvider.java`
- Create: `src/main/java/com/dbbackup/domain/port/NotificationService.java`
- Create: `src/main/java/com/dbbackup/domain/port/AuditLogService.java`
- Create: `src/main/java/com/dbbackup/domain/port/StateTracker.java`
- Test: `src/test/java/com/dbbackup/domain/model/DomainModelTest.java`

**Interfaces:**
- Produces: Core domain models (`BackupConfig`, `BackupHistoryRecord`) and port contracts (`DbClientEngine`, `StorageProvider`, `AuditLogService`, `StateTracker`).

- [ ] **Step 1: Write the failing test**

```java
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
        assertEquals(BackupType.FULL, record.backupType());
        assertEquals(DumpFormat.PLAIN_SQL, record.dumpFormat());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DomainModelTest`  
Expected: FAIL (Classes and packages do not exist yet)

- [ ] **Step 3: Write minimal implementation**

Create Maven `pom.xml` with Java 25, Spring Boot 3.3.x, Spring Shell, SQLite JDBC, JUnit 5, and Lombok/Jackson. Create domain models & interfaces.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DomainModelTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/
git commit -m "chore: setup maven project and core domain interfaces"
```

---

### Task 2: Segmented AES-256-GCM Encryption Stream (`SegmentedCipherStream`)

**Files:**
- Create: `src/main/java/com/dbbackup/security/SegmentedCipherOutputStream.java`
- Create: `src/main/java/com/dbbackup/security/SegmentedCipherInputStream.java`
- Test: `src/test/java/com/dbbackup/security/SegmentedCipherStreamTest.java`

**Interfaces:**
- Consumes: Key passphrase + Salt.
- Produces: `SegmentedCipherOutputStream` (writes 32-byte header + length-prefixed segments) and `SegmentedCipherInputStream` (parses header + decrypts segments).

- [ ] **Step 1: Write the failing test**

```java
package com.dbbackup.security;

import org.junit.jupiter.api.Test;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class SegmentedCipherStreamTest {
    @Test
    void testEncryptAndDecryptRoundtrip() throws Exception {
        byte[] originalData = "Hello World, Database Backup Test Payload!".getBytes();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String passphrase = "SuperSecretPassword123";

        try (SegmentedCipherOutputStream cos = new SegmentedCipherOutputStream(baos, passphrase)) {
            cos.write(originalData);
        }

        byte[] encryptedBytes = baos.toByteArray();
        assertTrue(encryptedBytes.length > 32, "Header (32 bytes) + Payload must be written");

        ByteArrayInputStream bais = new ByteArrayInputStream(encryptedBytes);
        ByteArrayOutputStream decryptedOs = new ByteArrayOutputStream();
        try (SegmentedCipherInputStream cis = new SegmentedCipherInputStream(bais, passphrase)) {
            cis.transferTo(decryptedOs);
        }

        assertArrayEquals(originalData, decryptedOs.toByteArray());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SegmentedCipherStreamTest`  
Expected: FAIL (Class not found)

- [ ] **Step 3: Write minimal implementation**

Implement `SegmentedCipherOutputStream` (writing 4-byte Magic `DBBK` + 16-byte Salt + 12-byte Base IV, followed by length-prefixed segments with IV XOR derivation) and `SegmentedCipherInputStream`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SegmentedCipherStreamTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: implement segmented AES-256-GCM encryption streams"
```

---

### Task 3: Credential Isolation & Startup Cleanup Sweep

**Files:**
- Create: `src/main/java/com/dbbackup/security/CredentialManager.java`
- Create: `src/main/java/com/dbbackup/security/StartupCleanupSweep.java`
- Test: `src/test/java/com/dbbackup/security/CredentialManagerTest.java`

**Interfaces:**
- Consumes: User/Password credentials.
- Produces: Isolated environment map or POSIX/ACL restricted temp `.my.cnf` file descriptor (`AutoCloseable`).

- [ ] **Step 1: Write the failing test**

```java
package com.dbbackup.security;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class CredentialManagerTest {
    @Test
    void testTempCnfFileCreationAndCleanup() throws Exception {
        File cnfFile;
        try (var handle = CredentialManager.createTempMyCnf("root", "secret123")) {
            cnfFile = handle.getFile();
            assertTrue(cnfFile.exists());
            String content = Files.readString(cnfFile.toPath());
            assertTrue(content.contains("password=secret123"));
        }
        assertFalse(cnfFile.exists(), "Temp credential file must be deleted upon close");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=CredentialManagerTest`  
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

Implement `CredentialManager` (handles environment maps & OS-specific ACL temp files with shutdown hooks) and `StartupCleanupSweep` (purging temp files older than 15 minutes at boot).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CredentialManagerTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: implement credential isolation and startup cleanup sweep"
```

---

### Task 4: Database Engine Client Layer (MySQL & PostgreSQL)

**Files:**
- Create: `src/main/java/com/dbbackup/engine/MySqlEngine.java`
- Create: `src/main/java/com/dbbackup/engine/PostgresEngine.java`
- Test: `src/test/java/com/dbbackup/engine/DbClientEngineTest.java`

**Interfaces:**
- Consumes: `BackupConfig` & `RestoreConfig`.
- Produces: `ProcessBuilder` configured with native tools (`mysqldump`/`mysql` and `pg_dump`/`psql`/`pg_restore`).

- [ ] **Step 1: Write the failing test**

```java
package com.dbbackup.engine;

import com.dbbackup.domain.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DbClientEngineTest {
    @Test
    void testPostgresCustomFormatFlagForSelectiveBackup() {
        PostgresEngine engine = new PostgresEngine();
        BackupConfig config = new BackupConfig(
            new DbConnectionConfig("postgresql", "localhost", 5432, "postgres", "pass", "mydb"),
            BackupType.FULL,
            List.of("users"), // Selective target
            true,
            Map.of()
        );
        ProcessBuilder pb = engine.buildBackupProcess(config, null);
        assertTrue(pb.command().contains("-Fc"), "Postgres selective backup must use -Fc custom format");
        assertEquals("pass", pb.environment().get("PGPASSWORD"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DbClientEngineTest`  
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

Implement `MySqlEngine` and `PostgresEngine` with environment-variable credential passing and format detection.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DbClientEngineTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: implement MySQL and PostgreSQL client process engines"
```

---

### Task 5: Storage Providers (Local, S3, Azure, GCS)

**Files:**
- Create: `src/main/java/com/dbbackup/storage/LocalStorageProvider.java`
- Create: `src/main/java/com/dbbackup/storage/AwsS3StorageProvider.java`
- Create: `src/main/java/com/dbbackup/storage/AzureBlobStorageProvider.java`
- Create: `src/main/java/com/dbbackup/storage/GcsStorageProvider.java`
- Test: `src/test/java/com/dbbackup/storage/StorageProviderTest.java`

**Interfaces:**
- Consumes: Streams & paths.
- Produces: Object store, list, retrieve, and delete capabilities.

- [ ] **Step 1: Write the failing test**

```java
package com.dbbackup.storage;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StorageProviderTest {
    @Test
    void testLocalStorageProviderStoreAndRetrieve() throws Exception {
        LocalStorageProvider provider = new LocalStorageProvider();
        Path tempDir = Files.createTempDirectory("dbbackup-storage-test");
        String destPath = tempDir.resolve("backup.tar.gz").toString();
        
        byte[] sample = "Storage Stream Test".getBytes();
        provider.store(new ByteArrayInputStream(sample), destPath, sample.length);

        InputStream in = provider.retrieve(destPath);
        assertArrayEquals(sample, in.readAllBytes());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=StorageProviderTest`  
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

Implement `LocalStorageProvider`, `AwsS3StorageProvider` (SDK v2 multipart), `AzureBlobStorageProvider`, and `GcsStorageProvider`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=StorageProviderTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: implement local and cloud storage providers (S3, Azure, GCS)"
```

---

### Task 6: Audit Logger & State Tracker (SQLite WAL Mode)

**Files:**
- Create: `src/main/java/com/dbbackup/audit/SqliteAuditLogService.java`
- Create: `src/main/java/com/dbbackup/audit/JsonStateTracker.java`
- Test: `src/test/java/com/dbbackup/audit/AuditLogServiceTest.java`

**Interfaces:**
- Consumes: `BackupHistoryRecord` & `StateRecord`.
- Produces: Persistent SQLite WAL audit database + JSON state files.

- [ ] **Step 1: Write the failing test**

```java
package com.dbbackup.audit;

import com.dbbackup.domain.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogServiceTest {
    @Test
    void testRecordCompletionAndHistoryQuery() {
        SqliteAuditLogService auditLog = new SqliteAuditLogService("jdbc:sqlite::memory:");
        auditLog.initSchema();

        BackupHistoryRecord record = new BackupHistoryRecord(
            "b-100", "mysql-prod", BackupType.FULL, DumpFormat.PLAIN_SQL,
            null, "chain-100", List.of(),
            LocalDateTime.now(), LocalDateTime.now(), 5000L, 2048L,
            "s3://bucket/b-100.sql.gz", "SUCCESS", null
        );
        auditLog.recordCompletion(record);

        List<BackupHistoryRecord> history = auditLog.getHistory(10, "mysql-prod");
        assertEquals(1, history.size());
        assertEquals("b-100", history.get(0).id());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AuditLogServiceTest`  
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

Implement `SqliteAuditLogService` with SQLite WAL mode (`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;`) and `JsonStateTracker`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AuditLogServiceTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: implement SQLite WAL audit logger and JSON state tracker"
```

---

### Task 7: Chain-Aware Retention Service (`RetentionService`)

**Files:**
- Create: `src/main/java/com/dbbackup/retention/RetentionService.java`
- Test: `src/test/java/com/dbbackup/retention/RetentionServiceTest.java`

**Interfaces:**
- Consumes: Audit log records & retention rules.
- Produces: Safe cohort deletion lists (preserving FULL base backups while active INCREMENTAL children exist).

- [ ] **Step 1: Write the failing test**

```java
package com.dbbackup.retention;

import com.dbbackup.domain.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RetentionServiceTest {
    @Test
    void testDoNotDeleteFullBackupIfIncrementalChildIsActive() {
        RetentionService service = new RetentionService();
        LocalDateTime oldDate = LocalDateTime.now().minusDays(10);
        LocalDateTime recentDate = LocalDateTime.now().minusDays(1);

        BackupHistoryRecord fullBase = new BackupHistoryRecord(
            "b-full", "prod", BackupType.FULL, DumpFormat.PLAIN_SQL,
            null, "chain-1", List.of(), oldDate, oldDate, 1000L, 5000L, "uri-1", "SUCCESS", null
        );
        BackupHistoryRecord incChild = new BackupHistoryRecord(
            "b-inc", "prod", BackupType.INCREMENTAL, DumpFormat.BINLOG,
            "b-full", "chain-1", List.of(), recentDate, recentDate, 1000L, 500L, "uri-2", "SUCCESS", null
        );

        List<String> toDelete = service.evaluateDeletions(List.of(fullBase, incChild), 5 /* keep-last 5 */);
        assertFalse(toDelete.contains("b-full"), "Full base backup must NOT be deleted while incremental child is active");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=RetentionServiceTest`  
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

Implement `RetentionService` with chain cohort grouping and retention locking.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=RetentionServiceTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: implement chain-aware backup retention service"
```

---

### Task 8: Notification Layer (Telegram, Slack, Discord)

**Files:**
- Create: `src/main/java/com/dbbackup/notification/TelegramNotificationService.java`
- Create: `src/main/java/com/dbbackup/notification/SlackNotificationService.java`
- Create: `src/main/java/com/dbbackup/notification/DiscordNotificationService.java`
- Test: `src/test/java/com/dbbackup/notification/NotificationServiceTest.java`

**Interfaces:**
- Consumes: `NotificationPayload`.
- Produces: Webhook / HTTP API alert transmissions.

- [ ] **Step 1: Write the failing test**

```java
package com.dbbackup.notification;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceTest {
    @Test
    void testTelegramNotificationServiceChannelName() {
        TelegramNotificationService service = new TelegramNotificationService("token123", "chat123");
        assertEquals("telegram", service.getChannelType());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=NotificationServiceTest`  
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

Implement `TelegramNotificationService`, `SlackNotificationService`, and `DiscordNotificationService` using Spring `RestClient` / `WebClient`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=NotificationServiceTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: implement Telegram, Slack, and Discord notification services"
```

---

### Task 9: Core Orchestrators (BackupOrchestrator & RestoreOrchestrator)

**Files:**
- Create: `src/main/java/com/dbbackup/service/BackupOrchestrator.java`
- Create: `src/main/java/com/dbbackup/service/RestoreOrchestrator.java`
- Test: `src/test/java/com/dbbackup/service/OrchestratorTest.java`

**Interfaces:**
- Consumes: Configs, Engines, Storage, Audit, and Notifications.
- Produces: End-to-end backup pipeline execution and sequential restore chain execution.

- [ ] **Step 1: Write the failing test**

```java
package com.dbbackup.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrchestratorTest {
    @Test
    void testBackupOrchestratorScopeValidationMismatchThrowsException() {
        // Verify mismatch in incremental scope vs full parent scope throws IllegalArgumentException
        BackupOrchestrator orchestrator = new BackupOrchestrator(null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.validateChainScope(List.of("users"), List.of("orders"));
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=OrchestratorTest`  
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

Implement `BackupOrchestrator` (streaming pipeline execution, scope validation, encryption wrapping) and `RestoreOrchestrator` (mixed restore chain sequential execution).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=OrchestratorTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: implement BackupOrchestrator and RestoreOrchestrator"
```

---

### Task 10: Spring Shell CLI Commands & Daemon Scheduler

**Files:**
- Create: `src/main/java/com/dbbackup/cli/BackupCommands.java`
- Create: `src/main/java/com/dbbackup/cli/RestoreCommands.java`
- Create: `src/main/java/com/dbbackup/cli/HistoryCommands.java`
- Create: `src/main/java/com/dbbackup/daemon/DaemonSchedulerService.java`
- Test: `src/test/java/com/dbbackup/cli/BackupCommandsTest.java`

**Interfaces:**
- Consumes: CLI user flags & YAML schedule files.
- Produces: Executable CLI commands and background Quartz/Spring Cron daemon.

- [ ] **Step 1: Write the failing test**

```java
package com.dbbackup.cli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackupCommandsTest {
    @Test
    void testTestConnectionCommandOutput() {
        BackupCommands commands = new BackupCommands(null);
        assertNotNull(commands);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=BackupCommandsTest`  
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

Implement `BackupCommands`, `RestoreCommands`, `HistoryCommands`, and `DaemonSchedulerService` (with `on-overlap` policy support).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=BackupCommandsTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: implement Spring Shell CLI commands and cron daemon scheduler"
```
