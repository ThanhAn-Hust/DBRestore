package com.dbbackup.security;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class StartupCleanupSweep {
    private static final Logger LOGGER = Logger.getLogger(StartupCleanupSweep.class.getName());
    private static final Duration DEFAULT_EXPIRATION = Duration.ofMinutes(15);

    public static int performSweep() {
        return performSweep(CredentialManager.getTempDir(), DEFAULT_EXPIRATION);
    }

    public static int performSweep(Path tempDir, Duration maxAge) {
        if (!Files.exists(tempDir) || !Files.isDirectory(tempDir)) {
            return 0;
        }

        int deletedCount = 0;
        Instant now = Instant.now();

        try (Stream<Path> stream = Files.list(tempDir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(file)) {
                    try {
                        Instant lastModified = Files.getLastModifiedTime(file).toInstant();
                        if (Duration.between(lastModified, now).compareTo(maxAge) > 0) {
                            Files.deleteIfExists(file);
                            deletedCount++;
                            LOGGER.info("Swept stale temp credential file: " + file);
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to process or delete temp file during sweep: " + file, e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to list temp directory during cleanup sweep: " + tempDir, e);
        }

        return deletedCount;
    }
}
