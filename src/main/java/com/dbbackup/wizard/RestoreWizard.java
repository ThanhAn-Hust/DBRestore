package com.dbbackup.wizard;

import com.dbbackup.domain.model.BackupHistoryRecord;
import com.dbbackup.domain.model.DbConnectionConfig;
import com.dbbackup.domain.port.AuditLogService;
import com.dbbackup.i18n.I18nService;
import com.dbbackup.service.RestoreOrchestrator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RestoreWizard {
    private static final String PROMPT_PRESS_ENTER = "prompt.press_enter";
    private static final String DB_POSTGRESQL = "postgresql";
    private static final String DB_MYSQL = "mysql";

    private final RestoreOrchestrator restoreOrchestrator;
    private final AuditLogService auditLogService;
    private final I18nService i18n;

    public RestoreWizard(RestoreOrchestrator restoreOrchestrator, AuditLogService auditLogService, I18nService i18n) {
        this.restoreOrchestrator = restoreOrchestrator;
        this.auditLogService = auditLogService;
        this.i18n = i18n;
    }

    public void run(PromptReader reader) {
        reader.printInfo(i18n.getMessage("restore.header"));
        String backupId = selectBackupId(reader);

        if (backupId.isBlank()) {
            reader.printError("Backup ID cannot be empty.");
            reader.waitForEnter(i18n.getMessage(PROMPT_PRESS_ENTER));
            return;
        }

        String passphrase = reader.readPassword(i18n.getMessage("restore.passphrase"));
        if (passphrase.isBlank()) {
            passphrase = null;
        }

        DbConnectionConfig targetConn = selectTargetConnection(reader);

        reader.printError(i18n.getMessage("restore.warning"));
        if (!reader.readBoolean(i18n.getMessage("prompt.confirm"), false)) {
            reader.printInfo(i18n.getMessage("status.cancelled"));
            reader.waitForEnter(i18n.getMessage(PROMPT_PRESS_ENTER));
            return;
        }

        reader.printInfo(i18n.getMessage("restore.executing"));
        try {
            restoreOrchestrator.restoreChain(backupId, passphrase, targetConn);
            reader.printSuccess(i18n.getMessage("restore.done", backupId));
        } catch (Exception e) {
            reader.printError(i18n.getMessage("status.failed") + ": " + e.getMessage());
        }
        reader.waitForEnter(i18n.getMessage(PROMPT_PRESS_ENTER));
    }

    private String selectBackupId(PromptReader reader) {
        List<BackupHistoryRecord> history = auditLogService != null ? auditLogService.getHistory(15, null) : List.of();
        if (!history.isEmpty()) {
            List<String> options = new ArrayList<>();
            for (BackupHistoryRecord r : history) {
                options.add(r.startTime() + " | " + r.dbName() + " | " + r.backupType() + " | " + r.sizeBytes() + " B | ID: " + r.id());
            }
            options.add(i18n.getMessage("restore.manual_id"));
            int choice = reader.readChoice(i18n.getMessage("restore.select_backup"), options, 1);
            if (choice < history.size()) {
                return history.get(choice).id();
            }
        } else {
            reader.printInfo(i18n.getMessage("restore.no_backups"));
        }
        return reader.readString(i18n.getMessage("restore.enter_id"), "");
    }

    private DbConnectionConfig selectTargetConnection(PromptReader reader) {
        int targetChoice = reader.readInt(i18n.getMessage("restore.dest_choice"), 1, 2, 1);
        if (targetChoice != 2) {
            return null;
        }
        Map<String, DbConnectionConfig> profiles = TestConnectionWizard.loadProfiles();
        List<String> profileNames = new ArrayList<>(profiles.keySet());
        if (!profileNames.isEmpty()) {
            List<String> profileOpts = new ArrayList<>(profileNames);
            profileOpts.add(i18n.getMessage("prompt.choose_profile_custom"));
            int pChoice = reader.readChoice(i18n.getMessage("prompt.choose_profile"), profileOpts, 1);
            if (pChoice < profileNames.size()) {
                return profiles.get(profileNames.get(pChoice));
            }
        }
        return promptCustomConnection(reader);
    }

    private DbConnectionConfig promptCustomConnection(PromptReader reader) {
        int dbChoice = reader.readInt(i18n.getMessage("prompt.db_type"), 1, 2, 1);
        String type = (dbChoice == 2) ? DB_POSTGRESQL : DB_MYSQL;
        int defaultPort = DB_POSTGRESQL.equals(type) ? 5432 : 3306;

        String host = reader.readString(i18n.getMessage("prompt.host"), "127.0.0.1");
        int port = reader.readInt(i18n.getMessage("prompt.port"), 1, 65535, defaultPort);
        String database = reader.readString(i18n.getMessage("prompt.database"), "restored_db");
        String username = reader.readString(i18n.getMessage("prompt.username"), DB_POSTGRESQL.equals(type) ? "postgres" : "root");
        String password = reader.readPassword(i18n.getMessage("prompt.password"));

        return new DbConnectionConfig(type, host, port, username, password, database);
    }
}