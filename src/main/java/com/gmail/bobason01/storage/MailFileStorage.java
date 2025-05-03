package com.gmail.bobason01.storage;

import com.gmail.bobason01.mail.Mail;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;

public class MailFileStorage {

    public static void loadAll(Plugin plugin,
                               Map<UUID, List<Mail>> inboxMap,
                               Map<UUID, Set<UUID>> blacklistMap,
                               Set<UUID> notifyDisabled,
                               Map<UUID, Set<UUID>> excludeMap) {

        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.getName().endsWith(".yml")) continue;

            try {
                UUID playerId = UUID.fromString(file.getName().replace(".yml", ""));
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);

                inboxMap.put(playerId, loadMails(config, playerId));
                blacklistMap.put(playerId, loadUUIDSet(config.getStringList("blacklist")));
                excludeMap.put(playerId, loadUUIDSet(config.getStringList("excluded")));

                if (!config.getBoolean("notify", true)) {
                    notifyDisabled.add(playerId); // notify=false → 알림 비활성화
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load data file: " + file.getName(), e);
            }
        }
    }

    public static void saveAll(Plugin plugin,
                               Map<UUID, List<Mail>> inboxMap,
                               Map<UUID, Set<UUID>> blacklistMap,
                               Set<UUID> notifyDisabled,
                               Map<UUID, Set<UUID>> excludeMap) {

        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();

        for (UUID playerId : inboxMap.keySet()) {
            File file = new File(folder, playerId + ".yml");
            FileConfiguration config = new YamlConfiguration();

            saveMails(config, inboxMap.get(playerId));
            config.set("blacklist", toStringList(blacklistMap.get(playerId)));
            config.set("excluded", toStringList(excludeMap.get(playerId)));
            config.set("notify", !notifyDisabled.contains(playerId)); // true if 알림 ON

            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Error saving mail data for " + playerId, e);
            }
        }
    }

    // ===== 내부 유틸 =====

    private static List<Mail> loadMails(FileConfiguration config, UUID receiver) {
        List<Mail> list = new ArrayList<>();
        if (!config.contains("mails")) return list;

        for (String key : Objects.requireNonNull(config.getConfigurationSection("mails")).getKeys(false)) {
            String path = "mails." + key + ".";
            try {
                UUID sender = UUID.fromString(Objects.requireNonNull(config.getString(path + "sender")));
                ItemStack item = config.getItemStack(path + "item");
                LocalDateTime sentAt = LocalDateTime.parse(Objects.requireNonNull(config.getString(path + "sentAt")));
                String expireStr = config.getString(path + "expireAt");
                LocalDateTime expireAt = (expireStr != null && !expireStr.isEmpty()) ? LocalDateTime.parse(expireStr) : null;

                list.add(new Mail(sender, receiver, item, sentAt, expireAt));
            } catch (Exception e) {
                // 개별 메일 로딩 실패 무시 (다음으로 계속)
            }
        }
        return list;
    }

    private static Set<UUID> loadUUIDSet(List<String> strings) {
        Set<UUID> set = new HashSet<>();
        for (String s : strings) {
            try {
                set.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }
        return set;
    }

    private static List<String> toStringList(Set<UUID> uuids) {
        List<String> list = new ArrayList<>();
        if (uuids != null) {
            for (UUID id : uuids) {
                list.add(id.toString());
            }
        }
        return list;
    }

    private static void saveMails(FileConfiguration config, List<Mail> mails) {
        if (mails == null) return;
        for (int i = 0; i < mails.size(); i++) {
            Mail mail = mails.get(i);
            String path = "mails." + i + ".";
            config.set(path + "sender", mail.getSender().toString());
            config.set(path + "item", mail.getItem());
            config.set(path + "sentAt", mail.getSentAt().toString());
            config.set(path + "expireAt", mail.getExpireAt() != null ? mail.getExpireAt().toString() : null);
        }
    }
}
