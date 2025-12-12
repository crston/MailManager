package com.gmail.bobason01.mail;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.database.DatabaseType;
import com.gmail.bobason01.database.MailStorage;
import com.gmail.bobason01.database.datasource.DataSourceFactory;
import com.gmail.bobason01.database.impl.JdbcStorage;
import com.gmail.bobason01.database.impl.MySqlStorage;
import com.gmail.bobason01.database.impl.YamlStorage;
import com.gmail.bobason01.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
                case MYSQL -> {
                    DataSource ds = DataSourceFactory.build(type);
                    storage = new MySqlStorage(ds);
                }
                case SQLITE -> {
                    DataSource ds = DataSourceFactory.build(type);
                    storage = new JdbcStorage(ds, false);
                }
                case YAML -> storage = new YamlStorage();
            }

            if (storage == null) {
                throw new IllegalStateException("MailStorage implementation is null");
            }

            storage.connect();
            storage.ensureSchema();

            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID u = p.getUniqueId();
                try {
                    updateGlobalPlayerInfo(u, p.getName()); // 접속 중인 플레이어 정보 갱신

                    List<Mail> mails = storage.loadMails(u);
                    mailCache.put(u, new CopyOnWriteArrayList<>(mails));
                    for (Mail m : mails) {
                        if (m != null) mailIdCache.put(m.getMailId(), m);
                    }

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
            storage = null;
        }

        if (storage == null) {
            plugin.getLogger().severe("[MailManager] Storage not available. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }

    public void unload() {
        try {
            if (storage != null) {

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
            }

            dbExecutor.shutdown();
            scheduler.shutdown();

            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) dbExecutor.shutdownNow();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow();

            if (storage != null) storage.disconnect();

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[MailManager] Shutdown failed", e);
        } finally {
            mailCache.clear();
            mailIdCache.clear();
            notifyCache.clear();
            blacklistCache.clear();
            excludeCache.clear();
            languageCache.clear();
            inventoryCache.clear();
        }
    }

    // --- Global Player Info Methods ---

    public void updateGlobalPlayerInfo(UUID uuid, String name) {
        if (storage == null) return;
        dbExecutor.submit(() -> {
            try {
                storage.updateGlobalPlayer(uuid, name);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<UUID> getGlobalUUID(String name) {
        if (storage == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return storage.lookupGlobalUUID(name);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }, dbExecutor);
    }

    public String getGlobalName(UUID uuid) {
        if (uuid == null) return "Unknown";

        // 1. 로컬 캐시/Bukkit 확인 (가장 빠름)
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (op.getName() != null) return op.getName();

        // 2. DB 조회 (GUI 타이틀 등에서 필요 시 동기 호출)
        if (storage != null) {
            try {
                String name = storage.lookupGlobalName(uuid);
                if (name != null) return name;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "Unknown";
    }

    // --- End Global Player Info Methods ---

    // [추가됨] 플레이어 메일 초기화
    public void resetPlayerMails(UUID receiver) {
        // 1. 캐시 정리
        List<Mail> removedMails = mailCache.remove(receiver);
        if (removedMails != null) {
            for (Mail mail : removedMails) {
                if (mail != null) {
                    mailIdCache.remove(mail.getMailId());
                }
            }
        }

        // 2. DB 삭제 (비동기)
        if (storage != null) {
            dbExecutor.submit(() -> {
                try {
                    storage.deletePlayerMails(receiver);
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.SEVERE, "[MailManager] Failed to reset mails for " + receiver, e);
                }
            });
        }
    }

    public ItemStack[] getInventory(int id) {
        if (storage == null) {
            return inventoryCache.getOrDefault(id, new ItemStack[0]);
        }
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
        if (storage == null) return;

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
        return CompletableFuture.supplyAsync(() -> new ArrayList<>(
                mailCache.getOrDefault(receiver, Collections.emptyList())
        ), dbExecutor);
    }

    public List<Mail> getUnreadMails(UUID receiver) {
        List<Mail> src = mailCache.getOrDefault(receiver, Collections.emptyList());
        if (src.isEmpty()) return Collections.emptyList();

        List<Mail> out = new ArrayList<>(src.size());
        for (Mail m : src) {
            if (m != null && !m.isExpired()) out.add(m);
        }
        return out;
    }

    public Mail getMailById(UUID receiver, UUID mailId) {
        List<Mail> list = mailCache.get(receiver);
        if (list == null) return null;

        for (Mail m : list) {
            if (m != null && m.getMailId().equals(mailId)) return m;
        }
        return null;
    }

    public Mail getMailById(UUID mailId) {
        Mail m = mailIdCache.get(mailId);
        if (m != null) return m;

        for (List<Mail> list : mailCache.values()) {
            for (Mail mail : list) {
                if (mail != null && mail.getMailId().equals(mailId)) return mail;
            }
        }
        return null;
    }

    private void flushMails(UUID uuid, List<Mail> mails) throws Exception {
        if (storage == null) return;

        for (Mail m : mails) {
            if (m != null) mailIdCache.put(m.getMailId(), m);
        }

        storage.batchInsertMails(
                mails.stream()
                        .filter(Objects::nonNull)
                        .map(m -> new MailStorage.MailRecord(uuid, m))
                        .toList()
        );
    }

    public void setNotify(UUID u, boolean enabled) {
        notifyCache.put(u, enabled);
        if (storage == null) return;

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
        if (storage == null) return;

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
        if (storage == null) return;

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
        boolean now;

        if (set.contains(target)) {
            set.remove(target);
            now = false;
        } else {
            set.add(target);
            now = true;
        }

        if (storage == null) return now;

        submitMeta(() -> {
            try {
                storage.saveExclude(owner, set);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return now;
    }

    public void savePlayerLanguage(UUID u, String lang) {
        languageCache.put(u, lang);
        if (storage == null) return;

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
        if (storage == null) return;

        pendingUpserts.add(new MailStorage.MailRecord(receiver, mail));
        scheduleFlush(false);
    }

    private void queueDelete(UUID receiver, Mail mail) {
        if (storage == null) return;

        pendingDeletes.add(new MailStorage.MailRecord(receiver, mail));
        scheduleFlush(false);
    }

    private void submitMeta(Runnable r) {
        if (storage == null) return;

        pendingMetaWrites.add(r);
        scheduleFlush(true);
    }

    private void scheduleFlush(boolean fast) {
        if (storage == null) return;

        if (!flushScheduled.compareAndSet(false, true)) return;

        long delay = fast ? 10 : 40;
        long since = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastFlushNanos);

        if (since > 100) delay = 5;

        scheduler.schedule(this::drainAndFlushSafe, delay, TimeUnit.MILLISECONDS);
    }

    private void drainAndFlushSafe() {
        try {
            drainAndFlush(false);
        } finally {
            flushScheduled.set(false);
            lastFlushNanos = System.nanoTime();
        }
    }

    private void drainAndFlush(boolean blocking) {
        if (storage == null) {
            pendingMetaWrites.clear();
            pendingUpserts.clear();
            pendingDeletes.clear();
            return;
        }

        List<MailStorage.MailRecord> ups = new ArrayList<>();
        List<MailStorage.MailRecord> dels = new ArrayList<>();

        Runnable r;
        while ((r = pendingMetaWrites.poll()) != null) {
            dbExecutor.submit(r);
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
        try {
            f.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    public void flushNow() {
        drainAndFlush(true);
    }

    public void forceReloadMails(UUID id) {
        if (storage == null) {
            return;
        }
        try {
            List<Mail> old = mailCache.get(id);
            if (old != null) {
                for (Mail m : old) {
                    if (m != null) mailIdCache.remove(m.getMailId());
                }
            }

            List<Mail> mails = storage.loadMails(id);
            mailCache.put(id, new CopyOnWriteArrayList<>(mails));

            for (Mail m : mails) {
                if (m != null) mailIdCache.put(m.getMailId(), m);
            }

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[MailManager] Force reload failed for " + id, e);
        }
    }
}