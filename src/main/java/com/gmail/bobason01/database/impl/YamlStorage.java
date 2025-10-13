package com.gmail.bobason01.database.impl;

import com.gmail.bobason01.database.MailStorage;
import com.gmail.bobason01.mail.Mail;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class YamlStorage implements MailStorage {
    private File file;
    private YamlConfiguration config;

    @Override
    public void connect() throws Exception {
        file = new File("plugins/MailManager/data.yml");
        if (!file.exists()) file.createNewFile();
        config = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void disconnect() {}

    @Override
    public void ensureSchema() {}

    @Override
    public void batchInsertMails(List<MailRecord> records) throws Exception {
        for (MailRecord rec : records) {
            config.set("mails." + rec.mail().getMailId(), rec.mail());
        }
        save();
    }

    @Override
    public void batchDeleteMails(List<MailRecord> records) throws Exception {
        for (MailRecord rec : records) {
            config.set("mails." + rec.mail().getMailId(), null);
        }
        save();
    }

    @Override
    public List<Mail> loadMails(UUID receiver) {
        List<Mail> list = new ArrayList<>();
        if (config.isConfigurationSection("mails")) {
            for (String key : config.getConfigurationSection("mails").getKeys(false)) {
                Mail mail = (Mail) config.get("mails." + key);
                if (mail != null && mail.getReceiver().equals(receiver)) list.add(mail);
            }
        }
        return list;
    }

    @Override
    public void saveNotifySetting(UUID uuid, boolean enabled) throws Exception {
        config.set("notify." + uuid, enabled);
        save();
    }

    @Override
    public Boolean loadNotifySetting(UUID uuid) {
        return config.getBoolean("notify." + uuid, true);
    }

    @Override
    public void saveBlacklist(UUID owner, Set<UUID> list) throws Exception {
        config.set("blacklist." + owner, list.stream().map(UUID::toString).toList());
        save();
    }

    @Override
    public Set<UUID> loadBlacklist(UUID owner) {
        List<String> list = config.getStringList("blacklist." + owner);
        Set<UUID> out = new HashSet<>();
        for (String s : list) out.add(UUID.fromString(s));
        return out;
    }

    @Override
    public void saveExclude(UUID uuid, Set<UUID> list) throws Exception {
        config.set("exclude." + uuid, list.stream().map(UUID::toString).toList());
        save();
    }

    @Override
    public Set<UUID> loadExclude(UUID uuid) {
        List<String> list = config.getStringList("exclude." + uuid);
        Set<UUID> out = new HashSet<>();
        for (String s : list) out.add(UUID.fromString(s));
        return out;
    }

    @Override
    public void savePlayerLanguage(UUID uuid, String lang) throws Exception {
        config.set("lang." + uuid, lang);
        save();
    }

    @Override
    public String loadPlayerLanguage(UUID uuid) {
        return config.getString("lang." + uuid);
    }

    @Override
    public void saveInventory(int id, ItemStack[] contents) throws Exception {
        config.set("inventories." + id, contents);
        save();
    }

    @Override
    public ItemStack[] loadInventory(int id) {
        return ((List<ItemStack>) config.get("inventories." + id, new ArrayList<>())).toArray(new ItemStack[0]);
    }

    private void save() throws IOException {
        config.save(file);
    }
}
