package com.gmail.bobason01.mail;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.database.DatabaseType;
import com.gmail.bobason01.database.MailStorage;
import com.gmail.bobason01.database.datasource.DataSourceFactory;
import com.gmail.bobason01.database.impl.JdbcStorage;
import com.gmail.bobason01.lang.LangManager;
import org.bukkit.Bukkit;
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
            DatabaseType type = DatabaseType.valueOf(
                    MailManager.getInstance().getConfig()
                            .getString("database.type", "SQLITE").toUpperCase()
            );
            if (type == DatabaseType.YAML) {
                throw new UnsupportedOperationException("YAML storage not optimized, use JDBC");
            } else {
                DataSource ds = DataSourceFactory.build(type);
                storage = new JdbcStorage(ds, type == DatabaseType.MYSQL);
            }
            storage.connect();
            storage.ensureSchema();
            loadAll();
            int interval = MailManager.getInstance().getConfig().getInt("flush.intervalTicks", 40);
            if (started.compareAndSet(false, true)) {
                taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                        MailManager.getInstance(),
                        this::flush,
                        interval,
                        interval
                );
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MailManager] Initialization failed", e);
        }
    }

    public void unload() {
        try {
            if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
            flush();
            if (storage != null) storage.disconnect();
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
        while ((rec = insertQueue.poll()) != null) ins.add(rec);
        List<MailStorage.MailRecord> del = new ArrayList<>();
        while ((rec = deleteQueue.poll()) != null) del.add(rec);
        try {
            if (!ins.isEmpty()) storage.batchInsertMails(ins);
            if (!del.isEmpty()) storage.batchDeleteMails(del);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[MailManager] Flush failed", e);
        }
    }

    public void addMail(Mail mail) {
        Deque<Mail> dq = mailMap.get(mail.getReceiver());
        if (dq == null) {
            dq = new ArrayDeque<>();
            mailMap.put(mail.getReceiver(), dq);
        }
        dq.add(mail);
        insertQueue.add(new MailStorage.MailRecord(mail.getReceiver(), mail));
    }

    public void removeMail(Mail mail) {
        Deque<Mail> dq = mailMap.get(mail.getReceiver());
        if (dq != null) {
            Iterator<Mail> it = dq.iterator();
            while (it.hasNext()) {
                Mail m = it.next();
                if (m.getMailId().equals(mail.getMailId())) {
                    it.remove();
                    break;
                }
            }
        }
        deleteQueue.add(new MailStorage.MailRecord(mail.getReceiver(), mail));
    }

    public void updateMail(Mail mail) {
        Deque<Mail> dq = mailMap.get(mail.getReceiver());
        if (dq == null) {
            dq = new ArrayDeque<>();
            mailMap.put(mail.getReceiver(), dq);
        }
        Iterator<Mail> it = dq.iterator();
        while (it.hasNext()) {
            Mail m = it.next();
            if (m.getMailId().equals(mail.getMailId())) {
                it.remove();
                break;
            }
        }
        dq.add(mail);
        insertQueue.add(new MailStorage.MailRecord(mail.getReceiver(), mail));
    }

    public List<Mail> getMails(UUID u) {
        Deque<Mail> dq = mailMap.get(u);
        if (dq == null || dq.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(dq);
    }

    public void setNotify(UUID u, boolean e) {
        notifyMap.put(u, e);
        try { storage.saveNotifySetting(u, e); } catch (Exception ignored) {}
    }

    public boolean isNotify(UUID u) {
        Boolean v = notifyMap.get(u);
        return v == null || v;
    }

    public void setBlacklist(UUID o, Set<UUID> l) {
        blacklistMap.put(o, l);
        try { storage.saveBlacklist(o, l); } catch (Exception ignored) {}
    }

    public Set<UUID> getBlacklist(UUID o) {
        Set<UUID> s = blacklistMap.get(o);
        if (s == null) return Collections.emptySet();
        return s;
    }

    public void setExclude(UUID u, Set<UUID> l) {
        excludeMap.put(u, l);
        try { storage.saveExclude(u, l); } catch (Exception ignored) {}
    }

    public Set<UUID> getExclude(UUID u) {
        Set<UUID> s = excludeMap.get(u);
        if (s == null) return Collections.emptySet();
        return s;
    }

    public void savePlayerLanguage(UUID u, String l) {
        try { storage.savePlayerLanguage(u, l); } catch (Exception ignored) {}
    }

    public boolean toggleNotification(UUID uuid) {
        boolean newState = !isNotify(uuid);
        setNotify(uuid, newState);
        return newState;
    }

    public List<Mail> getUnreadMails(UUID uuid) {
        List<Mail> list = getMails(uuid);
        List<Mail> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Mail m = list.get(i);
            if (!m.isExpired()) out.add(m);
        }
        return out;
    }
}
