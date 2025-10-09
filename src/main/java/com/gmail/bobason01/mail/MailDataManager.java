package com.gmail.bobason01.mail;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.database.DatabaseType;
import com.gmail.bobason01.database.MailStorage;
import com.gmail.bobason01.database.datasource.DataSourceFactory;
import com.gmail.bobason01.database.impl.JdbcStorage;
import com.gmail.bobason01.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class MailDataManager {
    private static final MailDataManager INSTANCE = new MailDataManager();
    public static MailDataManager getInstance() { return INSTANCE; }
    private final Map<UUID, Deque<Mail>> mailMap = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> notifyMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> blacklistMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> excludeMap = new ConcurrentHashMap<>();
    private final Queue<MailStorage.MailRecord> insertQueue = new ConcurrentLinkedQueue<>();
    private final Queue<MailStorage.MailRecord> deleteQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private MailStorage storage;
    private int taskId = -1;
    public void load(JavaPlugin plugin) {
        try {
            FileConfiguration cfg = MailManager.getInstance().getConfig();
            DatabaseType type = DatabaseType.valueOf(cfg.getString("database.type", "SQLITE").toUpperCase());
            if (type == DatabaseType.YAML) {
                throw new UnsupportedOperationException("YAML storage not optimized, use JDBC");
            } else {
                DataSource ds = DataSourceFactory.build(type);
                storage = new JdbcStorage(ds, type == DatabaseType.MYSQL);
            }
            storage.connect();
            storage.ensureSchema();
            loadAll();
            int interval = cfg.getInt("flush.intervalTicks", 40);
            if (started.compareAndSet(false, true)) {
                taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(MailManager.getInstance(), this::flush, interval, interval);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MailManager] Initialization failed", e);
        }
    }
    public void unload() {
        try {
            if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
            flush();
            storage.disconnect();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[MailManager] Shutdown failed", e);
        }
    }
    private void loadAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID u = p.getUniqueId();
            try {
                List<Mail> list = storage.loadMails(u);
                if (!list.isEmpty()) mailMap.put(u, new ArrayDeque<>(list));
                Boolean n = storage.loadNotifySetting(u);
                if (n != null) notifyMap.put(u, n);
                Set<UUID> bl = storage.loadBlacklist(u);
                if (!bl.isEmpty()) blacklistMap.put(u, bl);
                Set<UUID> ex = storage.loadExclude(u);
                if (!ex.isEmpty()) excludeMap.put(u, ex);
                String lang = storage.loadPlayerLanguage(u);
                if (lang != null) LangManager.loadUserLanguage(u, lang);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[MailManager] Load failed for " + u, e);
            }
        }
    }
    public void flush() {
        List<MailStorage.MailRecord> ins = new ArrayList<>();
        MailStorage.MailRecord rec;
        while ((rec = insertQueue.poll()) != null) {
            ins.add(rec);
        }

        List<MailStorage.MailRecord> del = new ArrayList<>();
        while ((rec = deleteQueue.poll()) != null) {
            del.add(rec);
        }

        try {
            if (!ins.isEmpty()) storage.batchInsertMails(ins);
            if (!del.isEmpty()) storage.batchDeleteMails(del);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[MailManager] Flush failed", e);
        }
    }
    public void addMail(Mail mail) {
        mailMap.computeIfAbsent(mail.getReceiver(), k -> new ArrayDeque<>()).add(mail);
        insertQueue.add(new MailStorage.MailRecord(mail.getReceiver(), mail));
    }
    public void removeMail(Mail mail) {
        Deque<Mail> dq = mailMap.get(mail.getReceiver());
        if (dq != null) dq.remove(mail);
        deleteQueue.add(new MailStorage.MailRecord(mail.getReceiver(), mail));
    }
    public List<Mail> getMails(UUID u) {
        return new ArrayList<>(mailMap.getOrDefault(u, new ArrayDeque<>()));
    }
    public void setNotify(UUID u, boolean e) {
        notifyMap.put(u, e);
        try { storage.saveNotifySetting(u, e); } catch (Exception ex) {}
    }
    public boolean isNotify(UUID u) {
        return notifyMap.getOrDefault(u, true);
    }
    public void setBlacklist(UUID o, Set<UUID> l) {
        blacklistMap.put(o, l);
        try { storage.saveBlacklist(o, l); } catch (Exception ex) {}
    }
    public Set<UUID> getBlacklist(UUID o) { return blacklistMap.getOrDefault(o, Collections.emptySet()); }
    public void setExclude(UUID u, Set<UUID> l) {
        excludeMap.put(u, l);
        try { storage.saveExclude(u, l); } catch (Exception ex) {}
    }
    public Set<UUID> getExclude(UUID u) { return excludeMap.getOrDefault(u, Collections.emptySet()); }
    public void savePlayerLanguage(UUID u, String l) {
        try { storage.savePlayerLanguage(u, l); } catch (Exception ex) {}
    }
    public boolean toggleNotification(UUID uuid) {
        boolean newState = !isNotify(uuid);
        setNotify(uuid, newState);
        return newState;
    }
    public List<Mail> getUnreadMails(UUID uuid) {
        return getMails(uuid).stream()
                .filter(mail -> !mail.isExpired())
                .toList();
    }
}
