# Interactive CLI Wizard & Bilingual (EN/VI) UX Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an interactive terminal Wizard (menu-driven prompt system) for `db-backup` that activates when running without CLI arguments, supporting English and Vietnamese (default English, persisted in user preferences), with step-by-step interactive flows for Test Connection, Backup (with dynamic Cloud S3/Azure/GCS credentials prompting), Restore (with numbered audit log selection), History viewing, and Configuration Management.

**Architecture:**
- **I18n Localization Layer**: `I18nService` managing ResourceBundles for English (`en`) and Vietnamese (`vi`), with preference persistence in `~/.db-backup/preferences.json` or `config.yml`.
- **Interactive Prompt & Terminal Engine**: `InteractivePromptService` wrapping standard console / `Scanner` with masked password inputs, default fallbacks `[default]`, validation loops, and formatted ASCII banner/menus.
- **Wizard Controllers**:
  - `MainMenuWizard`: Main dispatch loop [1-6, 0].
  - `TestConnectionWizard`: Prompt host, port, db-type, credentials.
  - `BackupWizard`: Prompt database source, target tables, storage sink (Local/S3/Azure/GCS) with dynamic credential collection, AES-256 encryption, and webhook notification selection.
  - `RestoreWizard`: Displays numbered active backup chains from `AuditLogService` for 1-click selection, target DB selection, and safety confirmation.
  - `HistoryWizard`: Formatted paginated tabular view of SQLite audit records.
  - `ConfigWizard`: Manage/save profiles and cloud credentials.
  - `LanguageWizard`: Toggle English / Tiếng Việt and persist preference.
- **Entrypoint Integration**: `DbBackupApplication` / `InteractiveRunner` checks command-line arguments: if no non-option arguments provided, launch the Interactive Wizard. If CLI arguments passed (e.g. `backup --profile ...`), execute standard CLI command.

**Tech Stack:** Java 21 LTS, Spring Boot 3.3, Spring Shell, Jackson / YAML, JLine / Java Console.

## Global Constraints

- Never break existing non-interactive CLI flags (`backup`, `restore`, `daemon start`, `test-connection`, `history`).
- Passwords entered in interactive prompts must be masked where supported (`System.console().readPassword()`).
- Default language is English (`en`), with Vietnamese (`vi`) fully supported.
- Staged commits on branch `develop`. Zero test commits in git (`src/test/` untracked per repository constraint).

---

### Task 1: Internationalization (I18n) Engine & User Preference Manager

**Files:**
- Create: `src/main/resources/messages.properties` (English default)
- Create: `src/main/resources/messages_vi.properties` (Vietnamese)
- Create: `src/main/java/com/dbbackup/i18n/I18nService.java`
- Create: `src/main/java/com/dbbackup/i18n/UserPreferences.java`
- Create: `src/test/java/com/dbbackup/i18n/I18nServiceTest.java`

**Interfaces:**
- Produces: `I18nService.getMessage(key, args...)`, `I18nService.setLanguage(lang)`, `I18nService.getCurrentLanguage()`

- [ ] **Step 1: Create `messages.properties` and `messages_vi.properties` with UI strings for all menus and prompts**
- [ ] **Step 2: Create `UserPreferences` to load/save `language: "en"|"vi"` to `~/.db-backup/preferences.json`**
- [ ] **Step 3: Create `I18nService` Spring component managing `ResourceBundle` and language switching**
- [ ] **Step 4: Write unit test `I18nServiceTest` verifying language switching and fallback**
- [ ] **Step 5: Verify tests pass (`mvn test -Dtest=I18nServiceTest`) and commit `src/main/` & `src/main/resources/`**

---

### Task 2: Interactive Prompt Engine & Console Utilities

**Files:**
- Create: `src/main/java/com/dbbackup/wizard/PromptReader.java`
- Create: `src/main/java/com/dbbackup/wizard/AnsiColor.java`
- Create: `src/test/java/com/dbbackup/wizard/PromptReaderTest.java`

**Interfaces:**
- Produces: 
  - `PromptReader.readString(prompt, defaultValue)`
  - `PromptReader.readPassword(prompt)`
  - `PromptReader.readInt(prompt, min, max, defaultValue)`
  - `PromptReader.readBoolean(prompt, defaultYes)`
  - `PromptReader.readChoice(prompt, options, defaultIndex)`

- [ ] **Step 1: Create `AnsiColor` utility for terminal colors (Green, Yellow, Red, Cyan, Bold)**
- [ ] **Step 2: Create `PromptReader` handling console input, default value display, masked password input, and stream redirection**
- [ ] **Step 3: Write unit tests in `PromptReaderTest` with simulated `ByteArrayInputStream`**
- [ ] **Step 4: Run tests (`mvn test -Dtest=PromptReaderTest`) and commit `src/main/`**

---

### Task 3: Wizard Implementations (Test Connection, Backup, Restore, History, Config)

**Files:**
- Create: `src/main/java/com/dbbackup/wizard/TestConnectionWizard.java`
- Create: `src/main/java/com/dbbackup/wizard/BackupWizard.java`
- Create: `src/main/java/com/dbbackup/wizard/RestoreWizard.java`
- Create: `src/main/java/com/dbbackup/wizard/HistoryWizard.java`
- Create: `src/main/java/com/dbbackup/wizard/ConfigWizard.java`
- Create: `src/main/java/com/dbbackup/wizard/MainMenuWizard.java`
- Create: `src/test/java/com/dbbackup/wizard/WizardFlowTest.java`

**Interfaces:**
- Consumes: `BackupOrchestrator`, `RestoreOrchestrator`, `AuditLogService`, `I18nService`, `PromptReader`
- Produces: Interactive step-by-step navigation flows.

- [ ] **Step 1: Implement `TestConnectionWizard` (test DB parameters or existing profile)**
- [ ] **Step 2: Implement `BackupWizard` (source DB selection, table selection, storage destination with AWS S3/Azure/GCP credentials prompt, AES-256 encryption passphrase, notification hooks, and execution summary)**
- [ ] **Step 3: Implement `RestoreWizard` (queries SQLite audit log, lists numbered backup points, decodes AES passphrase, target DB selection, safety confirmation prompt)**
- [ ] **Step 4: Implement `HistoryWizard` & `ConfigWizard` (paginated audit trail table, profile creation/editor)**
- [ ] **Step 5: Implement `MainMenuWizard` with welcome banner, main menu options [1-6, 0], and loop execution**
- [ ] **Step 6: Write unit tests in `WizardFlowTest` verifying wizard routing and execution**
- [ ] **Step 7: Run tests (`mvn test -Dtest=WizardFlowTest`) and commit `src/main/`**

---

### Task 4: Application Runner Hook, Verification & Documentation Update

**Files:**
- Modify: `src/main/java/com/dbbackup/DbBackupApplication.java`
- Modify: `src/main/java/com/dbbackup/cli/BackupCommands.java`
- Update: `README.md`
- Update: `docs/USER_GUIDE.md`
- Create: `docs/update_ux_cli/WIZARD_GUIDE.md`

**Interfaces:**
- Launches `MainMenuWizard` when no command arguments provided, or executes Spring Shell commands when CLI flags passed.

- [ ] **Step 1: Update `DbBackupApplication.java` with `CommandLineRunner` to trigger `MainMenuWizard.start()` if `args.length == 0`**
- [ ] **Step 2: Add `interactive` / `wizard` command to Spring Shell in `BackupCommands.java`**
- [ ] **Step 3: Run full Maven test suite `mvn clean test` (ensure 100% pass across all tests)**
- [ ] **Step 4: Package JAR `mvn clean package -DskipTests` and test interactive wizard launch**
- [ ] **Step 5: Write `docs/update_ux_cli/WIZARD_GUIDE.md` with complete ASCII walkthroughs, screenshots descriptions, and language switching guide**
- [ ] **Step 6: Update `README.md` and `docs/USER_GUIDE.md` to highlight Interactive Wizard mode**
- [ ] **Step 7: Commit all changes on branch `develop`**
