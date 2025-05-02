package com.gmail.bobason01.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LangUtil {

    private static final Map<String, String> messages = new HashMap<>();

    public static void load(Plugin plugin) {
        messages.clear();

        String langCode = plugin.getConfig().getString("language", "en_us").toLowerCase();
        String resourcePath = "lang/" + langCode + ".yml";
        File langFile = new File(plugin.getDataFolder(), resourcePath);

        // JAR 내부 리소스를 외부로 복사
        if (!langFile.exists()) {
            plugin.saveResource(resourcePath, false);
        }

        // 파일 존재 시 로드
        if (langFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
            for (String key : config.getKeys(true)) {
                if (config.isString(key)) {
                    messages.put(key, config.getString(key));
                }
            }
        } else {
            plugin.getLogger().warning("Cannot find or create language file: " + resourcePath);
        }
    }

    public static String get(String key) {
        return messages.getOrDefault(key, "§c" + key);
    }
}
