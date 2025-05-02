package com.gmail.bobason01.utils;

import com.gmail.bobason01.MailManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;

public class LangUtil {

    private static YamlConfiguration lang;

    public static void load(MailManager plugin) {
        File langFile = new File(plugin.getDataFolder(), "lang.yml");

        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }

        lang = YamlConfiguration.loadConfiguration(langFile);
    }

    public static String get(UUID uuid, String key) {
        if (lang == null) return "[lang not loaded]";
        String value = lang.getString(key);
        return value != null ? value : "§c[Missing: " + key + "]";
    }

    public static String getOrDefault(String key, String def) {
        if (lang == null) return def;
        return lang.getString(key, def);
    }
}
