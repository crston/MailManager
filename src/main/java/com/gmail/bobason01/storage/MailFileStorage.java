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
                               Set<UUID> notifyEnabled,
                               Map<UUID, Set<UUID>> excludeMap) {

        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();

        for (File file : Objects.requireNonNull(folder.listFiles())) {
            if (!file.getName().endsWith(".yml")) continue;

            UUID playerId = UUID.fromString(file.getName().replace(".yml", ""));
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            // 메일
            List<Mail> mails = new ArrayList<>();
            if (config.contains("mails")) {
                for (String key : Objects.requireNonNull(config.getConfigurationSection("mails")).getKeys(false)) {
                    String path = "mails." + key + ".";
                    UUID sender = UUID.fromString(Objects.requireNonNull(config.getString(path + "sender")));
                    ItemStack item = config.getItemStack(path + "item");
                    LocalDateTime sentAt = LocalDateTime.parse(Objects.requireNonNull(config.getString(path + "sentAt")));
                    String expireStr = config.getString(path + "expireAt");
                    LocalDateTime expireAt = expireStr == null ? null : LocalDateTime.parse(expireStr);
                    mails.add(new Mail(sender, playerId, item, sentAt, expireAt));
                }
            }
            inboxMap.put(playerId, mails);

            // 블랙리스트
            Set<UUID> blockedSet = new HashSet<>();
            for (String s : config.getStringList("blacklist")) {
                blockedSet.add(UUID.fromString(s));
            }
            blacklistMap.put(playerId, blockedSet);

            // 제외 대상
            Set<UUID> excludedSet = new HashSet<>();
            for (String s : config.getStringList("excluded")) {
                excludedSet.add(UUID.fromString(s));
            }
            excludeMap.put(playerId, excludedSet);

            // 알림 여부
            if (config.getBoolean("notify", true)) {
                notifyEnabled.add(playerId);
            }
        }
    }

    public static void saveAll(Plugin plugin,
                               Map<UUID, List<Mail>> inboxMap,
                               Map<UUID, Set<UUID>> blacklistMap,
                               Set<UUID> notifyEnabled,
                               Map<UUID, Set<UUID>> excludeMap) {

        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();

        for (UUID playerId : inboxMap.keySet()) {
            File file = new File(folder, playerId + ".yml");
            FileConfiguration config = new YamlConfiguration();

            // 메일
            List<Mail> mails = inboxMap.get(playerId);
            for (int i = 0; i < mails.size(); i++) {
                Mail mail = mails.get(i);
                String path = "mails." + i + ".";
                config.set(path + "sender", mail.getSender().toString());
                config.set(path + "item", mail.getItem());
                config.set(path + "sentAt", mail.getSentAt().toString());
                config.set(path + "expireAt", mail.getExpireAt() == null ? null : mail.getExpireAt().toString());
            }

            // 블랙리스트
            List<String> blockedList = new ArrayList<>();
            for (UUID id : blacklistMap.getOrDefault(playerId, new HashSet<>())) {
                blockedList.add(id.toString());
            }
            config.set("blacklist", blockedList);

            // 제외 대상
            List<String> excludedList = new ArrayList<>();
            for (UUID id : excludeMap.getOrDefault(playerId, new HashSet<>())) {
                excludedList.add(id.toString());
            }
            config.set("excluded", excludedList);

            // 알림
            config.set("notify", notifyEnabled.contains(playerId));

            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Error saving mail data for " + playerId, e);
            }
        }
    }
}
