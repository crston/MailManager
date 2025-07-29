package com.gmail.bobason01.lang;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LangManager {

    private static final String DEFAULT_LANG = "en";
    private static final Map<String, YamlConfiguration> langConfigs = new HashMap<>();
    private static final Map<UUID, String> userLang = new ConcurrentHashMap<>();

    // 언어 파일 모두 로드
    public static void loadAll(File dataFolder) {
        File langFolder = new File(dataFolder, "lang");
        if (!langFolder.exists()) langFolder.mkdirs();

        File[] files = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String lang = file.getName().replace(".yml", "");
                langConfigs.put(lang, YamlConfiguration.loadConfiguration(file));
            }
        }
    }

    // 특정 유저 언어로 메시지 가져오기
    public static String get(UUID uuid, String key) {
        String lang = getLanguage(uuid);
        return get(lang, key);
    }

    // 특정 언어코드로 메시지 가져오기
    public static String get(String lang, String key) {
        YamlConfiguration config = langConfigs.getOrDefault(lang, langConfigs.get(DEFAULT_LANG));
        return ChatColor.translateAlternateColorCodes('&', config.getString(key, "§cMissing: " + key));
    }

    // 언어 설정
    public static void setLanguage(UUID uuid, String lang) {
        if (lang == null || lang.equalsIgnoreCase(DEFAULT_LANG)) {
            userLang.remove(uuid);
        } else {
            userLang.put(uuid, lang);
        }
    }

    // 유저의 언어코드 가져오기(없으면 en)
    public static String getLanguage(UUID uuid) {
        return userLang.getOrDefault(uuid, DEFAULT_LANG);
    }

    // 사용 가능한 언어 목록
    public static Set<String> getAvailableLanguages() {
        return langConfigs.keySet();
    }
}
