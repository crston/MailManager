package com.gmail.bobason01.database.impl;

import com.gmail.bobason01.database.MailStorage;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailSerializer;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class JdbcStorage implements MailStorage {
    private final DataSource ds;
    private final boolean mysql;
    public JdbcStorage(DataSource ds, boolean mysql) {
        this.ds = ds;
        this.mysql = mysql;
    }
    @Override public void connect() {}
    @Override public void disconnect() {}
    @Override public void ensureSchema() throws Exception {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS mails (receiver VARCHAR(36), mail_id VARCHAR(36), data BLOB, PRIMARY KEY(receiver, mail_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS notify (uuid VARCHAR(36) PRIMARY KEY, enabled TINYINT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS blacklist (owner VARCHAR(36), target VARCHAR(36), PRIMARY KEY(owner, target))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS exclude (uuid VARCHAR(36), excluded VARCHAR(36), PRIMARY KEY(uuid, excluded))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS player_settings (uuid VARCHAR(36) PRIMARY KEY, lang VARCHAR(32))");
        }
    }
    @Override public void batchInsertMails(List<MailRecord> records) throws Exception {
        if (records.isEmpty()) return;
        String sql = "INSERT INTO mails (receiver, mail_id, data) VALUES (?, ?, ?) " + (mysql ? "ON DUPLICATE KEY UPDATE data=VALUES(data)" : "ON CONFLICT(receiver, mail_id) DO UPDATE SET data=excluded.data");
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (MailRecord r : records) {
                ps.setString(1, r.receiver().toString());
                ps.setString(2, r.mail().getMailId().toString());
                ps.setBytes(3, MailSerializer.serialize(r.mail()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
    @Override public void batchDeleteMails(List<MailRecord> records) throws Exception {
        if (records.isEmpty()) return;
        String sql = "DELETE FROM mails WHERE receiver=? AND mail_id=?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (MailRecord r : records) {
                ps.setString(1, r.receiver().toString());
                ps.setString(2, r.mail().getMailId().toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
    @Override public List<Mail> loadMails(UUID receiver) throws Exception {
        List<Mail> list = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT data FROM mails WHERE receiver=?")) {
            ps.setString(1, receiver.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Mail m = MailSerializer.deserialize(rs.getBytes(1));
                    if (m != null) list.add(m);
                }
            }
        }
        return list;
    }
    @Override public void saveNotifySetting(UUID uuid, boolean enabled) throws Exception {
        String sql = mysql ? "INSERT INTO notify (uuid, enabled) VALUES (?, ?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)" : "INSERT INTO notify (uuid, enabled) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET enabled=excluded.enabled";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, enabled ? 1 : 0);
            ps.executeUpdate();
        }
    }
    @Override public Boolean loadNotifySetting(UUID uuid) throws Exception {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT enabled FROM notify WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) == 1;
            }
        }
        return null;
    }
    @Override public void saveBlacklist(UUID owner, Set<UUID> list) throws Exception {
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM blacklist WHERE owner=?")) {
                del.setString(1, owner.toString());
                del.executeUpdate();
            }
            if (!list.isEmpty()) {
                try (PreparedStatement ins = c.prepareStatement("INSERT INTO blacklist (owner, target) VALUES (?, ?)")) {
                    for (UUID t : list) {
                        ins.setString(1, owner.toString());
                        ins.setString(2, t.toString());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }
        }
    }
    @Override public Set<UUID> loadBlacklist(UUID owner) throws Exception {
        Set<UUID> set = new HashSet<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT target FROM blacklist WHERE owner=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) set.add(UUID.fromString(rs.getString(1)));
            }
        }
        return set;
    }
    @Override public void saveExclude(UUID uuid, Set<UUID> list) throws Exception {
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM exclude WHERE uuid=?")) {
                del.setString(1, uuid.toString());
                del.executeUpdate();
            }
            if (!list.isEmpty()) {
                try (PreparedStatement ins = c.prepareStatement("INSERT INTO exclude (uuid, excluded) VALUES (?, ?)")) {
                    for (UUID t : list) {
                        ins.setString(1, uuid.toString());
                        ins.setString(2, t.toString());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }
        }
    }
    @Override public Set<UUID> loadExclude(UUID uuid) throws Exception {
        Set<UUID> set = new HashSet<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT excluded FROM exclude WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) set.add(UUID.fromString(rs.getString(1)));
            }
        }
        return set;
    }
    @Override public void savePlayerLanguage(UUID uuid, String lang) throws Exception {
        String sql = mysql ? "INSERT INTO player_settings (uuid, lang) VALUES (?, ?) ON DUPLICATE KEY UPDATE lang=VALUES(lang)" : "INSERT INTO player_settings (uuid, lang) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET lang=excluded.lang";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, lang);
            ps.executeUpdate();
        }
    }
    @Override public String loadPlayerLanguage(UUID uuid) throws Exception {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT lang FROM player_settings WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }
}
