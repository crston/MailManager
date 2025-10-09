package com.gmail.bobason01.lang;

import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class LangManager {

    private static String defaultLang = "en_us";
    private static final Map<String, YamlConfiguration> langConfigs = new HashMap<>();
    private static final Map<UUID, String> userLang = new ConcurrentHashMap<>();

    public static void loadAll(File dataFolder) {
        langConfigs.clear();

        File configFile = new File(dataFolder, "config.yml");
        if (configFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            defaultLang = config.getString("default-language", "en_us");
        }

        File langFolder = new File(dataFolder, "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        File[] files = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String lang = file.getName().replace(".yml", "");
                YamlConfiguration config = new YamlConfiguration();
                try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
                    config.load(reader);
                    langConfigs.put(lang, config);
                } catch (IOException | InvalidConfigurationException e) {
                    Bukkit.getLogger().log(Level.WARNING, "[MailManager] Failed to load language file: " + file.getName(), e);
                }
            }
        }

        if (!langConfigs.containsKey(defaultLang)) {
            defaultLang = "en_us";
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
        YamlConfiguration config = langConfigs.get(lang);
        if (config == null) {
            config = langConfigs.get(defaultLang);
        }
        if (config == null) {
            config = langConfigs.get("en_us");
        }
        if (config == null) {
            return "§c[ERROR] Language files not found!";
        }

        String message = config.getString(key, "§cMissing: " + lang + "/" + key);
        return ChatColor.translateAlternateColorCodes('&', message);
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