package com.gmail.bobason01.database;

import com.gmail.bobason01.mail.Mail;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MailStorage {
    void connect() throws Exception;
    void disconnect();
    void ensureSchema() throws Exception;

    void batchInsertMails(List<MailRecord> records) throws Exception;
    void batchDeleteMails(List<MailRecord> records) throws Exception;
    List<Mail> loadMails(UUID receiver) throws Exception;

    void saveNotifySetting(UUID uuid, boolean enabled) throws Exception;
    Boolean loadNotifySetting(UUID uuid) throws Exception;

    void saveBlacklist(UUID owner, Set<UUID> list) throws Exception;
    Set<UUID> loadBlacklist(UUID owner) throws Exception;

    void saveExclude(UUID uuid, Set<UUID> list) throws Exception;
    Set<UUID> loadExclude(UUID uuid) throws Exception;

    void savePlayerLanguage(UUID uuid, String lang) throws Exception;
    String loadPlayerLanguage(UUID uuid) throws Exception;

    void saveInventory(int id, ItemStack[] contents) throws Exception;
    ItemStack[] loadInventory(int id) throws Exception;

    record MailRecord(UUID receiver, Mail mail) {}
}
