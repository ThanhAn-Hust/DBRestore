package com.dbbackup.i18n;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UserPreferences {
    private String language = "en";

    private static final String PREF_DIR = System.getProperty("user.home") + "/.db-backup";
    private static final String PREF_FILE = PREF_DIR + "/preferences.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public static UserPreferences load() {
        try {
            File file = new File(PREF_FILE);
            if (file.exists()) {
                return MAPPER.readValue(file, UserPreferences.class);
            }
        } catch (Exception ignored) {
        }
        return new UserPreferences();
    }

    public void save() {
        try {
            Path dir = Paths.get(PREF_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(PREF_FILE), this);
        } catch (IOException ignored) {
        }
    }
}