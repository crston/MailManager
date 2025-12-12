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

    // 파일 객체들
    private File mailsFile;
    private FileConfiguration mailsConfig;

    private File playersFile;
    private FileConfiguration playersConfig;

    private File inventoriesFile;
    private FileConfiguration inventoriesConfig;

    @Override
    public void connect() throws Exception {
        // 메일 데이터
        mailsFile = new File(plugin.getDataFolder(), "data/mails.yml");
        if (!mailsFile.exists()) {
            mailsFile.getParentFile().mkdirs();
            mailsFile.createNewFile();
        }
        mailsConfig = YamlConfiguration.loadConfiguration(mailsFile);

        // 플레이어 설정 및 글로벌 데이터
        playersFile = new File(plugin.getDataFolder(), "data/players.yml");
        if (!playersFile.exists()) {
            playersFile.getParentFile().mkdirs();
            playersFile.createNewFile();
        }
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);

        // 인벤토리 데이터
        inventoriesFile = new File(plugin.getDataFolder(), "data/inventories.yml");
        if (!inventoriesFile.exists()) {
            inventoriesFile.getParentFile().mkdirs();
            inventoriesFile.createNewFile();
        }
        inventoriesConfig = YamlConfiguration.loadConfiguration(inventoriesFile);
    }

    @Override
    public void disconnect() throws Exception {
        saveMails();
        savePlayers();
        saveInventories();
    }

    @Override
    public void ensureSchema() {
        // YAML은 스키마 생성 불필요
    }

    // --- 저장 헬퍼 메서드 ---
    private void saveMails() {
        try { mailsConfig.save(mailsFile); } catch (IOException e) { e.printStackTrace(); }
    }
    private void savePlayers() {
        try { playersConfig.save(playersFile); } catch (IOException e) { e.printStackTrace(); }
    }
    private void saveInventories() {
        try { inventoriesConfig.save(inventoriesFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // --- 메일 로직 ---

    @Override
    public List<Mail> loadMails(UUID receiver) {
        List<Mail> list = new ArrayList<>();
        String path = "mails." + receiver.toString();

        if (mailsConfig.isConfigurationSection(path)) {
            ConfigurationSection section = mailsConfig.getConfigurationSection(path);
            if (section != null) {
                for (String mailIdStr : section.getKeys(false)) {
                    String base64 = section.getString(mailIdStr);
                    if (base64 != null) {
                        try {
                            byte[] data = Base64.getDecoder().decode(base64);
                            Mail mail = MailSerializer.deserialize(data);
                            if (mail != null) list.add(mail);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Failed to load mail " + mailIdStr, e);
                        }
                    }
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
            String base64 = Base64.getEncoder().encodeToString(data);
            mailsConfig.set(path, base64);
        }
        saveMails();
    }

    @Override
    public void batchDeleteMails(List<MailRecord> records) throws Exception {
        if (records.isEmpty()) return;

        for (MailRecord rec : records) {
            String path = "mails." + rec.receiver().toString() + "." + rec.mail().getMailId().toString();
            mailsConfig.set(path, null);
        }
        saveMails();
    }

    // [구현됨] 플레이어 메일 전체 삭제
    @Override
    public void deletePlayerMails(UUID receiver) throws Exception {
        mailsConfig.set("mails." + receiver.toString(), null);
        saveMails();
    }

    // --- 설정 로직 ---

    @Override
    public void saveNotifySetting(UUID uuid, boolean enabled) throws Exception {
        playersConfig.set("notify." + uuid.toString(), enabled);
        savePlayers();
    }

    @Override
    public Boolean loadNotifySetting(UUID uuid) {
        return playersConfig.getBoolean("notify." + uuid.toString(), true);
    }

    @Override
    public void saveBlacklist(UUID owner, Set<UUID> list) throws Exception {
        List<String> strList = list.stream().map(UUID::toString).toList();
        playersConfig.set("blacklist." + owner.toString(), strList);
        savePlayers();
    }

    @Override
    public Set<UUID> loadBlacklist(UUID owner) {
        List<String> list = playersConfig.getStringList("blacklist." + owner.toString());
        Set<UUID> result = new HashSet<>();
        for (String s : list) {
            try {
                result.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    @Override
    public void saveExclude(UUID owner, Set<UUID> list) throws Exception {
        List<String> strList = list.stream().map(UUID::toString).toList();
        playersConfig.set("exclude." + owner.toString(), strList);
        savePlayers();
    }

    @Override
    public Set<UUID> loadExclude(UUID owner) {
        List<String> list = playersConfig.getStringList("exclude." + owner.toString());
        Set<UUID> result = new HashSet<>();
        for (String s : list) {
            try {
                result.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    @Override
    public void savePlayerLanguage(UUID uuid, String lang) throws Exception {
        playersConfig.set("lang." + uuid.toString(), lang);
        savePlayers();
    }

    @Override
    public String loadPlayerLanguage(UUID uuid) {
        return playersConfig.getString("lang." + uuid.toString());
    }

    // --- 인벤토리 로직 ---

    @Override
    public void saveInventory(int id, ItemStack[] contents) throws Exception {
        byte[] data = serializeItems(contents);
        String base64 = Base64.getEncoder().encodeToString(data);
        inventoriesConfig.set("inv." + id, base64);
        saveInventories();
    }

    @Override
    public ItemStack[] loadInventory(int id) throws Exception {
        String base64 = inventoriesConfig.getString("inv." + id);
        if (base64 == null) return null;

        byte[] data = Base64.getDecoder().decode(base64);
        return deserializeItems(data);
    }

    // --- 글로벌 플레이어 로직 ---

    @Override
    public void updateGlobalPlayer(UUID uuid, String name) throws Exception {
        if (name == null) return;
        // UUID -> Name
        playersConfig.set("global.uuid2name." + uuid.toString(), name);
        // Name -> UUID (소문자로 저장하여 대소문자 무시 검색 지원)
        playersConfig.set("global.name2uuid." + name.toLowerCase(Locale.ROOT), uuid.toString());
        savePlayers();
    }

    @Override
    public UUID lookupGlobalUUID(String name) throws Exception {
        if (name == null) return null;
        String uuidStr = playersConfig.getString("global.name2uuid." + name.toLowerCase(Locale.ROOT));
        if (uuidStr != null) {
            try {
                return UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    @Override
    public String lookupGlobalName(UUID uuid) throws Exception {
        if (uuid == null) return null;
        return playersConfig.getString("global.uuid2name." + uuid.toString());
    }

    // [구현됨] 전체 글로벌 UUID 조회
    @Override
    public Set<UUID> getAllGlobalUUIDs() {
        Set<UUID> uuids = new HashSet<>();
        ConfigurationSection section = playersConfig.getConfigurationSection("global.uuid2name");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    uuids.add(UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return uuids;
    }

    // --- 직렬화 헬퍼 ---

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