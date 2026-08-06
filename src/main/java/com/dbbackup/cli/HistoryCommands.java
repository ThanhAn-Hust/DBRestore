package com.dbbackup.cli;

import com.dbbackup.audit.SqliteAuditLogService;
import com.dbbackup.domain.model.BackupHistoryRecord;
import com.dbbackup.domain.port.AuditLogService;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

@ShellComponent
@Command(command = "history", group = "History Commands")
public class HistoryCommands {

    private final AuditLogService auditLogService;

    public HistoryCommands() {
        this(new SqliteAuditLogService());
    }

    public HistoryCommands(AuditLogService auditLogService) {
        this.auditLogService = auditLogService != null ? auditLogService : new SqliteAuditLogService();
    }

    @ShellMethod(key = "history", value = "Query audit log history")
    @Command(command = "history", description = "Query audit log history")
    public String history(
        @ShellOption(value = {"--profile", "-p"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "profile", shortNames = 'p', defaultValue = "") String profile,

        @ShellOption(value = {"--limit", "-l"}, defaultValue = "20")
        @Option(longNames = "limit", shortNames = 'l', defaultValue = "20") int limit
    ) {
        try {
            List<BackupHistoryRecord> records = auditLogService.getHistory(limit > 0 ? limit : 20, profile);
            if (records == null || records.isEmpty()) {
                return "No audit history records found.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-25s | %-15s | %-12s | %-12s | %-10s | %-10s | %s\n",
                "ID", "PROFILE/DB", "TYPE", "FORMAT", "SIZE(B)", "STATUS", "START TIME"));
            sb.append("-".repeat(110)).append("\n");

            for (BackupHistoryRecord r : records) {
                sb.append(String.format("%-25s | %-15s | %-12s | %-12s | %-10d | %-10s | %s\n",
                    r.id(),
                    r.dbName() != null ? r.dbName() : "N/A",
                    r.backupType() != null ? r.backupType() : "N/A",
                    r.dumpFormat() != null ? r.dumpFormat() : "N/A",
                    r.sizeBytes() != null ? r.sizeBytes() : 0,
                    r.status() != null ? r.status() : "N/A",
                    r.startTime() != null ? r.startTime().toString() : "N/A"
                ));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Failed to retrieve history: " + e.getMessage();
        }
    }
}
