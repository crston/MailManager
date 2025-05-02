package com.gmail.bobason01.storage;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.mail.Mail;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MailFileStorage {

    private static final File folder = new File(MailManager.getInstance().getDataFolder(), "mails");

    public static Map<UUID, List<Mail>> loadAll() {
        Map<UUID, List<Mail>> data = new HashMap<>();
        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return data;

        for (File file : files) {
            try {
                UUID uuid = UUID.fromString(file.getName().replace(".yml", ""));
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                List<Mail> mails = new ArrayList<>();

                ConfigurationSection section = config.getConfigurationSection("mails");
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        String path = "mails." + key;
                        UUID sender = UUID.fromString(config.getString(path + ".sender"));
                        ItemStack item = config.getItemStack(path + ".item");
                        long expire = config.getLong(path + ".expire");

                        if (sender != null && item != null) {
                            mails.add(new Mail(sender, item, expire));
                        }
                    }
                }

                data.put(uuid, mails);
            } catch (Exception e) {
                MailManager.getInstance().getLogger().warning("Failed to load mail file: " + file.getName());
            }
        }

        return data;
    }

    public static void saveAll(Map<UUID, List<Mail>> data) {
        if (!folder.exists()) folder.mkdirs();

        for (Map.Entry<UUID, List<Mail>> entry : data.entrySet()) {
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
}
