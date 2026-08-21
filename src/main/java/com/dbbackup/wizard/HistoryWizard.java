package com.dbbackup.wizard;

import com.dbbackup.domain.model.BackupHistoryRecord;
import com.dbbackup.domain.port.AuditLogService;
import com.dbbackup.i18n.I18nService;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.util.List;

@Component
public class HistoryWizard {
    private final AuditLogService auditLogService;
    private final I18nService i18n;

    public HistoryWizard(AuditLogService auditLogService, I18nService i18n) {
        this.auditLogService = auditLogService;
        this.i18n = i18n;
    }

    public void run(PromptReader reader) {
        run(reader, reader.getOut());
    }

    public void run(PromptReader reader, PrintStream out) {
        reader.printInfo(i18n.getMessage("history.header"));
        int limit = reader.readInt("Number of recent records to display", 1, 100, 15);
        List<BackupHistoryRecord> history = auditLogService != null ? auditLogService.getHistory(limit, null) : List.of();

        if (history.isEmpty()) {
            reader.printInfo(i18n.getMessage("history.empty"));
        } else {
            out.println();
            out.printf("%-24s %-16s %-12s %-10s %-14s %-20s%n", "ID", "DB NAME", "TYPE", "STATUS", "SIZE", "START TIME");
            out.println("-".repeat(98));
            for (BackupHistoryRecord r : history) {
                long size = r.sizeBytes() != null ? r.sizeBytes() : 0L;
                String sizeStr = size > 1024 * 1024
                        ? String.format("%.2f MB", (double) size / (1024 * 1024))
                        : size + " B";
                out.printf("%-24s %-16s %-12s %-10s %-14s %-20s%n",
                        r.id(),
                        r.dbName() != null ? r.dbName() : "-",
                        r.backupType(),
                        r.status(),
                        sizeStr,
                        r.startTime());
            }
        }
        reader.waitForEnter(i18n.getMessage("prompt.press_enter"));
    }
}