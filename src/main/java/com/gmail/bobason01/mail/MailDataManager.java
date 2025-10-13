package com.gmail.bobason01.mail;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.database.DatabaseType;
import com.gmail.bobason01.database.MailStorage;
import com.gmail.bobason01.database.datasource.DataSourceFactory;
import com.gmail.bobason01.database.impl.JdbcStorage;
import com.gmail.bobason01.database.impl.YamlStorage;
import com.gmail.bobason01.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class MailDataManager {

    private static final MailDataManager INSTANCE = new MailDataManager();
    public static MailDataManager getInstance() { return INSTANCE; }

    private MailStorage storage;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MailManager-DB");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MailManager-Flush");
        t.setDaemon(true);
        return t;
    });

    private final Map<UUID, List<Mail>> mailCache = new ConcurrentHashMap<>();
    private final Map<UUID, Mail> mailIdCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> notifyCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> blacklistCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> excludeCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> languageCache = new ConcurrentHashMap<>();
    private final Map<Integer, ItemStack[]> inventoryCache = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<MailStorage.MailRecord> pendingUpserts = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<MailStorage.MailRecord> pendingDeletes = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Runnable> pendingMetaWrites = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private volatile long lastFlushNanos = System.nanoTime();

    public void load(JavaPlugin plugin) {
        try {
            DatabaseType type = DatabaseType.valueOf(
                    MailManager.getInstance().getConfig()
                            .getString("database.type", "SQLITE").toUpperCase()
            );
            switch (type) {
                case MYSQL, SQLITE -> {
                    DataSource ds = DataSourceFactory.build(type);
                    storage = new JdbcStorage(ds, type == DatabaseType.MYSQL);
                }
                case YAML -> storage = new YamlStorage();
            }
            storage.connect();
            storage.ensureSchema();
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID u = p.getUniqueId();
                try {
                    List<Mail> mails = storage.loadMails(u);
                    mailCache.put(u, new CopyOnWriteArrayList<>(mails));
                    for (Mail m : mails) mailIdCache.put(m.getMailId(), m);
                    Boolean notify = storage.loadNotifySetting(u);
                    notifyCache.put(u, notify == null || notify);
                    Set<UUID> bl = storage.loadBlacklist(u);
                    blacklistCache.put(u, new HashSet<>(bl));
                    Set<UUID> ex = storage.loadExclude(u);
                    excludeCache.put(u, new HashSet<>(ex));
                    String lang = storage.loadPlayerLanguage(u);
                    if (lang != null) {
                        languageCache.put(u, lang);
                        LangManager.loadUserLanguage(u, lang);
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.WARNING, "[MailManager] Load failed for " + u, e);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MailManager] Initialization failed", e);
        }
    }

    public void unload() {
        try {
            for (Map.Entry<UUID, List<Mail>> entry : mailCache.entrySet()) {
                flushMails(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<UUID, Boolean> entry : notifyCache.entrySet()) {
                storage.saveNotifySetting(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<UUID, Set<UUID>> entry : blacklistCache.entrySet()) {
                storage.saveBlacklist(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<UUID, Set<UUID>> entry : excludeCache.entrySet()) {
                storage.saveExclude(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<UUID, String> entry : languageCache.entrySet()) {
                storage.savePlayerLanguage(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<Integer, ItemStack[]> entry : inventoryCache.entrySet()) {
                storage.saveInventory(entry.getKey(), entry.getValue());
            }
            drainAndFlush(true);
            dbExecutor.shutdown();
            scheduler.shutdown();
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) dbExecutor.shutdownNow();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow();
            if (storage != null) storage.disconnect();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[MailManager] Shutdown failed", e);
        }
    }

    public ItemStack[] getInventory(int id) {
        return inventoryCache.computeIfAbsent(id, k -> {
            try {
                ItemStack[] loaded = storage.loadInventory(id);
                return loaded != null ? loaded : new ItemStack[0];
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[MailManager] Load inventory failed for id " + id, e);
                return new ItemStack[0];
            }
        });
    }

    public void saveInventory(int id, ItemStack[] contents) {
        inventoryCache.put(id, contents);
        submitMeta(() -> {
            try {
                storage.saveInventory(id, contents);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void addMail(Mail mail) {
        mailCache.computeIfAbsent(mail.getReceiver(), k -> new CopyOnWriteArrayList<>()).add(mail);
        mailIdCache.put(mail.getMailId(), mail);
        queueUpsert(mail.getReceiver(), mail);
    }

    public void removeMail(Mail mail) {
        mailCache.computeIfAbsent(mail.getReceiver(), k -> new CopyOnWriteArrayList<>()).remove(mail);
        mailIdCache.remove(mail.getMailId());
        queueDelete(mail.getReceiver(), mail);
    }

    public void updateMail(Mail mail) {
        mailCache.computeIfAbsent(mail.getReceiver(), k -> new CopyOnWriteArrayList<>())
                .removeIf(m -> m.getMailId().equals(mail.getMailId()));
        mailCache.get(mail.getReceiver()).add(mail);
        mailIdCache.put(mail.getMailId(), mail);
        queueUpsert(mail.getReceiver(), mail);
    }

    public List<Mail> getMails(UUID receiver) {
        return mailCache.getOrDefault(receiver, Collections.emptyList());
    }

    public CompletableFuture<List<Mail>> getMailsAsync(UUID receiver) {
        return CompletableFuture.supplyAsync(() -> {
            List<Mail> list = mailCache.getOrDefault(receiver, Collections.emptyList());
            return new ArrayList<>(list);
        }, dbExecutor);
    }

    public List<Mail> getUnreadMails(UUID receiver) {
        List<Mail> src = mailCache.getOrDefault(receiver, Collections.emptyList());
        if (src.isEmpty()) return Collections.emptyList();
        List<Mail> out = new ArrayList<>(src.size());
        for (Mail m : src) if (m != null && !m.isExpired()) out.add(m);
        return out;
    }

    public Mail getMailById(UUID receiver, UUID mailId) {
        List<Mail> list = mailCache.get(receiver);
        if (list == null) return null;
        for (Mail m : list) if (m != null && m.getMailId().equals(mailId)) return m;
        return null;
    }

    public Mail getMailById(UUID mailId) {
        Mail m = mailIdCache.get(mailId);
        if (m != null) return m;
        for (List<Mail> list : mailCache.values()) for (Mail x : list) if (x != null && x.getMailId().equals(mailId)) return x;
        return null;
    }

    private void flushMails(UUID uuid, List<Mail> mails) throws Exception {
        for (Mail m : mails) mailIdCache.put(m.getMailId(), m);
        storage.batchInsertMails(mails.stream().map(m -> new MailStorage.MailRecord(uuid, m)).toList());
    }

    public void setNotify(UUID u, boolean enabled) {
        notifyCache.put(u, enabled);
        submitMeta(() -> {
            try {
                storage.saveNotifySetting(u, enabled);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean isNotify(UUID u) {
        return notifyCache.getOrDefault(u, true);
    }

    public boolean toggleNotification(UUID uuid) {
        boolean newState = !isNotify(uuid);
        setNotify(uuid, newState);
        return newState;
    }

    public Set<UUID> getBlacklist(UUID owner) {
        return blacklistCache.computeIfAbsent(owner, k -> new HashSet<>());
    }

    public void setBlacklist(UUID owner, Set<UUID> list) {
        blacklistCache.put(owner, new HashSet<>(list));
        submitMeta(() -> {
            try {
                storage.saveBlacklist(owner, list);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Set<UUID> getExclude(UUID owner) {
        return excludeCache.computeIfAbsent(owner, k -> new HashSet<>());
    }

    public void setExclude(UUID owner, Set<UUID> list) {
        excludeCache.put(owner, new HashSet<>(list));
        submitMeta(() -> {
            try {
                storage.saveExclude(owner, list);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean toggleExclude(UUID owner, UUID target) {
        Set<UUID> set = excludeCache.computeIfAbsent(owner, k -> new HashSet<>());
        final boolean nowExcluded;
        if (set.contains(target)) {
            set.remove(target);
            nowExcluded = false;
        } else {
            set.add(target);
            nowExcluded = true;
        }
        submitMeta(() -> {
            try {
                storage.saveExclude(owner, set);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return nowExcluded;
    }

    public void savePlayerLanguage(UUID u, String lang) {
        languageCache.put(u, lang);
        submitMeta(() -> {
            try {
                storage.savePlayerLanguage(u, lang);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public String getPlayerLanguage(UUID u) {
        return languageCache.get(u);
    }

    private void queueUpsert(UUID receiver, Mail mail) {
        pendingUpserts.add(new MailStorage.MailRecord(receiver, mail));
        scheduleFlush(false);
    }

    private void queueDelete(UUID receiver, Mail mail) {
        pendingDeletes.add(new MailStorage.MailRecord(receiver, mail));
        scheduleFlush(false);
    }

    private void submitMeta(Runnable r) {
        pendingMetaWrites.add(r);
        scheduleFlush(true);
    }

    private void scheduleFlush(boolean fast) {
        if (!flushScheduled.compareAndSet(false, true)) return;
        long delay = fast ? 10 : 40;
        long since = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastFlushNanos);
        if (since > 100) delay = 5;
        scheduler.schedule(this::drainAndFlushSafe, delay, TimeUnit.MILLISECONDS);
    }

    private void drainAndFlushSafe() {
        try { drainAndFlush(false); } finally { flushScheduled.set(false); lastFlushNanos = System.nanoTime(); }
    }

    private void drainAndFlush(boolean blocking) {
        List<MailStorage.MailRecord> ups = new ArrayList<>();
        List<MailStorage.MailRecord> dels = new ArrayList<>();
        Runnable r;
        while ((r = pendingMetaWrites.poll()) != null) {
            Runnable task = r;
            dbExecutor.submit(task);
        }
        MailStorage.MailRecord rec;
        while ((rec = pendingUpserts.poll()) != null) ups.add(rec);
        while ((rec = pendingDeletes.poll()) != null) dels.add(rec);
        if (!ups.isEmpty()) {
            Future<?> f = dbExecutor.submit(() -> {
                try {
                    storage.batchInsertMails(ups);
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.WARNING, "[MailManager] Async DB upsert failed", e);
                }
            });
            if (blocking) waitFuture(f);
        }
        if (!dels.isEmpty()) {
            Future<?> f = dbExecutor.submit(() -> {
                try {
                    storage.batchDeleteMails(dels);
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.WARNING, "[MailManager] Async DB delete failed", e);
                }
            });
            if (blocking) waitFuture(f);
        }
    }

    private void waitFuture(Future<?> f) {
        try { f.get(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
    }

    public void flushNow() {
        drainAndFlush(true);
    }

    public void forceReloadMails(UUID playerId) {
        try {
            List<Mail> old = mailCache.get(playerId);
            if (old != null) {
                for (Mail m : old) {
                    if (m != null) mailIdCache.remove(m.getMailId());
                }
            }
            List<Mail> mails = storage.loadMails(playerId);
            mailCache.put(playerId, new CopyOnWriteArrayList<>(mails));
            for (Mail m : mails) {
                if (m != null) mailIdCache.put(m.getMailId(), m);
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[MailManager] Force reload failed for " + playerId, e);
        }
    }
}
