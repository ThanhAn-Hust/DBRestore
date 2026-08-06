package com.dbbackup.service;

import com.dbbackup.domain.model.*;
import com.dbbackup.domain.port.*;
import com.dbbackup.engine.MySqlEngine;
import com.dbbackup.engine.PostgresEngine;
import com.dbbackup.retention.RetentionService;
import com.dbbackup.security.SegmentedCipherOutputStream;
import com.dbbackup.storage.StorageProviderFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

@Service
public class BackupOrchestrator {

    private final List<DbClientEngine> engines;
    private final StorageProviderFactory storageProviderFactory;
    private final AuditLogService auditLogService;
    private final StateTracker stateTracker;
    private final List<NotificationService> notificationServices;
    private final RetentionService retentionService;

    public BackupOrchestrator() {
        this(
            List.of(new MySqlEngine(), new PostgresEngine()),
            new StorageProviderFactory(),
            null,
            null,
            List.of(),
            new RetentionService()
        );
    }

    public BackupOrchestrator(
        List<DbClientEngine> engines,
        StorageProviderFactory storageProviderFactory,
        AuditLogService auditLogService,
        StateTracker stateTracker,
        List<NotificationService> notificationServices
    ) {
        this(engines, storageProviderFactory, auditLogService, stateTracker, notificationServices, new RetentionService());
    }

    public BackupOrchestrator(
        List<DbClientEngine> engines,
        StorageProviderFactory storageProviderFactory,
        AuditLogService auditLogService,
        StateTracker stateTracker,
        List<NotificationService> notificationServices,
        RetentionService retentionService
    ) {
        this.engines = engines != null ? new ArrayList<>(engines) : List.of(new MySqlEngine(), new PostgresEngine());
        this.storageProviderFactory = storageProviderFactory != null ? storageProviderFactory : new StorageProviderFactory();
        this.auditLogService = auditLogService;
        this.stateTracker = stateTracker;
        this.notificationServices = notificationServices != null ? new ArrayList<>(notificationServices) : List.of();
        this.retentionService = retentionService != null ? retentionService : new RetentionService();
    }

    public void validateChainScope(List<String> parentTables, List<String> currentTables) {
        Set<String> parentSet = parentTables == null ? Collections.emptySet() : new HashSet<>(parentTables);
        Set<String> currentSet = currentTables == null ? Collections.emptySet() : new HashSet<>(currentTables);
        if (!parentSet.equals(currentSet)) {
            throw new IllegalArgumentException(
                "Incremental/Differential table scope " + currentSet + " does not match parent full base table scope " + parentSet
            );
        }
    }

    public DbClientEngine selectEngine(String dbType) {
        if (dbType == null || dbType.isBlank()) {
            throw new IllegalArgumentException("Database type cannot be null or blank");
        }
        for (DbClientEngine engine : engines) {
            if (engine.supports(dbType)) {
                return engine;
            }
        }
        throw new IllegalArgumentException("No DbClientEngine available for database type: " + dbType);
    }

    public BackupHistoryRecord executeBackup(BackupConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("BackupConfig cannot be null");
        }
        DbConnectionConfig dbConn = config.connectionConfig();
        String dbType = dbConn != null ? dbConn.dbType() : null;
        DbClientEngine engine = selectEngine(dbType);

        String destinationUri = config.storageUri() != null ? config.storageUri() : "file:///backups/backup.sql";
        StorageProvider storageProvider = storageProviderFactory.getProvider(destinationUri);

        String parentId = config.parentBackupId();
        String chainId = config.chainId();

        if (config.backupType() == BackupType.INCREMENTAL || config.backupType() == BackupType.DIFFERENTIAL) {
            if (parentId != null && !parentId.isBlank() && auditLogService != null) {
                BackupHistoryRecord parentRecord = auditLogService.getRecordById(parentId);
                if (parentRecord != null) {
                    validateChainScope(parentRecord.tables(), config.tables());
                    if (chainId == null || chainId.isBlank()) {
                        chainId = parentRecord.chainId() != null ? parentRecord.chainId() : parentRecord.id();
                    }
                }
            }
        }

        String backupId = "b-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        if (chainId == null || chainId.isBlank()) {
            chainId = backupId;
        }

        LocalDateTime startTime = LocalDateTime.now();
        DumpFormat dumpFormat = engine.getDumpFormat(config);
        String dbName = dbConn != null && dbConn.databaseName() != null ? dbConn.databaseName() : "default";

        BackupHistoryRecord inProgressRecord = new BackupHistoryRecord(
            backupId,
            dbName,
            config.backupType() != null ? config.backupType() : BackupType.FULL,
            dumpFormat,
            parentId,
            chainId,
            config.tables() != null ? config.tables() : List.of(),
            startTime,
            null,
            0L,
            0L,
            destinationUri,
            "IN_PROGRESS",
            null
        );

        if (auditLogService != null) {
            auditLogService.recordCompletion(inProgressRecord);
        }

        ProcessBuilder pb = engine.buildBackupProcess(config, stateTracker);
        AtomicLong bytesWritten = new AtomicLong(0);
        AtomicReference<Throwable> streamError = new AtomicReference<>();

        try {
            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos, 65536);
            CountingOutputStream cos = new CountingOutputStream(pos, bytesWritten);

            Thread copyThread = new Thread(() -> {
                try (OutputStream wrappedOut = wrapBackupOutputStream(cos, config, dumpFormat)) {
                    Process process = pb.start();
                    try (InputStream processIn = process.getInputStream()) {
                        processIn.transferTo(wrappedOut);
                    }
                    wrappedOut.flush();
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        String err = "";
                        try (InputStream errIn = process.getErrorStream()) {
                            err = new String(errIn.readAllBytes());
                        }
                        throw new RuntimeException("Backup process failed with exit code " + exitCode + ": " + err);
                    }
                } catch (Throwable t) {
                    streamError.set(t);
                }
            }, "dbbackup-pipeline-thread");

            copyThread.start();
            storageProvider.store(pis, destinationUri, 0L);
            copyThread.join();

            if (streamError.get() != null) {
                throw streamError.get();
            }

            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();

            BackupHistoryRecord successRecord = new BackupHistoryRecord(
                backupId,
                dbName,
                config.backupType() != null ? config.backupType() : BackupType.FULL,
                dumpFormat,
                parentId,
                chainId,
                config.tables() != null ? config.tables() : List.of(),
                startTime,
                endTime,
                durationMs,
                bytesWritten.get(),
                destinationUri,
                "SUCCESS",
                null
            );

            if (auditLogService != null) {
                auditLogService.recordCompletion(successRecord);
            }

            sendNotifications("Backup Successful: " + dbName, "Backup completed successfully for database '" + dbName + "' (ID: " + backupId + ")", true);
            runRetentionCleanup(dbName, storageProvider);

            return successRecord;

        } catch (Throwable t) {
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();

            BackupHistoryRecord failedRecord = new BackupHistoryRecord(
                backupId,
                dbName,
                config.backupType() != null ? config.backupType() : BackupType.FULL,
                dumpFormat,
                parentId,
                chainId,
                config.tables() != null ? config.tables() : List.of(),
                startTime,
                endTime,
                durationMs,
                bytesWritten.get(),
                destinationUri,
                "FAILED",
                t.getMessage()
            );

            if (auditLogService != null) {
                auditLogService.recordCompletion(failedRecord);
            }

            sendNotifications("Backup Failed: " + dbName, "Backup failed for database '" + dbName + "': " + t.getMessage(), false);

            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Backup execution failed", t);
        }
    }

    private OutputStream wrapBackupOutputStream(OutputStream out, BackupConfig config, DumpFormat dumpFormat) throws IOException {
        OutputStream wrapped = out;
        if (config.encrypted() && config.encryptionPassphrase() != null && !config.encryptionPassphrase().isBlank()) {
            wrapped = new SegmentedCipherOutputStream(wrapped, config.encryptionPassphrase());
        }
        if (config.compressed() && dumpFormat != DumpFormat.CUSTOM_FC) {
            wrapped = new GZIPOutputStream(wrapped);
        }
        return wrapped;
    }

    private void runRetentionCleanup(String dbName, StorageProvider storageProvider) {
        if (retentionService == null || auditLogService == null) {
            return;
        }
        try {
            List<BackupHistoryRecord> history = auditLogService.getHistory(1000, dbName);
            List<String> toDelete = retentionService.evaluateDeletions(history, 5);
            for (String recordId : toDelete) {
                BackupHistoryRecord recordToDelete = auditLogService.getRecordById(recordId);
                if (recordToDelete != null && recordToDelete.storageUri() != null) {
                    try {
                        storageProvider.delete(recordToDelete.storageUri());
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void sendNotifications(String title, String message, boolean success) {
        if (notificationServices == null) return;
        NotificationService.NotificationPayload payload = new NotificationService.NotificationPayload(title, message, success);
        for (NotificationService ns : notificationServices) {
            if (ns != null) {
                try {
                    ns.sendNotification(payload);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static class CountingOutputStream extends FilterOutputStream {
        private final AtomicLong counter;

        public CountingOutputStream(OutputStream out, AtomicLong counter) {
            super(out);
            this.counter = counter;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            counter.incrementAndGet();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            counter.addAndGet(len);
        }
    }
}
