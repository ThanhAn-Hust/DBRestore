package com.dbbackup.config;

import com.dbbackup.domain.model.DbConnectionConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class ProfileConfigResolver {

    public static DbConnectionConfig resolveProfile(String profileName) {
        return resolveProfile(profileName, getDefaultConfigPath());
    }

    public static Path getDefaultConfigPath() {
        return Paths.get(System.getProperty("user.home"), ".db-backup", "config.yml");
    }

    @SuppressWarnings("unchecked")
    public static DbConnectionConfig resolveProfile(String profileName, Path configPath) {
        if (profileName == null || profileName.isBlank()) {
            return null;
        }
        if (configPath == null || !Files.exists(configPath)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(in);
            if (data == null || !data.containsKey("profiles")) {
                return null;
            }
            Map<String, Object> profiles = (Map<String, Object>) data.get("profiles");
            if (profiles == null || !profiles.containsKey(profileName)) {
                return null;
            }
            Map<String, Object> profileData = (Map<String, Object>) profiles.get(profileName);
            if (profileData == null) {
                return null;
            }

            String dbType = resolveEnvVars((String) profileData.getOrDefault("db-type", profileData.get("dbType")));
            if (dbType == null) dbType = "mysql";
            String host = resolveEnvVars((String) profileData.getOrDefault("host", "localhost"));
            Object portObj = profileData.getOrDefault("port", 3306);
            int port = portObj instanceof Number n ? n.intValue() : Integer.parseInt(portObj.toString());
            String username = resolveEnvVars((String) profileData.get("username"));
            String password = resolveEnvVars((String) profileData.get("password"));
            String database = resolveEnvVars((String) profileData.getOrDefault("database", profileData.get("databaseName")));

            return new DbConnectionConfig(dbType, host, port, username, password, database);
        } catch (Exception e) {
            return null;
        }
    }

    public static String resolveEnvVars(String val) {
        if (val == null) return null;
        if (val.startsWith("${") && val.endsWith("}")) {
            String inner = val.substring(2, val.length() - 1);
            String defaultValue = null;
            if (inner.contains(":")) {
                String[] parts = inner.split(":", 2);
                inner = parts[0];
                defaultValue = parts[1];
            }
            String env = System.getenv(inner);
            if (env != null && !env.isEmpty()) {
                return env;
            }
            String sysProp = System.getProperty(inner);
            if (sysProp != null && !sysProp.isEmpty()) {
                return sysProp;
            }
            return defaultValue != null ? defaultValue : val;
        }
        return val;
    }
}
