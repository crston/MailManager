package com.gmail.bobason01.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LangUtil {

    private static final Map<String, String> messages = new HashMap<>();

    /**
     * Load language file based on config (e.g., lang/en_us.yml)
     */
    public static void load(Plugin plugin) {
        messages.clear();

        String langCode = plugin.getConfig().getString("language", "en_us").toLowerCase();
        String resourcePath = "lang/" + langCode + ".yml";
        File langFile = new File(plugin.getDataFolder(), resourcePath);

        if (!langFile.exists()) {
            plugin.saveResource(resourcePath, false);
        }

        if (langFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
            Set<String> keys = config.getKeys(true);
            for (String key : keys) {
                if (config.isString(key)) {
                    String value = config.getString(key);
                    if (value != null) {
                        messages.put(key, value);
                    }
                }
            }
        } else {
            plugin.getLogger().warning("Cannot find or create language file: " + resourcePath);
        }
    }

    /**
     * Get message by key. If not found, return key in red.
     */
    public static String get(String key) {
        return messages.getOrDefault(key, "§c" + key);
    }

    /**
     * Get message and apply placeholders.
     * Usage: get("mail.sent", Map.of("player", "Steve"))
     */
    public static String get(String key, Map<String, String> placeholders) {
        String message = get(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }
}

