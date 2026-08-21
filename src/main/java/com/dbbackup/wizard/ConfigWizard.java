package com.dbbackup.wizard;

import com.dbbackup.domain.model.DbConnectionConfig;
import com.dbbackup.i18n.I18nService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConfigWizard {
    private final I18nService i18n;

    public ConfigWizard(I18nService i18n) {
        this.i18n = i18n;
    }

    public void run(PromptReader reader) {
        reader.printInfo("=== [5] " + i18n.getMessage("menu.option.config") + " ===");
        Map<String, DbConnectionConfig> profiles = TestConnectionWizard.loadProfiles();

        System.out.println();
        System.out.println("Configured Database Profiles (~/.db-backup/config.yml):");
        if (profiles.isEmpty()) {
            System.out.println("  (No profiles configured yet)");
        } else {
            for (Map.Entry<String, DbConnectionConfig> entry : profiles.entrySet()) {
                DbConnectionConfig c = entry.getValue();
                System.out.println("  • " + AnsiColor.green(entry.getKey()) + ": " + c.dbType() + " -> " + c.host() + ":" + c.port() + " (DB: " + c.databaseName() + ", User: " + c.username() + ")");
            }
        }
        System.out.println();
        reader.printInfo("Tip: Edit ~/.db-backup/config.yml to add new database profiles or cloud credentials.");
        reader.waitForEnter(i18n.getMessage("prompt.press_enter"));
    }
}