package com.gmail.bobason01.database.impl;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.database.MailStorage;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class YamlStorage implements MailStorage {

    private final MailManager plugin = MailManager.getInstance();

    private File mailsFile;
    private FileConfiguration mailsConfig;
    private File playersFile;
    private FileConfiguration playersConfig;
    private File inventoriesFile;
    private FileConfiguration inventoriesConfig;

    @Override
    public void connect() throws Exception {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();

        mailsFile = new File(dataFolder, "mails.yml");
        playersFile = new File(dataFolder, "players.yml");
        inventoriesFile = new File(dataFolder, "inventories.yml");

        if (!mailsFile.exists()) mailsFile.createNewFile();
        if (!playersFile.exists()) playersFile.createNewFile();
        if (!inventoriesFile.exists()) inventoriesFile.createNewFile();

        mailsConfig = YamlConfiguration.loadConfiguration(mailsFile);
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);
        inventoriesConfig = YamlConfiguration.loadConfiguration(inventoriesFile);
    }

    @Override
    public void disconnect() throws Exception {
        // 종료 시에만 모든 데이터를 한꺼번에 물리 디스크에 기록 (성능 최적화)
        saveAll();
    }

    @Override
    public void ensureSchema() {}

    private void saveAll() {
        saveMails();
        savePlayers();
        saveInventories();
    }

    private void saveMails() {
        try { mailsConfig.save(mailsFile); } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not save mails.yml", e); }
    }
    private void savePlayers() {
        try { playersConfig.save(playersFile); } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not save players.yml", e); }
    }
    private void saveInventories() {
        try { inventoriesConfig.save(inventoriesFile); } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not save inventories.yml", e); }
    }

    @Override
    public List<Mail> loadMails(UUID receiver) {
        List<Mail> list = new ArrayList<>();
        String path = "mails." + receiver.toString();

        ConfigurationSection section = mailsConfig.getConfigurationSection(path);
        if (section != null) {
            for (String mailIdStr : section.getKeys(false)) {
                String base64 = section.getString(mailIdStr);
                if (base64 == null) continue;
                try {
                    byte[] data = Base64.getDecoder().decode(base64);
                    // CRITICAL: receiver UUID 전달로 복사 버그 방지
                    Mail mail = MailSerializer.deserialize(data, receiver);
                    if (mail != null) list.add(mail);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load mail " + mailIdStr + " for " + receiver, e);
                }
            }
        }
        return list;
    }

    @Override
    public void batchInsertMails(List<MailRecord> records) throws Exception {
        if (records.isEmpty()) return;
        for (MailRecord rec : records) {
            String path = "mails." + rec.receiver().toString() + "." + rec.mail().getMailId().toString();
            byte[] data = MailSerializer.serialize(rec.mail());
            mailsConfig.set(path, Base64.getEncoder().encodeToString(data));
        }
        // 매 건마다 저장하지 않고 메모리 유지 (MailDataManager가 적절한 타이밍에 flush 호출 유도)
    }

    @Override
    public void batchDeleteMails(List<MailRecord> records) throws Exception {
        if (records.isEmpty()) return;
        for (MailRecord rec : records) {
            String path = "mails." + rec.receiver().toString() + "." + rec.mail().getMailId().toString();
            mailsConfig.set(path, null);
        }
    }

    @Override
    public void deletePlayerMails(UUID receiver) throws Exception {
        mailsConfig.set("mails." + receiver.toString(), null);
    }

    @Override
    public void saveNotifySetting(UUID uuid, boolean enabled) throws Exception {
        playersConfig.set("notify." + uuid.toString(), enabled);
    }

    @Override
    public Boolean loadNotifySetting(UUID uuid) {
        return playersConfig.contains("notify." + uuid.toString()) ? playersConfig.getBoolean("notify." + uuid.toString()) : null;
    }

    @Override
    public void saveBlacklist(UUID owner, Set<UUID> list) throws Exception {
        List<String> strList = list.stream().map(UUID::toString).toList();
        playersConfig.set("blacklist." + owner.toString(), strList);
    }

    @Override
    public Set<UUID> loadBlacklist(UUID owner) {
        List<String> list = playersConfig.getStringList("blacklist." + owner.toString());
        if (list.isEmpty()) return new HashSet<>();
        Set<UUID> result = new HashSet<>();
        for (String s : list) {
            try { result.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        return result;
    }

    @Override
    public void saveExclude(UUID owner, Set<UUID> list) throws Exception {
        List<String> strList = list.stream().map(UUID::toString).toList();
        playersConfig.set("exclude." + owner.toString(), strList);
    }

    @Override
    public Set<UUID> loadExclude(UUID owner) {
        List<String> list = playersConfig.getStringList("exclude." + owner.toString());
        if (list.isEmpty()) return new HashSet<>();
        Set<UUID> result = new HashSet<>();
        for (String s : list) {
            try { result.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        return result;
    }

    @Override
    public void savePlayerLanguage(UUID uuid, String lang) throws Exception {
        playersConfig.set("lang." + uuid.toString(), lang);
    }

    @Override
    public String loadPlayerLanguage(UUID uuid) {
        return playersConfig.getString("lang." + uuid.toString());
    }

    @Override
    public void saveInventory(int id, ItemStack[] contents) throws Exception {
        byte[] data = serializeItems(contents);
        inventoriesConfig.set("inv." + id, Base64.getEncoder().encodeToString(data));
    }

    @Override
    public ItemStack[] loadInventory(int id) throws Exception {
        String base64 = inventoriesConfig.getString("inv." + id);
        return base64 == null ? null : deserializeItems(Base64.getDecoder().decode(base64));
    }

    @Override
    public void updateGlobalPlayer(UUID uuid, String name) throws Exception {
        if (name == null) return;
        playersConfig.set("global.uuid2name." + uuid.toString(), name);
        playersConfig.set("global.name2uuid." + name.toLowerCase(Locale.ROOT), uuid.toString());
    }

    @Override
    public UUID lookupGlobalUUID(String name) throws Exception {
        String uuidStr = playersConfig.getString("global.name2uuid." + name.toLowerCase(Locale.ROOT));
        try { return uuidStr != null ? UUID.fromString(uuidStr) : null; } catch (Exception e) { return null; }
    }

    @Override
    public String lookupGlobalName(UUID uuid) throws Exception {
        return playersConfig.getString("global.uuid2name." + uuid.toString());
    }

    @Override
    public Set<UUID> getAllGlobalUUIDs() {
        Set<UUID> uuids = new HashSet<>();
        ConfigurationSection section = playersConfig.getConfigurationSection("global.uuid2name");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try { uuids.add(UUID.fromString(key)); } catch (Exception ignored) {}
            }
        }
        return uuids;
    }

    private byte[] serializeItems(ItemStack[] items) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeObject(items);
            return bos.toByteArray();
        }
    }

    private ItemStack[] deserializeItems(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            return (ItemStack[]) ois.readObject();
        }
    }
}