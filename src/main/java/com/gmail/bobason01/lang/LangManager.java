package com.gmail.bobason01.lang;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LangManager {

    private static String defaultLang = "ko"; // 기본값 (config에서 덮어쓰기 가능)
    private static final Map<String, YamlConfiguration> langConfigs = new HashMap<>();
    private static final Map<UUID, String> userLang = new ConcurrentHashMap<>();

    // 언어 파일 및 config에서 기본 언어 로드
    public static void loadAll(File dataFolder) {
        // config.yml에서 default-language 가져오기
        File configFile = new File(dataFolder, "config.yml");
        if (configFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String configLang = config.getString("default-language", "ko");

            // 해당 언어 파일이 존재하는 경우에만 적용
            File langFolder = new File(dataFolder, "lang");
            File langFile = new File(langFolder, configLang + ".yml");
            if (langFile.exists()) {
                defaultLang = configLang;
            }
        }

        // lang 폴더에서 언어 파일 로드
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

    // 특정 유저의 언어로 메시지 가져오기
    public static String get(UUID uuid, String key) {
        String lang = getLanguage(uuid);
        return get(lang, key);
    }

    // 특정 언어 코드로 메시지 가져오기
    public static String get(String lang, String key) {
        YamlConfiguration config = langConfigs.getOrDefault(lang, langConfigs.get(defaultLang));
        return ChatColor.translateAlternateColorCodes('&', config.getString(key, "§cMissing: " + key));
    }

    // 유저의 언어 설정
    public static void setLanguage(UUID uuid, String lang) {
        if (lang == null || lang.equalsIgnoreCase(defaultLang)) {
            userLang.remove(uuid);
        } else {
            userLang.put(uuid, lang);
        }
    }

    // 유저의 언어 코드 가져오기 (없으면 defaultLang 사용)
    public static String getLanguage(UUID uuid) {
        return userLang.getOrDefault(uuid, defaultLang);
    }

    // 사용 가능한 언어 목록 가져오기
    public static Set<String> getAvailableLanguages() {
        return langConfigs.keySet();
    }
}
