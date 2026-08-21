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
    private final BackupOrchestrator orchestrator;
    private final I18nService i18n;

    public BackupWizard(BackupOrchestrator orchestrator, I18nService i18n) {
        this.orchestrator = orchestrator;
        this.i18n = i18n;
    }

    public void run(PromptReader reader) {
        reader.printInfo(i18n.getMessage("backup.header"));
        Map<String, DbConnectionConfig> profiles = TestConnectionWizard.loadProfiles();
        List<String> profileNames = new ArrayList<>(profiles.keySet());

        DbConnectionConfig connConfig;
        if (!profileNames.isEmpty()) {
            List<String> options = new ArrayList<>();
            for (String p : profileNames) {
                DbConnectionConfig c = profiles.get(p);
                options.add(p + " (" + c.dbType() + " -> " + c.host() + ":" + c.port() + "/" + c.databaseName() + ")");
            }
            options.add(i18n.getMessage("prompt.choose_profile_custom"));
            int choice = reader.readChoice(i18n.getMessage("backup.choose_source"), options, 1);
            if (choice < profileNames.size()) {
                connConfig = profiles.get(profileNames.get(choice));
            } else {
                connConfig = promptCustomConnection(reader);
            }
        } else {
            connConfig = promptCustomConnection(reader);
        }

        int bTypeChoice = reader.readInt(i18n.getMessage("backup.type"), 1, 3, 1);
        BackupType backupType = switch (bTypeChoice) {
            case 2 -> BackupType.INCREMENTAL;
            case 3 -> BackupType.DIFFERENTIAL;
            default -> BackupType.FULL;
        };

        String tablesInput = reader.readString(i18n.getMessage("backup.tables"), "");
        List<String> tables = tablesInput.isBlank() ? List.of() : Arrays.asList(tablesInput.split("\\s*,\\s*"));

        int storageChoice = reader.readInt(i18n.getMessage("backup.storage"), 1, 4, 1);
        String destinationUri;
        switch (storageChoice) {
            case 1 -> {
                String bucket = reader.readString("S3 Bucket & Key (e.g. my-bucket/backups/" + connConfig.databaseName() + ".sql.gz)", "my-bucket/backups/" + connConfig.databaseName() + ".sql.gz");
                destinationUri = bucket.startsWith("s3://") ? bucket : "s3://" + bucket;
            }
            case 2 -> {
                String blob = reader.readString("Azure Container & Blob Path", "backups/" + connConfig.databaseName() + ".sql.gz");
                destinationUri = blob.startsWith("azure://") ? blob : "azure://" + blob;
            }
            case 3 -> {
                String gcs = reader.readString("GCS Bucket & Object Path", "my-gcs-bucket/backups/" + connConfig.databaseName() + ".sql.gz");
                destinationUri = gcs.startsWith("gs://") ? gcs : "gs://" + gcs;
            }
            default -> {
                String localPath = reader.readString("Local file path", "./backups/" + connConfig.databaseName() + "_" + System.currentTimeMillis() + ".sql.gz");
                destinationUri = localPath.startsWith("file://") ? localPath : "file://" + localPath;
            }
        }

        boolean encrypt = reader.readBoolean(i18n.getMessage("backup.encrypt"), true);
        String passphrase = null;
        if (encrypt) {
            while (true) {
                passphrase = reader.readPassword(i18n.getMessage("backup.passphrase"));
                String confirm = reader.readPassword(i18n.getMessage("backup.passphrase_confirm"));
                if (passphrase != null && !passphrase.isEmpty() && passphrase.equals(confirm)) {
                    break;
                }
                reader.printError(i18n.getMessage("backup.passphrase_mismatch"));
            }
        }

        String notifInput = reader.readString(i18n.getMessage("backup.notifications"), "");
        List<String> notifications = notifInput.isBlank() ? List.of() : Arrays.asList(notifInput.split("\\s*,\\s*"));
        Map<String, Object> extraParams = notifications.isEmpty() ? Map.of() : Map.of("notifications", notifications);

        reader.printInfo(i18n.getMessage("backup.summary.header"));
        reader.printInfo(i18n.getMessage("backup.summary.source", connConfig.databaseName(), connConfig.host() + ":" + connConfig.port()));
        reader.printInfo(i18n.getMessage("backup.summary.type", backupType));
        reader.printInfo(i18n.getMessage("backup.summary.dest", destinationUri));
        reader.printInfo(i18n.getMessage("backup.summary.encrypted", encrypt));

        if (!reader.readBoolean(i18n.getMessage("prompt.confirm"), true)) {
            reader.printInfo(i18n.getMessage("status.cancelled"));
            reader.waitForEnter(i18n.getMessage("prompt.press_enter"));
            return;
        }

        BackupConfig finalConfig = new BackupConfig(
                connConfig, backupType, DumpFormat.PLAIN_SQL, tables,
                true, encrypt, passphrase, destinationUri, null, null, extraParams
        );

        reader.printInfo(i18n.getMessage("backup.executing"));
        try {
            BackupHistoryRecord record = orchestrator.executeBackup(finalConfig);
            reader.printSuccess(i18n.getMessage("backup.done", record.id(), String.valueOf(record.sizeBytes()) + " bytes", String.valueOf(record.durationMs())));
        } catch (Exception e) {
            reader.printError(i18n.getMessage("status.failed") + ": " + e.getMessage());
        }
        reader.waitForEnter(i18n.getMessage("prompt.press_enter"));
    }

    private DbConnectionConfig promptCustomConnection(PromptReader reader) {
        int dbChoice = reader.readInt(i18n.getMessage("prompt.db_type"), 1, 2, 1);
        String type = (dbChoice == 2) ? "postgresql" : "mysql";
        int defaultPort = "postgresql".equals(type) ? 5432 : 3306;

        String host = reader.readString(i18n.getMessage("prompt.host"), "127.0.0.1");
        int port = reader.readInt(i18n.getMessage("prompt.port"), 1, 65535, defaultPort);
        String database = reader.readString(i18n.getMessage("prompt.database"), "shopdb");
        String username = reader.readString(i18n.getMessage("prompt.username"), "postgresql".equals(type) ? "postgres" : "root");
        String password = reader.readPassword(i18n.getMessage("prompt.password"));

        return new DbConnectionConfig(type, host, port, username, password, database);
    }
}