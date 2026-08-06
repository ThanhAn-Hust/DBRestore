package com.dbbackup.audit;

import com.dbbackup.domain.model.BackupHistoryRecord;
import com.dbbackup.domain.model.BackupType;
import com.dbbackup.domain.model.DumpFormat;
import com.dbbackup.domain.port.AuditLogService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SqliteAuditLogService implements AuditLogService {

    private final String jdbcUrl;
    private Connection memoryConnection;

    public SqliteAuditLogService() {
        this(getDefaultJdbcUrl());
    }

    public SqliteAuditLogService(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        ensureParentDirExists(jdbcUrl);
        if (jdbcUrl.contains(":memory:")) {
            try {
                this.memoryConnection = DriverManager.getConnection(jdbcUrl);
                configurePragmas(this.memoryConnection);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to open in-memory SQLite connection", e);
            }
        }
    }

    private static String getDefaultJdbcUrl() {
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path auditDir = userHome.resolve(".db-backup");
        try {
            Files.createDirectories(auditDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create audit log directory: " + auditDir, e);
        }
        Path auditDbFile = auditDir.resolve("audit.db");
        return "jdbc:sqlite:" + auditDbFile.toAbsolutePath().toString().replace("\\", "/");
    }

    private static void ensureParentDirExists(String jdbcUrl) {
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:sqlite:") && !jdbcUrl.contains(":memory:")) {
            String pathStr = jdbcUrl.substring("jdbc:sqlite:".length());
            int queryIdx = pathStr.indexOf('?');
            if (queryIdx != -1) {
                pathStr = pathStr.substring(0, queryIdx);
            }
            if (!pathStr.isBlank()) {
                Path path = Paths.get(pathStr);
                Path parent = path.getParent();
                if (parent != null) {
                    try {
                        Files.createDirectories(parent);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private Connection getConnection() throws SQLException {
        if (jdbcUrl.contains(":memory:") && memoryConnection != null && !memoryConnection.isClosed()) {
            return memoryConnection;
        }
        Connection conn = DriverManager.getConnection(jdbcUrl);
        configurePragmas(conn);
        return conn;
    }

    private void configurePragmas(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA busy_timeout=5000;");
        }
    }

    private void closeIfNecessary(Connection conn) {
        if (!jdbcUrl.contains(":memory:") && conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    @Override
    public void initSchema() {
        String sql = """
            CREATE TABLE IF NOT EXISTS backup_history (
                id TEXT PRIMARY KEY,
                profile_name TEXT,
                backup_type TEXT,
                dump_format TEXT,
                parent_backup_id TEXT,
                backup_chain_id TEXT,
                target_tables TEXT,
                start_time TEXT,
                end_time TEXT,
                duration_ms INTEGER,
                file_size_bytes INTEGER,
                destination_uri TEXT,
                status TEXT,
                error_message TEXT
            );
            """;
        Connection conn = null;
        try {
            conn = getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite audit log schema", e);
        } finally {
            closeIfNecessary(conn);
        }
    }

    @Override
    public void recordCompletion(BackupHistoryRecord record) {
        String sql = """
            INSERT INTO backup_history (
                id, profile_name, backup_type, dump_format,
                parent_backup_id, backup_chain_id, target_tables,
                start_time, end_time, duration_ms, file_size_bytes,
                destination_uri, status, error_message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                profile_name = excluded.profile_name,
                backup_type = excluded.backup_type,
                dump_format = excluded.dump_format,
                parent_backup_id = excluded.parent_backup_id,
                backup_chain_id = excluded.backup_chain_id,
                target_tables = excluded.target_tables,
                start_time = excluded.start_time,
                end_time = excluded.end_time,
                duration_ms = excluded.duration_ms,
                file_size_bytes = excluded.file_size_bytes,
                destination_uri = excluded.destination_uri,
                status = excluded.status,
                error_message = excluded.error_message;
            """;
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, record.id());
                pstmt.setString(2, record.dbName());
                pstmt.setString(3, record.backupType() != null ? record.backupType().name() : null);
                pstmt.setString(4, record.dumpFormat() != null ? record.dumpFormat().name() : null);
                pstmt.setString(5, record.parentId());
                pstmt.setString(6, record.chainId());
                pstmt.setString(7, record.tables() != null && !record.tables().isEmpty() ? String.join(",", record.tables()) : "");
                pstmt.setString(8, record.startTime() != null ? record.startTime().toString() : null);
                pstmt.setString(9, record.endTime() != null ? record.endTime().toString() : null);
                if (record.durationMs() != null) {
                    pstmt.setLong(10, record.durationMs());
                } else {
                    pstmt.setNull(10, Types.INTEGER);
                }
                if (record.sizeBytes() != null) {
                    pstmt.setLong(11, record.sizeBytes());
                } else {
                    pstmt.setNull(11, Types.INTEGER);
                }
                pstmt.setString(12, record.storageUri());
                pstmt.setString(13, record.status());
                pstmt.setString(14, record.errorMessage());

                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record completion in SQLite audit log", e);
        } finally {
            closeIfNecessary(conn);
        }
    }

    @Override
    public List<BackupHistoryRecord> getHistory(int limit, String dbName) {
        boolean filterByName = dbName != null && !dbName.isBlank();
        int maxRows = limit > 0 ? limit : Integer.MAX_VALUE;

        String sql = filterByName ? """
            SELECT id, profile_name, backup_type, dump_format, parent_backup_id, backup_chain_id,
                   target_tables, start_time, end_time, duration_ms, file_size_bytes,
                   destination_uri, status, error_message
            FROM backup_history
            WHERE profile_name = ?
            ORDER BY start_time DESC
            LIMIT ?
            """ : """
            SELECT id, profile_name, backup_type, dump_format, parent_backup_id, backup_chain_id,
                   target_tables, start_time, end_time, duration_ms, file_size_bytes,
                   destination_uri, status, error_message
            FROM backup_history
            ORDER BY start_time DESC
            LIMIT ?
            """;

        List<BackupHistoryRecord> result = new ArrayList<>();
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                if (filterByName) {
                    pstmt.setString(1, dbName);
                    pstmt.setInt(2, maxRows);
                } else {
                    pstmt.setInt(1, maxRows);
                }
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapRecord(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query audit history from SQLite", e);
        } finally {
            closeIfNecessary(conn);
        }
        return result;
    }

    @Override
    public BackupHistoryRecord getRecordById(String id) {
        String sql = """
            SELECT id, profile_name, backup_type, dump_format, parent_backup_id, backup_chain_id,
                   target_tables, start_time, end_time, duration_ms, file_size_bytes,
                   destination_uri, status, error_message
            FROM backup_history
            WHERE id = ?
            """;
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapRecord(rs);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query audit record by ID from SQLite", e);
        } finally {
            closeIfNecessary(conn);
        }
        return null;
    }

    private BackupHistoryRecord mapRecord(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String dbName = rs.getString("profile_name");
        String bTypeStr = rs.getString("backup_type");
        BackupType backupType = bTypeStr != null ? BackupType.valueOf(bTypeStr) : null;
        String dFormatStr = rs.getString("dump_format");
        DumpFormat dumpFormat = dFormatStr != null ? DumpFormat.valueOf(dFormatStr) : null;
        String parentId = rs.getString("parent_backup_id");
        String chainId = rs.getString("backup_chain_id");
        String tablesStr = rs.getString("target_tables");
        List<String> tables = parseTables(tablesStr);

        String startTimeStr = rs.getString("start_time");
        LocalDateTime startTime = startTimeStr != null ? LocalDateTime.parse(startTimeStr) : null;
        String endTimeStr = rs.getString("end_time");
        LocalDateTime endTime = endTimeStr != null ? LocalDateTime.parse(endTimeStr) : null;

        Long durationMs = rs.getObject("duration_ms") != null ? rs.getLong("duration_ms") : null;
        Long sizeBytes = rs.getObject("file_size_bytes") != null ? rs.getLong("file_size_bytes") : null;
        String destinationUri = rs.getString("destination_uri");
        String status = rs.getString("status");
        String errorMessage = rs.getString("error_message");

        return new BackupHistoryRecord(
            id, dbName, backupType, dumpFormat, parentId, chainId,
            tables, startTime, endTime, durationMs, sizeBytes,
            destinationUri, status, errorMessage
        );
    }

    private List<String> parseTables(String str) {
        if (str == null || str.isBlank()) {
            return List.of();
        }
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
