package com.gmail.bobason01.utils;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LangUtil {

    private static final Map<String, String> messages = new HashMap<>();

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
            loadStringsRecursively(config, "", config.getKeys(true));
        } else {
            plugin.getLogger().warning("Cannot find or create language file: " + resourcePath);
        }
    }

    private static void loadStringsRecursively(YamlConfiguration config, String prefix, Set<String> keys) {
        for (String key : keys) {
            if (config.isString(key)) {
                String raw = config.getString(key);
                if (raw != null) {
                    messages.put(key, ChatColor.translateAlternateColorCodes('&', raw));
                }
            }
        }
    }

    public static String get(String key) {
        return messages.getOrDefault(key, "§cMissing: " + key);
    }

    public static String get(String key, Map<String, String> placeholders) {
        String message = get(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return message;
    }
}
