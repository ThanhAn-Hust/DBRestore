package com.dbbackup.service;

import com.dbbackup.domain.model.*;
import com.dbbackup.domain.port.*;
import com.dbbackup.engine.MySqlEngine;
import com.dbbackup.engine.PostgresEngine;
import com.dbbackup.security.SegmentedCipherInputStream;
import com.dbbackup.storage.StorageProviderFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.*;
import java.util.zip.GZIPInputStream;

@Service
public class RestoreOrchestrator {

    private final List<DbClientEngine> engines;
    private final StorageProviderFactory storageProviderFactory;
    private final AuditLogService auditLogService;
    private final List<NotificationService> notificationServices;

    public RestoreOrchestrator() {
        this(
            List.of(new MySqlEngine(), new PostgresEngine()),
            new StorageProviderFactory(),
            null,
            List.of()
        );
    }

    public RestoreOrchestrator(
        List<DbClientEngine> engines,
        StorageProviderFactory storageProviderFactory,
        AuditLogService auditLogService,
        List<NotificationService> notificationServices
    ) {
        this.engines = engines != null ? new ArrayList<>(engines) : List.of(new MySqlEngine(), new PostgresEngine());
        this.storageProviderFactory = storageProviderFactory != null ? storageProviderFactory : new StorageProviderFactory();
        this.auditLogService = auditLogService;
        this.notificationServices = notificationServices != null ? new ArrayList<>(notificationServices) : List.of();
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

    public void restoreChain(String targetBackupId, String passphrase) {
        restoreChain(targetBackupId, passphrase, null);
    }

    public void restoreChain(String targetBackupId, String passphrase, DbConnectionConfig connectionConfig) {
        if (targetBackupId == null || targetBackupId.isBlank()) {
            throw new IllegalArgumentException("targetBackupId cannot be null or blank");
        }
        if (auditLogService == null) {
            throw new IllegalStateException("AuditLogService is required to resolve restore chain for ID: " + targetBackupId);
        }

        BackupHistoryRecord targetRecord = auditLogService.getRecordById(targetBackupId);
        if (targetRecord == null) {
            throw new IllegalArgumentException("Target backup record not found: " + targetBackupId);
        }

        LinkedList<BackupHistoryRecord> chain = new LinkedList<>();
        BackupHistoryRecord curr = targetRecord;
        Set<String> visited = new HashSet<>();

        while (curr != null) {
            if (!visited.add(curr.id())) {
                throw new IllegalStateException("Circular backup chain detected at ID: " + curr.id());
            }
            chain.addFirst(curr);
            if (curr.parentId() == null || curr.parentId().isBlank()) {
                break;
            }
            curr = auditLogService.getRecordById(curr.parentId());
            if (curr == null) {
                throw new IllegalStateException("Parent backup record not found in chain: " + chain.peekFirst().parentId());
            }
        }

        for (BackupHistoryRecord record : chain) {
            restoreRecord(record, passphrase, connectionConfig);
        }

        sendNotifications("Restore Successful", "Successfully restored backup chain up to target " + targetBackupId, true);
    }

    private void restoreRecord(BackupHistoryRecord record, String passphrase, DbConnectionConfig connectionConfig) {
        String dbType = connectionConfig != null ? connectionConfig.dbType() : resolveDbType(record);
        DbClientEngine engine = selectEngine(dbType);

        DbConnectionConfig dbConn = connectionConfig != null ? connectionConfig : new DbConnectionConfig(dbType, "localhost", 0, "root", "", record.dbName());
        StorageProvider storageProvider = storageProviderFactory.getProvider(record.storageUri());

        try (InputStream rawStream = storageProvider.retrieve(record.storageUri())) {
            InputStream in = rawStream;
            if (passphrase != null && !passphrase.isBlank()) {
                in = new SegmentedCipherInputStream(in, passphrase);
            }

            PushbackInputStream pbIn = new PushbackInputStream(in, 2);
            byte[] header = new byte[2];
            int read = pbIn.read(header);
            if (read == 2 && (header[0] & 0xFF) == 0x1F && (header[1] & 0xFF) == 0x8B) {
                pbIn.unread(header);
                in = new GZIPInputStream(pbIn);
            } else if (read > 0) {
                pbIn.unread(header, 0, read);
                in = pbIn;
            }

            RestoreConfig restoreConfig = new RestoreConfig(
                dbConn,
                record.storageUri(),
                record.dumpFormat(),
                record.tables(),
                false,
                passphrase != null && !passphrase.isBlank(),
                passphrase,
                Map.of()
            );

            ProcessBuilder pb = engine.buildRestoreProcess(restoreConfig, in);
            Process process = pb.start();

            try (OutputStream processOut = process.getOutputStream()) {
                in.transferTo(processOut);
                processOut.flush();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errStr = "";
                try (InputStream errIn = process.getErrorStream()) {
                    errStr = new String(errIn.readAllBytes());
                }
                throw new RuntimeException("Restore process for record " + record.id() + " failed with exit code " + exitCode + ": " + errStr);
            }
        } catch (Exception e) {
            sendNotifications("Restore Failed", "Failed to restore backup " + record.id() + ": " + e.getMessage(), false);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Restore execution failed for record " + record.id(), e);
        }
    }

    private String resolveDbType(BackupHistoryRecord record) {
        if (record != null && record.dumpFormat() != null) {
            DumpFormat format = record.dumpFormat();
            if (format == DumpFormat.CUSTOM_FC || format == DumpFormat.WAL) {
                return "postgresql";
            }
            if (format == DumpFormat.BINLOG) {
                return "mysql";
            }
            if (format == DumpFormat.OPLOG) {
                return "mongodb";
            }
        }
        return "mysql";
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
}
