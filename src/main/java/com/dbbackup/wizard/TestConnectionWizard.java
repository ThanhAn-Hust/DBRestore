package com.dbbackup.wizard;

import com.dbbackup.config.ProfileConfigResolver;
import com.dbbackup.domain.model.DbConnectionConfig;
import com.dbbackup.engine.MySqlEngine;
import com.dbbackup.engine.PostgresEngine;
import com.dbbackup.i18n.I18nService;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

@Component
public class TestConnectionWizard {
    private static final String DB_POSTGRESQL = "postgresql";
    private static final String DB_MYSQL = "mysql";

    private final MySqlEngine mySqlEngine = new MySqlEngine();
    private final PostgresEngine postgresEngine = new PostgresEngine();
    private final I18nService i18n;

    public TestConnectionWizard(I18nService i18n) {
        this.i18n = i18n;
    }

    public void run(PromptReader reader) {
        reader.printInfo(i18n.getMessage("test_conn.header"));
        Map<String, DbConnectionConfig> profiles = loadProfiles();
        List<String> profileNames = new ArrayList<>(profiles.keySet());

        DbConnectionConfig config;
        if (!profileNames.isEmpty()) {
            List<String> options = new ArrayList<>();
            for (String p : profileNames) {
                DbConnectionConfig c = profiles.get(p);
                options.add(p + " (" + c.dbType() + " -> " + c.host() + ":" + c.port() + "/" + c.databaseName() + ")");
            }
            options.add(i18n.getMessage("prompt.choose_profile_custom"));
            int choice = reader.readChoice(i18n.getMessage("prompt.choose_profile"), options, 1);
            if (choice < profileNames.size()) {
                config = profiles.get(profileNames.get(choice));
            } else {
                config = promptCustomConfig(reader);
            }
        } else {
            config = promptCustomConfig(reader);
        }

        reader.printInfo(i18n.getMessage("test_conn.testing", config.host(), String.valueOf(config.port()), config.databaseName()));
        try {
            ProcessBuilder pb = DB_POSTGRESQL.equalsIgnoreCase(config.dbType()) || "postgres".equalsIgnoreCase(config.dbType())
                    ? postgresEngine.testConnection(config)
                    : mySqlEngine.testConnection(config);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                reader.printSuccess(i18n.getMessage("test_conn.success", config.databaseName(), config.host(), String.valueOf(config.port()), config.dbType()));
            } else {
                reader.printError(i18n.getMessage("test_conn.failed", config.databaseName(), "Process returned exit code " + exitCode));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            reader.printError(i18n.getMessage("test_conn.failed", config.databaseName(), "Interrupted: " + ie.getMessage()));
        } catch (Exception e) {
            reader.printError(i18n.getMessage("test_conn.failed", config.databaseName(), e.getMessage()));
        }
        reader.waitForEnter(i18n.getMessage("prompt.press_enter"));
    }

    private DbConnectionConfig promptCustomConfig(PromptReader reader) {
        int dbChoice = reader.readInt(i18n.getMessage("prompt.db_type"), 1, 2, 1);
        String type = (dbChoice == 2) ? DB_POSTGRESQL : DB_MYSQL;
        int defaultPort = DB_POSTGRESQL.equals(type) ? 5432 : 3306;

        String host = reader.readString(i18n.getMessage("prompt.host"), "127.0.0.1");
        int port = reader.readInt(i18n.getMessage("prompt.port"), 1, 65535, defaultPort);
        String database = reader.readString(i18n.getMessage("prompt.database"), "testdb");
        String username = reader.readString(i18n.getMessage("prompt.username"), DB_POSTGRESQL.equals(type) ? "postgres" : "root");
        String password = reader.readPassword(i18n.getMessage("prompt.password"));

        return new DbConnectionConfig(type, host, port, username, password, database);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, DbConnectionConfig> loadProfiles() {
        Map<String, DbConnectionConfig> result = new HashMap<>();
        Path configPath = ProfileConfigResolver.getDefaultConfigPath();
        if (!Files.exists(configPath)) {
            return result;
        }
        try (var in = Files.newInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(in);
            if (data != null && data.containsKey("profiles")) {
                Map<String, Object> profiles = (Map<String, Object>) data.get("profiles");
                for (String name : profiles.keySet()) {
                    DbConnectionConfig conn = ProfileConfigResolver.resolveProfile(name, configPath);
                    if (conn != null) {
                        result.put(name, conn);
                    }
                }
            }
        } catch (Exception ignored) {
            // Return empty or partial map on parse exception
        }
        return result;
    }
}