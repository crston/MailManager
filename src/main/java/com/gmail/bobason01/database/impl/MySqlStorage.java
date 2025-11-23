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

public class MySqlStorage implements MailStorage {

    private final DataSource ds;

    public MySqlStorage(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public void connect() throws Exception {
    }

    @Override
    public void disconnect() throws Exception {
    }

    @Override
    public void ensureSchema() throws Exception {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS mails (" +
                            "id VARCHAR(36) PRIMARY KEY," +
                            "receiver VARCHAR(36) NOT NULL," +
                            "data LONGBLOB NOT NULL," +
                            "INDEX idx_mails_receiver (receiver)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS inventories (" +
                            "id INT PRIMARY KEY," +
                            "data LONGBLOB" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS notify_settings (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "enabled TINYINT(1) NOT NULL" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS blacklist (" +
                            "owner VARCHAR(36) NOT NULL," +
                            "target VARCHAR(36) NOT NULL," +
                            "PRIMARY KEY(owner, target)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS exclude_list (" +
                            "owner VARCHAR(36) NOT NULL," +
                            "target VARCHAR(36) NOT NULL," +
                            "PRIMARY KEY(owner, target)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_lang (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "lang VARCHAR(16)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
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
    public void batchInsertMails(List<MailRecord> list) throws Exception {
        if (list.isEmpty()) return;

        String sql = "INSERT INTO mails(id, receiver, data) VALUES(?,?,?) " +
                "ON DUPLICATE KEY UPDATE receiver=VALUES(receiver), data=VALUES(data)";

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (MailRecord rec : list) {
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
    public void batchDeleteMails(List<MailRecord> list) throws Exception {
        if (list.isEmpty()) return;

        String sql = "DELETE FROM mails WHERE id=?";

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (MailRecord rec : list) {
                    ps.setString(1, rec.mail().getMailId().toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        }
    }

    @Override
    public void saveNotifySetting(UUID uuid, boolean enabled) throws Exception {
        String sql = "INSERT INTO notify_settings(uuid, enabled) VALUES(?,?) " +
                "ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setBoolean(2, enabled);
            ps.executeUpdate();
        }
    }

    @Override
    public Boolean loadNotifySetting(UUID uuid) throws Exception {
        String sql = "SELECT enabled FROM notify_settings WHERE uuid=?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBoolean(1);
            }
        }
        return true;
    }

    @Override
    public void saveBlacklist(UUID owner, Set<UUID> list) throws Exception {
        String del = "DELETE FROM blacklist WHERE owner=?";
        String ins = "INSERT INTO blacklist(owner, target) VALUES(?,?)";

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(del)) {
                ps.setString(1, owner.toString());
                ps.executeUpdate();
            }

            if (!list.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(ins)) {
                    for (UUID t : list) {
                        ps.setString(1, owner.toString());
                        ps.setString(2, t.toString());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            conn.commit();
        }
    }

    @Override
    public Set<UUID> loadBlacklist(UUID owner) throws Exception {
        Set<UUID> out = new HashSet<>();
        String sql = "SELECT target FROM blacklist WHERE owner=?";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
            }
        }
        return out;
    }

    @Override
    public void saveExclude(UUID owner, Set<UUID> list) throws Exception {
        String del = "DELETE FROM exclude_list WHERE owner=?";
        String ins = "INSERT INTO exclude_list(owner, target) VALUES(?,?)";

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(del)) {
                ps.setString(1, owner.toString());
                ps.executeUpdate();
            }

            if (!list.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(ins)) {
                    for (UUID t : list) {
                        ps.setString(1, owner.toString());
                        ps.setString(2, t.toString());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            conn.commit();
        }
    }

    @Override
    public Set<UUID> loadExclude(UUID owner) throws Exception {
        Set<UUID> out = new HashSet<>();
        String sql = "SELECT target FROM exclude_list WHERE owner=?";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
            }
        }
        return out;
    }

    @Override
    public void savePlayerLanguage(UUID uuid, String lang) throws Exception {
        String sql = "INSERT INTO player_lang(uuid, lang) VALUES(?,?) " +
                "ON DUPLICATE KEY UPDATE lang=VALUES(lang)";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, lang);
            ps.executeUpdate();
        }
    }

    @Override
    public String loadPlayerLanguage(UUID uuid) throws Exception {
        String sql = "SELECT lang FROM player_lang WHERE uuid=?";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    @Override
    public void saveInventory(int id, ItemStack[] contents) throws Exception {
        String sql = "REPLACE INTO inventories(id, data) VALUES(?,?)";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.setBytes(2, serialize(contents));
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
                if (rs.next()) return deserialize(rs.getBytes(1));
            }
        }
        return null;
    }

    private byte[] serialize(Object obj) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {

            oos.writeObject(obj);
            return bos.toByteArray();
        }
    }

    private ItemStack[] deserialize(byte[] data) throws Exception {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {

            return (ItemStack[]) ois.readObject();
        }
    }
}
