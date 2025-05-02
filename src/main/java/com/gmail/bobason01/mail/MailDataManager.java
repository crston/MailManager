package com.gmail.bobason01.mail;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MailDataManager {

    private static final File folder = new File(Bukkit.getPluginManager().getPlugin("MailManager").getDataFolder(), "mails");

    // UUID → 메일 리스트
    private static final Map<UUID, List<Mail>> mailMap = new HashMap<>();

    /**
     * 메일 전체 불러오기 (플러그인 시작 시)
     */
    public static void loadAll() {
        mailMap.clear();
        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles((f, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                UUID uuid = UUID.fromString(file.getName().replace(".yml", ""));
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

                List<Mail> list = new ArrayList<>();
                ConfigurationSection section = config.getConfigurationSection("mails");
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        UUID sender = UUID.fromString(section.getString(key + ".sender"));
                        ItemStack item = section.getItemStack(key + ".item");
                        long expire = section.getLong(key + ".expire");

                        if (sender != null && item != null) {
                            list.add(new Mail(sender, item, expire));
                        }
                    }
                }

                mailMap.put(uuid, list);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[MailManager] Failed to load mail: " + file.getName());
                e.printStackTrace();
            }
        }
    }

    /**
     * 메일 전체 저장 (플러그인 종료 시)
     */
    public static void saveAll() {
        if (!folder.exists()) folder.mkdirs();

        for (Map.Entry<UUID, List<Mail>> entry : mailMap.entrySet()) {
            UUID uuid = entry.getKey();
            List<Mail> mails = entry.getValue();

            File file = new File(folder, uuid + ".yml");
            YamlConfiguration config = new YamlConfiguration();

            int index = 0;
            for (Mail mail : mails) {
                String path = "mails." + index++;
                config.set(path + ".sender", mail.getSender().toString());
                config.set(path + ".item", mail.getItem());
                config.set(path + ".expire", mail.getExpireAtMillis());
            }

            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static List<Mail> getInbox(UUID uuid) {
        return mailMap.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    public static void addMail(UUID receiver, Mail mail) {
        getInbox(receiver).add(mail);
    }

    public static void removeMail(UUID receiver, Mail mail) {
        getInbox(receiver).remove(mail);
    }

    public static void clearInbox(UUID receiver) {
        getInbox(receiver).clear();
    }
}
