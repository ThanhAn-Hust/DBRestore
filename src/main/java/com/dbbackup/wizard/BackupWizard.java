package com.dbbackup.wizard;

import com.dbbackup.domain.model.BackupConfig;
import com.dbbackup.domain.model.BackupHistoryRecord;
import com.dbbackup.domain.model.BackupType;
import com.dbbackup.domain.model.DbConnectionConfig;
import com.dbbackup.domain.model.DumpFormat;
import com.dbbackup.i18n.I18nService;
import com.dbbackup.service.BackupOrchestrator;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BackupWizard {
    private static final String EXT_SQL_GZ = ".sql.gz";
    private static final String DB_POSTGRESQL = "postgresql";
    private static final String DB_MYSQL = "mysql";

    private final BackupOrchestrator orchestrator;
    private final I18nService i18n;

    public BackupWizard(BackupOrchestrator orchestrator, I18nService i18n) {
        this.orchestrator = orchestrator;
        this.i18n = i18n;
    }

    public void run(PromptReader reader) {
        reader.printInfo(i18n.getMessage("backup.header"));
        DbConnectionConfig connConfig = selectSourceConnection(reader);
        BackupType backupType = selectBackupType(reader);
        List<String> tables = selectTables(reader);
        String destinationUri = selectDestination(reader, connConfig.databaseName());
        String passphrase = configureEncryption(reader);
        boolean encrypt = passphrase != null;
        List<String> notifications = selectNotifications(reader);

        printSummary(reader, connConfig, backupType, destinationUri, encrypt);

        if (!reader.readBoolean(i18n.getMessage("prompt.confirm"), true)) {
            reader.printInfo(i18n.getMessage("status.cancelled"));
            reader.waitForEnter(i18n.getMessage("prompt.press_enter"));
            return;
        }

        Map<String, Object> extraParams = notifications.isEmpty() ? Map.of() : Map.of("notifications", notifications);
        BackupConfig finalConfig = new BackupConfig(
                connConfig, backupType, DumpFormat.PLAIN_SQL, tables,
                true, encrypt, passphrase, destinationUri, null, null, extraParams
        );

        reader.printInfo(i18n.getMessage("backup.executing"));
        try {
            BackupHistoryRecord historyRecord = orchestrator.executeBackup(finalConfig);
            reader.printSuccess(i18n.getMessage("backup.done", historyRecord.id(), String.valueOf(historyRecord.sizeBytes()) + " bytes", String.valueOf(historyRecord.durationMs())));
        } catch (Exception e) {
            reader.printError(i18n.getMessage("status.failed") + ": " + e.getMessage());
        }
        reader.waitForEnter(i18n.getMessage("prompt.press_enter"));
    }

    private DbConnectionConfig selectSourceConnection(PromptReader reader) {
        Map<String, DbConnectionConfig> profiles = TestConnectionWizard.loadProfiles();
        List<String> profileNames = new ArrayList<>(profiles.keySet());
        if (!profileNames.isEmpty()) {
            List<String> options = new ArrayList<>();
            for (String p : profileNames) {
                DbConnectionConfig c = profiles.get(p);
                options.add(p + " (" + c.dbType() + " -> " + c.host() + ":" + c.port() + "/" + c.databaseName() + ")");
            }
            options.add(i18n.getMessage("prompt.choose_profile_custom"));
            int choice = reader.readChoice(i18n.getMessage("backup.choose_source"), options, 1);
            if (choice < profileNames.size()) {
                return profiles.get(profileNames.get(choice));
            }
        }
        return promptCustomConnection(reader);
    }

    private BackupType selectBackupType(PromptReader reader) {
        int bTypeChoice = reader.readInt(i18n.getMessage("backup.type"), 1, 3, 1);
        return switch (bTypeChoice) {
            case 2 -> BackupType.INCREMENTAL;
            case 3 -> BackupType.DIFFERENTIAL;
            default -> BackupType.FULL;
        };
    }

    private List<String> selectTables(PromptReader reader) {
        String tablesInput = reader.readString(i18n.getMessage("backup.tables"), "");
        if (tablesInput.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tablesInput.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String selectDestination(PromptReader reader, String databaseName) {
        int storageChoice = reader.readInt(i18n.getMessage("backup.storage"), 1, 4, 1);
        switch (storageChoice) {
            case 1 -> {
                String bucket = reader.readString("S3 Bucket & Key (e.g. my-bucket/backups/" + databaseName + EXT_SQL_GZ + ")", "my-bucket/backups/" + databaseName + EXT_SQL_GZ);
                return bucket.startsWith("s3://") ? bucket : "s3://" + bucket;
            }
            case 2 -> {
                String blob = reader.readString("Azure Container & Blob Path", "backups/" + databaseName + EXT_SQL_GZ);
                return blob.startsWith("azure://") ? blob : "azure://" + blob;
            }
            case 3 -> {
                String gcs = reader.readString("GCS Bucket & Object Path", "my-gcs-bucket/backups/" + databaseName + EXT_SQL_GZ);
                return gcs.startsWith("gs://") ? gcs : "gs://" + gcs;
            }
            default -> {
                String localPath = reader.readString("Local file path", "./backups/" + databaseName + "_" + System.currentTimeMillis() + EXT_SQL_GZ);
                return localPath.startsWith("file://") ? localPath : "file://" + localPath;
            }
        }
    }

    private String configureEncryption(PromptReader reader) {
        boolean encrypt = reader.readBoolean(i18n.getMessage("backup.encrypt"), true);
        if (!encrypt) {
            return null;
        }
        while (true) {
            String passphrase = reader.readPassword(i18n.getMessage("backup.passphrase"));
            String confirm = reader.readPassword(i18n.getMessage("backup.passphrase_confirm"));
            if (passphrase != null && !passphrase.isEmpty() && passphrase.equals(confirm)) {
                return passphrase;
            }
            reader.printError(i18n.getMessage("backup.passphrase_mismatch"));
        }
    }

    private List<String> selectNotifications(PromptReader reader) {
        String notifInput = reader.readString(i18n.getMessage("backup.notifications"), "");
        if (notifInput.isBlank()) {
            return List.of();
        }
        return Arrays.stream(notifInput.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private void printSummary(PromptReader reader, DbConnectionConfig connConfig, BackupType backupType, String destinationUri, boolean encrypt) {
        reader.printInfo(i18n.getMessage("backup.summary.header"));
        reader.printInfo(i18n.getMessage("backup.summary.source", connConfig.databaseName(), connConfig.host() + ":" + connConfig.port()));
        reader.printInfo(i18n.getMessage("backup.summary.type", backupType));
        reader.printInfo(i18n.getMessage("backup.summary.dest", destinationUri));
        reader.printInfo(i18n.getMessage("backup.summary.encrypted", encrypt));
    }

    private DbConnectionConfig promptCustomConnection(PromptReader reader) {
        int dbChoice = reader.readInt(i18n.getMessage("prompt.db_type"), 1, 2, 1);
        String type = (dbChoice == 2) ? DB_POSTGRESQL : DB_MYSQL;
        int defaultPort = DB_POSTGRESQL.equals(type) ? 5432 : 3306;

        String host = reader.readString(i18n.getMessage("prompt.host"), "127.0.0.1");
        int port = reader.readInt(i18n.getMessage("prompt.port"), 1, 65535, defaultPort);
        String database = reader.readString(i18n.getMessage("prompt.database"), "shopdb");
        String username = reader.readString(i18n.getMessage("prompt.username"), DB_POSTGRESQL.equals(type) ? "postgres" : "root");
        String password = reader.readPassword(i18n.getMessage("prompt.password"));

        return new DbConnectionConfig(type, host, port, username, password, database);
    }
}