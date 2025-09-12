package com.gmail.bobason01.lang;

import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LangManager {

    private static String defaultLang = "en";
    private static final Map<String, YamlConfiguration> langConfigs = new HashMap<>();
    private static final Map<UUID, String> userLang = new ConcurrentHashMap<>();

    public static void loadAll(File dataFolder) {
        langConfigs.clear();

        File configFile = new File(dataFolder, "config.yml");
        if (configFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            defaultLang = config.getString("default-language", "en");
        }

        File langFolder = new File(dataFolder, "lang");
        if (!langFolder.exists()) langFolder.mkdirs();

        File[] files = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String lang = file.getName().replace(".yml", "");
                langConfigs.put(lang, YamlConfiguration.loadConfiguration(file));
            }
        }

        if (!langConfigs.containsKey(defaultLang)) {
            defaultLang = "en";
        }
    }

    public static void loadUserLanguage(UUID uuid, String lang) {
        if (lang != null && !lang.isEmpty()) {
            userLang.put(uuid, lang);
        }
    }

    public static String get(UUID uuid, String key) {
        String lang = getLanguage(uuid);
        return get(lang, key);
    }

    public static String get(String lang, String key) {
        YamlConfiguration config = langConfigs.getOrDefault(lang, langConfigs.get(defaultLang));
        if (config == null) {
            config = langConfigs.get("en");
        }
        if (config == null) {
            return "§c[ERROR] Language files not found!";
        }
        return ChatColor.translateAlternateColorCodes('&', config.getString(key, "§cMissing: " + lang + "/" + key));
    }

    public static void setLanguage(UUID uuid, String lang) {
        if (lang != null && !langConfigs.containsKey(lang)) {
            return;
        }

        if (lang == null || lang.equalsIgnoreCase(defaultLang)) {
            userLang.remove(uuid);
            MailDataManager.getInstance().savePlayerLanguage(uuid, null);
        } else {
            userLang.put(uuid, lang);
            MailDataManager.getInstance().savePlayerLanguage(uuid, lang);
        }
    }

    public static String getLanguage(UUID uuid) {
        return userLang.getOrDefault(uuid, defaultLang);
    }

    public static Set<String> getAvailableLanguages() {
        return langConfigs.keySet();
    }
}