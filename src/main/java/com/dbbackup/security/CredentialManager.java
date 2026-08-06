package com.dbbackup.security;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CredentialManager {
    private static final Logger LOGGER = Logger.getLogger(CredentialManager.class.getName());
    private static final Set<Path> TRACKED_TEMP_FILES = ConcurrentHashMap.newKeySet();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Path path : TRACKED_TEMP_FILES) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to delete temp credential file on shutdown: " + path, e);
                }
            }
        }));
    }

    public interface TempCredentialHandle extends AutoCloseable {
        File getFile();
        Path getPath();
        @Override
        void close();
    }

    public static Path getTempDir() {
        String tempEnv = System.getenv("TEMP");
        Path baseTemp = (tempEnv != null && !tempEnv.isBlank())
                ? Path.of(tempEnv)
                : Path.of(System.getProperty("java.io.tmpdir"));
        return baseTemp.resolve(".db-backup");
    }

    public static Map<String, String> getEnvironmentCredentials(String dbType, String password) {
        return getEnvironmentCredentials(dbType, null, password);
    }

    public static Map<String, String> getEnvironmentCredentials(String dbType, String username, String password) {
        Map<String, String> env = new HashMap<>();
        if (dbType != null && password != null) {
            String lower = dbType.toLowerCase(Locale.ROOT);
            if (lower.contains("mysql") || lower.contains("maria")) {
                env.put("MYSQL_PWD", password);
            } else if (lower.contains("postgres") || lower.contains("pg")) {
                env.put("PGPASSWORD", password);
            } else if (lower.contains("mongo")) {
                env.put("MONGO_PASSWORD", password);
            }
        }
        return Collections.unmodifiableMap(env);
    }

    public static TempCredentialHandle createTempMyCnf(String user, String password) throws IOException {
        Path tempDir = getTempDir();
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        Path tempFile = Files.createTempFile(tempDir, "mysql-", ".cnf");
        TRACKED_TEMP_FILES.add(tempFile);

        String content = "[client]\nuser=" + user + "\npassword=" + password + "\n";
        Files.writeString(tempFile, content, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

        applyStrictPermissions(tempFile);

        return new TempCredentialHandle() {
            private boolean closed = false;

            @Override
            public File getFile() {
                return tempFile.toFile();
            }

            @Override
            public Path getPath() {
                return tempFile;
            }

            @Override
            public synchronized void close() {
                if (!closed) {
                    closed = true;
                    TRACKED_TEMP_FILES.remove(tempFile);
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to delete temp credential file on close: " + tempFile, e);
                    }
                }
            }
        };
    }

    private static void applyStrictPermissions(Path filePath) throws IOException {
        FileSystem fileSystem = filePath.getFileSystem();
        Set<String> views = fileSystem.supportedFileAttributeViews();

        if (views.contains("posix")) {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(filePath, perms);
        } else if (views.contains("acl")) {
            try {
                AclFileAttributeView aclView = Files.getFileAttributeView(filePath, AclFileAttributeView.class);
                if (aclView != null) {
                    UserPrincipal owner = Files.getOwner(filePath);
                    AclEntry entry = AclEntry.newBuilder()
                            .setType(AclEntryType.ALLOW)
                            .setPrincipal(owner)
                            .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                            .build();
                    aclView.setAcl(List.of(entry));
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not set ACL permissions on " + filePath + ": " + e.getMessage());
            }
        } else {
            File file = filePath.toFile();
            file.setReadable(false, false);
            file.setReadable(true, true);
            file.setWritable(false, false);
            file.setWritable(true, true);
            file.setExecutable(false, false);
        }
    }
}
