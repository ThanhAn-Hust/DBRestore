package com.dbbackup.cli;

import com.dbbackup.config.ProfileConfigResolver;
import com.dbbackup.domain.model.*;
import com.dbbackup.domain.port.DbClientEngine;
import com.dbbackup.engine.MySqlEngine;
import com.dbbackup.engine.PostgresEngine;
import com.dbbackup.service.BackupOrchestrator;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.*;
import java.util.stream.Collectors;

@ShellComponent
@Command(command = "backup", group = "Backup Commands")
public class BackupCommands {

    private final BackupOrchestrator backupOrchestrator;
    private final List<DbClientEngine> engines;

    public BackupCommands() {
        this(new BackupOrchestrator(), List.of(new MySqlEngine(), new PostgresEngine()));
    }

    public BackupCommands(BackupOrchestrator backupOrchestrator) {
        this(backupOrchestrator, List.of(new MySqlEngine(), new PostgresEngine()));
    }

    public BackupCommands(BackupOrchestrator backupOrchestrator, List<DbClientEngine> engines) {
        this.backupOrchestrator = backupOrchestrator != null ? backupOrchestrator : new BackupOrchestrator();
        this.engines = engines != null ? engines : List.of(new MySqlEngine(), new PostgresEngine());
    }

    @ShellMethod(key = "test-connection", value = "Test database connection")
    @Command(command = "test-connection", description = "Test database connection")
    public String testConnection(
        @ShellOption(value = {"--profile", "-p"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "profile", shortNames = 'p', defaultValue = "") String profile,

        @ShellOption(value = {"--db-type", "-t"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "db-type", shortNames = 't', defaultValue = "") String dbType,

        @ShellOption(value = {"--host", "-h"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "host", shortNames = 'h', defaultValue = "") String host,

        @ShellOption(value = {"--port", "-P"}, defaultValue = "0")
        @Option(longNames = "port", shortNames = 'P', defaultValue = "0") int port,

        @ShellOption(value = {"--username", "-u"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "username", shortNames = 'u', defaultValue = "") String username,

        @ShellOption(value = {"--password", "-w"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "password", shortNames = 'w', defaultValue = "") String password,

        @ShellOption(value = {"--database", "-d"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "database", shortNames = 'd', defaultValue = "") String database
    ) {
        DbConnectionConfig conn = resolveConnectionConfig(profile, dbType, host, port, username, password, database);
        DbClientEngine engine = findEngine(conn.dbType());
        if (engine == null) {
            return "FAILED: Unsupported database type '" + conn.dbType() + "'";
        }
        try {
            ProcessBuilder pb = engine.testConnection(conn);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return "SUCCESS: Connection test passed for " + conn.dbType() + " at " + conn.host() + ":" + conn.port();
            } else {
                String err = "";
                try (var errIn = process.getErrorStream()) {
                    err = new String(errIn.readAllBytes());
                }
                return "FAILED: Connection test failed with exit code " + exitCode + (err.isBlank() ? "" : ": " + err);
            }
        } catch (Exception e) {
            return "FAILED: Connection test error: " + e.getMessage();
        }
    }

    @ShellMethod(key = "backup", value = "Trigger database backup")
    @Command(command = "backup", description = "Trigger database backup")
    public String backup(
        @ShellOption(value = {"--profile", "-p"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "profile", shortNames = 'p', defaultValue = "") String profile,

        @ShellOption(value = {"--type"}, defaultValue = "FULL")
        @Option(longNames = "type", defaultValue = "FULL") String type,

        @ShellOption(value = {"--tables"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "tables", defaultValue = "") String tables,

        @ShellOption(value = {"--encrypt"}, defaultValue = "false")
        @Option(longNames = "encrypt", defaultValue = "false") boolean encrypt,

        @ShellOption(value = {"--passphrase"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "passphrase", defaultValue = "") String passphrase,

        @ShellOption(value = {"--output"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "output", defaultValue = "") String output,

        @ShellOption(value = {"--notify"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "notify", defaultValue = "") String notify,

        @ShellOption(value = {"--db-type"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "db-type", defaultValue = "") String dbType,

        @ShellOption(value = {"--host"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "host", defaultValue = "") String host,

        @ShellOption(value = {"--port"}, defaultValue = "0")
        @Option(longNames = "port", defaultValue = "0") int port,

        @ShellOption(value = {"--username"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "username", defaultValue = "") String username,

        @ShellOption(value = {"--password"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "password", defaultValue = "") String password,

        @ShellOption(value = {"--database"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "database", defaultValue = "") String database
    ) {
        try {
            DbConnectionConfig conn = resolveConnectionConfig(profile, dbType, host, port, username, password, database);
            BackupType backupType;
            try {
                backupType = BackupType.valueOf(type.toUpperCase());
            } catch (Exception e) {
                backupType = BackupType.FULL;
            }

            List<String> tableList = parseTables(tables);
            String storageUri = (output != null && !output.isBlank()) ? output : "file:///backups/" + conn.databaseName() + ".sql.gz";

            List<String> notificationChannels = (notify != null && !notify.isBlank())
                    ? Arrays.stream(notify.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList())
                    : null;

            BackupConfig config = new BackupConfig(
                conn,
                backupType,
                DumpFormat.PLAIN_SQL,
                tableList,
                true,
                encrypt,
                passphrase,
                storageUri,
                null,
                null,
                Map.of("single-transaction", true)
            );

            BackupHistoryRecord record = backupOrchestrator.executeBackup(config);
            return String.format(
                "Backup %s! ID: %s | Profile: %s | Type: %s | Size: %d bytes | Uri: %s",
                record.status(), record.id(), record.dbName(), record.backupType(), record.sizeBytes(), record.storageUri()
            );
        } catch (Exception e) {
            return "Backup FAILED: " + e.getMessage();
        }
    }

    private DbConnectionConfig resolveConnectionConfig(
        String profile, String dbType, String host, int port, String username, String password, String database
    ) {
        DbConnectionConfig pConn = ProfileConfigResolver.resolveProfile(profile);

        String fType = (dbType != null && !dbType.isBlank()) ? dbType : (pConn != null ? pConn.dbType() : "mysql");
        String fHost = (host != null && !host.isBlank()) ? host : (pConn != null ? pConn.host() : "localhost");
        int fPort = port > 0 ? port : (pConn != null ? pConn.port() : (fType.equalsIgnoreCase("postgresql") || fType.equalsIgnoreCase("postgres") ? 5432 : 3306));
        String fUser = (username != null && !username.isBlank()) ? username : (pConn != null ? pConn.username() : "root");
        String fPass = (password != null && !password.isBlank()) ? password : (pConn != null ? pConn.password() : "");
        String fDb = (database != null && !database.isBlank()) ? database : (pConn != null ? pConn.databaseName() : "mydb");

        return new DbConnectionConfig(fType, fHost, fPort, fUser, fPass, fDb);
    }

    private DbClientEngine findEngine(String dbType) {
        if (dbType == null) return null;
        for (DbClientEngine e : engines) {
            if (e.supports(dbType)) return e;
        }
        return null;
    }

    private List<String> parseTables(String tablesStr) {
        if (tablesStr == null || tablesStr.isBlank()) return List.of();
        return Arrays.stream(tablesStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
