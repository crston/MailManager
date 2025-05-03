package com.gmail.bobason01.utils;

import com.gmail.bobason01.MailManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 메일 수신 알림 설정을 파일로 관리하는 유틸 클래스
 */
public class MailNotifySettingsManager {

    private static final Set<UUID> notifyEnabledUsers = new HashSet<>();
    private static final File file = new File(MailManager.getInstance().getDataFolder(), "notify.yml");
    private static final Logger log = MailManager.getInstance().getLogger();

    public static void load() {
        notifyEnabledUsers.clear();

        if (!file.exists()) {
            log.info("[MailManager] notify.yml not found, skipping load.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int count = 0;

        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (config.getBoolean(key)) {
                    notifyEnabledUsers.add(uuid);
                    count++;
                }
            } catch (IllegalArgumentException ignored) {
                log.warning("[MailManager] Invalid UUID in notify.yml: " + key);
            }
        }

        log.info("[MailManager] notify.yml loaded (" + count + " users enabled)");
    }

    public static void save() {
        YamlConfiguration config = new YamlConfiguration();

        for (UUID uuid : notifyEnabledUsers) {
            config.set(uuid.toString(), true);
        }

        try {
            config.save(file);
            log.info("[MailManager] notify.yml saved.");
        } catch (IOException e) {
            log.severe("[MailManager] Failed to save notify.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean isNotifyEnabled(UUID uuid) {
        return notifyEnabledUsers.contains(uuid);
    }

    public static void toggle(UUID uuid) {
        if (notifyEnabledUsers.contains(uuid)) {
            notifyEnabledUsers.remove(uuid);
        } else {
            notifyEnabledUsers.add(uuid);
        }
    }

    public static void setNotify(UUID uuid, boolean enabled) {
        if (enabled) {
            notifyEnabledUsers.add(uuid);
        } else {
            notifyEnabledUsers.remove(uuid);
        }
    }
}
