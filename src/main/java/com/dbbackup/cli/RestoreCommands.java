package com.dbbackup.cli;

import com.dbbackup.config.ProfileConfigResolver;
import com.dbbackup.domain.model.DbConnectionConfig;
import com.dbbackup.service.RestoreOrchestrator;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@Command(command = "restore", group = "Restore Commands")
public class RestoreCommands {

    private final RestoreOrchestrator restoreOrchestrator;

    public RestoreCommands() {
        this(new RestoreOrchestrator());
    }

    public RestoreCommands(RestoreOrchestrator restoreOrchestrator) {
        this.restoreOrchestrator = restoreOrchestrator != null ? restoreOrchestrator : new RestoreOrchestrator();
    }

    @ShellMethod(key = "restore", value = "Trigger database restore")
    @Command(command = "restore", description = "Trigger database restore")
    public String restore(
        @ShellOption(value = {"--profile", "-p"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "profile", shortNames = 'p', defaultValue = "") String profile,

        @ShellOption(value = {"--backup-id", "-b"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "backup-id", shortNames = 'b', defaultValue = "") String backupId,

        @ShellOption(value = {"--passphrase", "-w"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "passphrase", shortNames = 'w', defaultValue = "") String passphrase,

        @ShellOption(value = {"--tables"}, defaultValue = ShellOption.NULL)
        @Option(longNames = "tables", defaultValue = "") String tables,

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
        if (backupId == null || backupId.isBlank()) {
            return "FAILED: --backup-id is required for restore command.";
        }
        try {
            DbConnectionConfig conn = null;
            if ((profile != null && !profile.isBlank()) || (host != null && !host.isBlank())) {
                DbConnectionConfig pConn = ProfileConfigResolver.resolveProfile(profile);
                String fType = (dbType != null && !dbType.isBlank()) ? dbType : (pConn != null ? pConn.dbType() : "mysql");
                String fHost = (host != null && !host.isBlank()) ? host : (pConn != null ? pConn.host() : "localhost");
                int targetPort = port > 0 ? port : (pConn != null ? pConn.port() : (fType.equalsIgnoreCase("postgresql") || fType.equalsIgnoreCase("postgres") ? 5432 : 3306));
                String fUser = (username != null && !username.isBlank()) ? username : (pConn != null ? pConn.username() : "root");
                String fPass = (password != null && !password.isBlank()) ? password : (pConn != null ? pConn.password() : "");
                String fDb = (database != null && !database.isBlank()) ? database : (pConn != null ? pConn.databaseName() : "mydb");
                conn = new DbConnectionConfig(fType, fHost, targetPort, fUser, fPass, fDb);
            }

            restoreOrchestrator.restoreChain(backupId, passphrase, conn);
            return "SUCCESS: Restore completed for backup ID '" + backupId + "'";
        } catch (Exception e) {
            return "Restore FAILED: " + e.getMessage();
        }
    }
}
