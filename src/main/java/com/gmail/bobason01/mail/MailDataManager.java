package com.gmail.bobason01.mail;

import com.gmail.bobason01.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class MailDataManager {

    private static final MailDataManager INSTANCE = new MailDataManager();
    public static MailDataManager getInstance() {
        return INSTANCE;
    }

    private final Map<UUID, Deque<Mail>> mailMap = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> notifyMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> blacklistMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> excludeMap = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    private Connection connection;
    private File dbFile;

    public void load(JavaPlugin plugin) {
        try {
            dbFile = new File(plugin.getDataFolder(), "mail_data.db");
            plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
            setupTable();
            loadFromDatabase();
        } catch (Exception e) {
            Bukkit.getLogger().severe("[MailManager] SQLite initialization failed: " + e.getMessage());
        }
    }

    public void unload() {
        saveToDatabase();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[MailManager] SQLite shutdown failed: " + e.getMessage());
        }
    }

    private void setupTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS mails (id INTEGER PRIMARY KEY AUTOINCREMENT, receiver TEXT NOT NULL, data BLOB NOT NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS notify (uuid TEXT PRIMARY KEY, enabled INTEGER NOT NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS blacklist (owner TEXT NOT NULL, target TEXT NOT NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS exclude (uuid TEXT NOT NULL, excluded TEXT NOT NULL)");
        }
    }

    public void addMail(UUID receiver, Mail mail) {
        mailMap.computeIfAbsent(receiver, k -> new ConcurrentLinkedDeque<>()).addLast(mail);
        dirty.set(true);

        if (isNotifyEnabled(receiver)) {
            Player player = Bukkit.getPlayer(receiver);
            if (player != null && player.isOnline()) {
                String lang = LangManager.getLanguage(receiver);
                player.sendMessage(LangManager.get(lang, "mail.notify.message"));
                player.sendTitle(
                        LangManager.get(lang, "mail.notify.title.main"),
                        LangManager.get(lang, "mail.notify.title.sub"),
                        10, 40, 20
                );
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            }
        }
    }

    public Deque<Mail> getMails(UUID uuid) {
        return mailMap.getOrDefault(uuid, new ConcurrentLinkedDeque<>());
    }

    public List<Mail> getUnreadMails(UUID uuid) {
        Deque<Mail> mails = getMails(uuid);
        List<Mail> unread = new ArrayList<>();
        for (Mail mail : mails) {
            if (!mail.isRead() && !mail.isExpired()) {
                unread.add(mail);
            }
        }
        return unread;
    }

    public void removeMail(UUID uuid, Mail mail) {
        Deque<Mail> mails = mailMap.get(uuid);
        if (mails != null && mails.remove(mail)) {
            dirty.set(true);
        }
    }

    public boolean isNotifyEnabled(UUID uuid) {
        return notifyMap.getOrDefault(uuid, true);
    }

    public boolean toggleNotification(UUID uuid) {
        boolean current = isNotifyEnabled(uuid);
        setNotify(uuid, !current);
        return !current;
    }

    public void setNotify(UUID uuid, boolean enabled) {
        notifyMap.put(uuid, enabled);
        saveNotifySetting(uuid, enabled);
    }

    private void saveNotifySetting(UUID uuid, boolean enabled) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO notify (uuid, enabled) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET enabled = excluded.enabled"
        )) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, enabled ? 1 : 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[MailManager] Failed to save notification setting: " + e.getMessage());
        }
    }

    public Set<UUID> getBlacklist(UUID owner) {
        return blacklistMap.getOrDefault(owner, Collections.emptySet());
    }

    public void setBlacklist(UUID owner, Set<UUID> list) {
        blacklistMap.put(owner, new HashSet<>(list));
        try (PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM blacklist WHERE owner = ?")) {
            deleteStmt.setString(1, owner.toString());
            deleteStmt.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[MailManager] Failed to delete blacklist: " + e.getMessage());
        }

        try (PreparedStatement insertStmt = connection.prepareStatement("INSERT INTO blacklist (owner, target) VALUES (?, ?)")) {
            for (UUID target : list) {
                insertStmt.setString(1, owner.toString());
                insertStmt.setString(2, target.toString());
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[MailManager] Failed to save blacklist: " + e.getMessage());
        }
    }

    public Set<UUID> getExclude(UUID player) {
        return excludeMap.getOrDefault(player, Collections.emptySet());
    }

    public void setExclude(UUID uuid, Set<UUID> excludes) {
        excludeMap.put(uuid, new HashSet<>(excludes));

        try (PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM exclude WHERE uuid = ?")) {
            deleteStmt.setString(1, uuid.toString());
            deleteStmt.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[MailManager] Failed to delete exclusion list: " + e.getMessage());
        }

        try (PreparedStatement insertStmt = connection.prepareStatement("INSERT INTO exclude (uuid, excluded) VALUES (?, ?)")) {
            for (UUID excluded : excludes) {
                insertStmt.setString(1, uuid.toString());
                insertStmt.setString(2, excluded.toString());
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[MailManager] Failed to save exclusion list: " + e.getMessage());
        }
    }

    public void save() {
        saveToDatabase();
    }

    private void loadFromDatabase() {
        loadMails();
        loadNotifySettings();
        loadBlacklist();
        loadExclude();
    }

    private void loadMails() {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT receiver, data FROM mails")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID receiver = UUID.fromString(rs.getString("receiver"));
                byte[] blob = rs.getBytes("data");
                Mail mail = MailSerializer.deserialize(blob);
                if (mail != null) {
                    mailMap.computeIfAbsent(receiver, k -> new ConcurrentLinkedDeque<>()).add(mail);
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().severe("[MailManager] Failed to load mails from SQLite: " + e.getMessage());
        }
    }

    private void loadNotifySettings() {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT uuid, enabled FROM notify")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                boolean enabled = rs.getInt("enabled") == 1;
                notifyMap.put(uuid, enabled);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[MailManager] Failed to load notification settings: " + e.getMessage());
        }
    }

    private void loadBlacklist() {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT owner, target FROM blacklist")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID owner = UUID.fromString(rs.getString("owner"));
                UUID target = UUID.fromString(rs.getString("target"));
                blacklistMap.computeIfAbsent(owner, k -> new HashSet<>()).add(target);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[MailManager] Failed to load blacklist: " + e.getMessage());
        }
    }

    private void loadExclude() {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT uuid, excluded FROM exclude")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                UUID excluded = UUID.fromString(rs.getString("excluded"));
                excludeMap.computeIfAbsent(uuid, k -> new HashSet<>()).add(excluded);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[MailManager] Failed to load exclusion list: " + e.getMessage());
        }
    }

    private void saveToDatabase() {
        if (!dirty.getAndSet(false)) return;
        try (Statement clearStmt = connection.createStatement()) {
            clearStmt.executeUpdate("DELETE FROM mails");
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[MailManager] Failed to clear existing mails: " + e.getMessage());
        }

        try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO mails (receiver, data) VALUES (?, ?)")) {
            for (Map.Entry<UUID, Deque<Mail>> entry : mailMap.entrySet()) {
                UUID uuid = entry.getKey();
                for (Mail mail : entry.getValue()) {
                    byte[] data = MailSerializer.serialize(mail);
                    if (data == null) {
                        Bukkit.getLogger().warning("[MailManager] Skipped saving mail due to serialization failure: " + mail.getMailId());
                        continue;
                    }
                    stmt.setString(1, uuid.toString());
                    stmt.setBytes(2, data);
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();
        } catch (Exception e) {
            Bukkit.getLogger().severe("[MailManager] Failed to save mails: " + e.getMessage());
        }
    }
}
