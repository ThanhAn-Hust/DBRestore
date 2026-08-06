package com.dbbackup.audit;

import com.dbbackup.domain.model.StateRecord;
import com.dbbackup.domain.port.StateTracker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class JsonStateTracker implements StateTracker {

    private final Path baseDir;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonStateTracker() {
        this(Paths.get(System.getProperty("user.home"), ".db-backup", "state"));
    }

    public JsonStateTracker(Path baseDir) {
        this.baseDir = baseDir;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public StateRecord getState(String dbName) {
        if (dbName == null || dbName.isBlank()) {
            return null;
        }
        Path file = getFilePath(dbName);
        lock.readLock().lock();
        try {
            if (!Files.exists(file)) {
                return null;
            }
            return objectMapper.readValue(file.toFile(), StateRecord.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read state record for profile: " + dbName, e);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void saveState(String dbName, StateRecord state) {
        if (dbName == null || dbName.isBlank()) {
            throw new IllegalArgumentException("Profile/dbName cannot be null or blank");
        }
        Path file = getFilePath(dbName);
        lock.writeLock().lock();
        try {
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, sanitizeFileName(dbName), ".tmp");
            objectMapper.writeValue(tempFile.toFile(), state);
            try {
                Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save state record for profile: " + dbName, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Path getFilePath(String dbName) {
        return baseDir.resolve(sanitizeFileName(dbName) + ".json");
    }

    private String sanitizeFileName(String dbName) {
        return dbName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
