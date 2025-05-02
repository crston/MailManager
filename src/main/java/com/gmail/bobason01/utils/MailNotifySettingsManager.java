package com.gmail.bobason01.utils;

import com.gmail.bobason01.MailManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MailNotifySettingsManager {

    private static final Set<UUID> enabledNotify = new HashSet<>();
    private static final File file = new File(MailManager.getInstance().getDataFolder(), "notify.yml");

    public static void load() {
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                if (config.getBoolean(key)) {
                    enabledNotify.add(UUID.fromString(key));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        Bukkit.getLogger().info("[MailManager] notify.yml loaded (" + enabledNotify.size() + " enabled)");
    }

    public static void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (UUID uuid : enabledNotify) {
            config.set(uuid.toString(), true);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[MailManager] Failed to save notify.yml");
            e.printStackTrace();
        }
    }

    public static boolean isNotifyEnabled(UUID uuid) {
        return enabledNotify.contains(uuid);
    }

    public static void toggle(UUID uuid) {
        if (enabledNotify.contains(uuid)) {
            enabledNotify.remove(uuid);
        } else {
            enabledNotify.add(uuid);
        }
    }

    public static void setNotify(UUID uuid, boolean enabled) {
        if (enabled) {
            enabledNotify.add(uuid);
        } else {
            enabledNotify.remove(uuid);
        }
    }
}
