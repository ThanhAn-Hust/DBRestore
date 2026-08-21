package com.dbbackup.wizard;

import com.dbbackup.i18n.I18nService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MainMenuWizard {
    private final TestConnectionWizard testConnectionWizard;
    private final BackupWizard backupWizard;
    private final RestoreWizard restoreWizard;
    private final HistoryWizard historyWizard;
    private final ConfigWizard configWizard;
    private final I18nService i18n;

    public MainMenuWizard(TestConnectionWizard testConnectionWizard,
                          BackupWizard backupWizard,
                          RestoreWizard restoreWizard,
                          HistoryWizard historyWizard,
                          ConfigWizard configWizard,
                          I18nService i18n) {
        this.testConnectionWizard = testConnectionWizard;
        this.backupWizard = backupWizard;
        this.restoreWizard = restoreWizard;
        this.historyWizard = historyWizard;
        this.configWizard = configWizard;
        this.i18n = i18n;
    }

    public void start() {
        PromptReader reader = new PromptReader();
        while (true) {
            System.out.println();
            System.out.println(AnsiColor.bold(i18n.getMessage("app.title")));
            System.out.println(i18n.getMessage("app.welcome"));
            System.out.println();

            List<String> options = List.of(
                    i18n.getMessage("menu.option.test_connection"),
                    i18n.getMessage("menu.option.backup"),
                    i18n.getMessage("menu.option.restore"),
                    i18n.getMessage("menu.option.history"),
                    i18n.getMessage("menu.option.config"),
                    i18n.getMessage("menu.option.language"),
                    i18n.getMessage("menu.option.exit")
            );

            System.out.println(i18n.getMessage("menu.header"));
            for (int i = 0; i < options.size() - 1; i++) {
                System.out.println("  " + options.get(i));
            }
            System.out.println("  " + options.get(options.size() - 1));
            System.out.println();

            int choice = reader.readInt(i18n.getMessage("menu.prompt"), 0, 6, 1);

            switch (choice) {
                case 1 -> testConnectionWizard.run(reader);
                case 2 -> backupWizard.run(reader);
                case 3 -> restoreWizard.run(reader);
                case 4 -> historyWizard.run(reader);
                case 5 -> configWizard.run(reader);
                case 6 -> handleLanguageSwitch(reader);
                case 0 -> {
                    System.out.println("Goodbye! / Hẹn gặp lại!");
                    return;
                }
                default -> System.out.println(i18n.getMessage("menu.invalid_choice"));
            }
        }
    }

    private void handleLanguageSwitch(PromptReader reader) {
        reader.printInfo(i18n.getMessage("language.header"));
        int langChoice = reader.readInt(i18n.getMessage("language.prompt"), 1, 2, i18n.getCurrentLanguage().equals("vi") ? 2 : 1);
        String lang = (langChoice == 2) ? "vi" : "en";
        i18n.setLanguage(lang);
        reader.printSuccess(i18n.getMessage("language.saved", lang.equals("vi") ? "Tiếng Việt" : "English"));
        reader.waitForEnter(i18n.getMessage("prompt.press_enter"));
    }
}