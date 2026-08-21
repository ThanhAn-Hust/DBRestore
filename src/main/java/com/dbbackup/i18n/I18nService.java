package com.dbbackup.i18n;

import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class I18nService {
    private String currentLanguage = "en";
    private final Map<String, Properties> languageBundles = new HashMap<>();

    public I18nService() {
        loadBundle("en", "messages.properties");
        loadBundle("vi", "messages_vi.properties");
        UserPreferences prefs = UserPreferences.load();
        setLanguage(prefs.getLanguage());
    }

    private void loadBundle(String lang, String fileName) {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
            }
        } catch (Exception ignored) {
        }
        languageBundles.put(lang, props);
    }

    public synchronized void setLanguage(String lang) {
        if (lang == null || (!lang.equalsIgnoreCase("vi") && !lang.equalsIgnoreCase("en"))) {
            lang = "en";
        }
        this.currentLanguage = lang.toLowerCase();
        UserPreferences prefs = UserPreferences.load();
        prefs.setLanguage(this.currentLanguage);
        prefs.save();
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public String getMessage(String key, Object... args) {
        Properties props = languageBundles.get(currentLanguage);
        if (props == null || !props.containsKey(key)) {
            props = languageBundles.get("en");
        }
        if (props == null || !props.containsKey(key)) {
            return key;
        }
        String pattern = props.getProperty(key);
        if (args != null && args.length > 0) {
            try {
                return MessageFormat.format(pattern, args);
            } catch (Exception e) {
                return pattern;
            }
        }
        return pattern;
    }
}