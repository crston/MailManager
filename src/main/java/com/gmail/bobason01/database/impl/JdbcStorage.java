package com.gmail.bobason01.database.impl;

import com.gmail.bobason01.database.MailStorage;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.*;

public class JdbcStorage implements MailStorage {
    private final DataSource ds;
    private final boolean isMySQL;

    public JdbcStorage(DataSource ds, boolean isMySQL) {
        this.ds = ds;
        this.isMySQL = isMySQL;
    }

    @Override
    public void connect() {}

    @Override
    public void disconnect() {}

    @Override
    public void ensureSchema() throws Exception {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            if (!isMySQL) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA temp_store=MEMORY");
                st.execute("PRAGMA mmap_size=134217728");
                st.execute("PRAGMA cache_size=-262144");
                st.execute("PRAGMA busy_timeout=5000");
            }
            st.executeUpdate("CREATE TABLE IF NOT EXISTS mails (" +
                    "id VARCHAR(36) PRIMARY KEY," +
                    "receiver VARCHAR(36) NOT NULL," +
                    "data BLOB NOT NULL" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS inventories (" +
                    "id INT PRIMARY KEY," +
                    "data BLOB" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mails_receiver ON mails(receiver)");
        }
    }

    @Override
    public void batchInsertMails(List<MailRecord> records) throws Exception {
        if (records.isEmpty()) return;
        String sql = "REPLACE INTO mails(id, receiver, data) VALUES(?,?,?)";
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (MailRecord rec : records) {
                    ps.setString(1, rec.mail().getMailId().toString());
                    ps.setString(2, rec.receiver().toString());
                    ps.setBytes(3, MailSerializer.serialize(rec.mail()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        }
    }

    @Override
    public void batchDeleteMails(List<MailRecord> records) throws Exception {
        if (records.isEmpty()) return;
        String sql = "DELETE FROM mails WHERE id=?";
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (MailRecord rec : records) {
                    ps.setString(1, rec.mail().getMailId().toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        }
    }

    @Override
    public List<Mail> loadMails(UUID receiver) throws Exception {
        List<Mail> list = new ArrayList<>();
        String sql = "SELECT data FROM mails WHERE receiver=?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, receiver.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Mail mail = MailSerializer.deserialize(rs.getBytes(1));
                    if (mail != null) list.add(mail);
                }
            }
        }
        return list;
    }

    @Override
    public void saveNotifySetting(UUID uuid, boolean enabled) throws Exception {}

    @Override
    public Boolean loadNotifySetting(UUID uuid) throws Exception { return true; }

    @Override
    public void saveBlacklist(UUID owner, Set<UUID> list) throws Exception {}

    @Override
    public Set<UUID> loadBlacklist(UUID owner) throws Exception { return new HashSet<>(); }

    @Override
    public void saveExclude(UUID uuid, Set<UUID> list) throws Exception {}

    @Override
    public Set<UUID> loadExclude(UUID uuid) throws Exception { return new HashSet<>(); }

    @Override
    public void savePlayerLanguage(UUID uuid, String lang) throws Exception {}

    @Override
    public String loadPlayerLanguage(UUID uuid) throws Exception { return null; }

    @Override
    public void saveInventory(int id, ItemStack[] contents) throws Exception {
        String sql = "REPLACE INTO inventories(id, data) VALUES(?,?)";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setBytes(2, serializeItems(contents));
            ps.executeUpdate();
        }
    }

    @Override
    public ItemStack[] loadInventory(int id) throws Exception {
        String sql = "SELECT data FROM inventories WHERE id=?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return deserializeItems(rs.getBytes(1));
            }
        }
        return null;
    }

    private byte[] serializeItems(Object obj) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        }
    }

    private ItemStack[] deserializeItems(byte[] data) throws Exception {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            return (ItemStack[]) ois.readObject();
        }
    }
}
