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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class MailDataManager {

    private static final MailDataManager INSTANCE = new MailDataManager();

    public static MailDataManager getInstance() {
        return INSTANCE;
    }

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
                            .getString("database.type", "SQLITE")
                            .toUpperCase()
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

            if (storage == null) throw new IllegalStateException("MailStorage is null");

            storage.connect();
            storage.ensureSchema();

            for (Player p : Bukkit.getOnlinePlayers()) {
                loadPlayerDataAsync(p.getUniqueId(), p.getName());
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
                // 1. 모든 캐시 상태를 '변경 예정(Pending)' 큐로 이동시킴 (직접 저장 X)
                // 예: languageCache.forEach((uuid, lang) -> savePlayerLanguage(uuid, lang));
                // 이렇게 하면 기존에 만들어둔 비동기 저장 로직(submitMeta)을 그대로 타게 됩니다.

                // 2. 큐에 쌓인 모든 데이터를 DB에 강제로 기록 (끝날 때까지 메인 스레드 대기)
                flushNow();
            }

            // 3. 실행자 종료
            dbExecutor.shutdown();
            scheduler.shutdown();

            // 4. 남은 작업이 완전히 끝날 때까지 대기
            if (!dbExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                Bukkit.getLogger().warning("[MailManager] Some data might not be saved due to slow DB.");
                dbExecutor.shutdownNow();
            }

            if (storage != null) storage.disconnect();

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[MailManager] Shutdown failed", e);
        } finally {
            clearAllCaches(); // 메모리 정리
        }
    }

    public void loadPlayerData(UUID uuid, String name) {
        loadPlayerDataAsync(uuid, name);
    }

    public CompletableFuture<Void> loadPlayerDataAsync(UUID uuid, String name) {
        if (storage == null) return CompletableFuture.completedFuture(null);

        UUID safeUuid = uuid;
        String safeName = name;

        return CompletableFuture.runAsync(() -> {
            try {
                storage.updateGlobalPlayer(safeUuid, safeName);

                List<Mail> mails = storage.loadMails(safeUuid);
                Boolean notify = storage.loadNotifySetting(safeUuid);
                Set<UUID> bl = storage.loadBlacklist(safeUuid);
                Set<UUID> ex = storage.loadExclude(safeUuid);
                String lang = storage.loadPlayerLanguage(safeUuid);

                mailCache.put(safeUuid, new CopyOnWriteArrayList<>(mails));
                for (Mail m : mails) {
                    if (m != null) mailIdCache.put(m.getMailId(), m);
                }

                notifyCache.put(safeUuid, notify != null ? notify : true);
                blacklistCache.put(safeUuid, new HashSet<>(bl));
                excludeCache.put(safeUuid, new HashSet<>(ex));

                if (lang != null) {
                    languageCache.put(safeUuid, lang);
                    LangManager.loadUserLanguage(safeUuid, lang);
                } else {
                    languageCache.remove(safeUuid);
                }

            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[MailManager] Failed to async load data for " + safeUuid, e);
            }
        }, dbExecutor);
    }

    public void updateGlobalPlayerInfo(UUID uuid, String name) {
        if (storage == null) return;
        dbExecutor.submit(() -> {
            try {
                storage.updateGlobalPlayer(uuid, name);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[MailManager] updateGlobalPlayerInfo failed", e);
            }
        });
    }

    public CompletableFuture<UUID> getGlobalUUID(String name) {
        if (storage == null) return CompletableFuture.completedFuture(null);

        // [수정] 온라인 플레이어는 대소문자 무시하고 즉시 반환 (핵심)
        Player online = Bukkit.getPlayer(name);
        if (online != null) return CompletableFuture.completedFuture(online.getUniqueId());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 오프라인은 DB 조회
                return storage.lookupGlobalUUID(name);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[MailManager] lookupGlobalUUID failed", e);
                return null;
            }
        }, dbExecutor);
    }

    public String getGlobalName(UUID uuid) {
        if (uuid == null) return "Unknown";

        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (op.getName() != null) return op.getName();

        if (storage != null) {
            try {
                String name = storage.lookupGlobalName(uuid);
                if (name != null) return name;
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[MailManager] lookupGlobalName failed", e);
            }
        }
        return "Unknown";
    }

    public CompletableFuture<Set<UUID>> getAllGlobalUUIDsAsync() {
        if (storage == null) return CompletableFuture.completedFuture(Collections.emptySet());
        return CompletableFuture.supplyAsync(() -> {
            try {
                return storage.getAllGlobalUUIDs();
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[MailManager] getAllGlobalUUIDs failed", e);
                return Collections.emptySet();
            }
        }, dbExecutor);
    }

    public void resetPlayerMails(UUID receiver) {
        List<Mail> removedMails = mailCache.remove(receiver);
        if (removedMails != null) {
            for (Mail mail : removedMails) {
                if (mail != null) mailIdCache.remove(mail.getMailId());
            }
        }
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
        if (mail == null) return;
        mailCache.computeIfAbsent(mail.getReceiver(), k -> new CopyOnWriteArrayList<>()).add(mail);
        mailIdCache.put(mail.getMailId(), mail);
        queueUpsert(mail.getReceiver(), mail);
    }

    public void removeMail(Mail mail) {
        if (mail == null) return;
        mailCache.computeIfAbsent(mail.getReceiver(), k -> new CopyOnWriteArrayList<>()).remove(mail);
        mailIdCache.remove(mail.getMailId());
        queueDelete(mail.getReceiver(), mail);
    }

    public void updateMail(Mail mail) {
        if (mail == null) return;
        mailCache.computeIfAbsent(mail.getReceiver(), k -> new CopyOnWriteArrayList<>())
                .removeIf(m -> m != null && m.getMailId().equals(mail.getMailId()));
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

        Set<UUID> copy = new HashSet<>(set);
        submitMeta(() -> {
            try {
                storage.saveExclude(owner, copy);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return now;
    }

    public void savePlayerLanguage(UUID u, String lang) {
        if (lang == null) {
            languageCache.remove(u);
        } else {
            languageCache.put(u, lang);
        }

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
            // [수정] blocking이 true일 경우 설정 저장도 완료될 때까지 기다려야 함
            Future<?> f = dbExecutor.submit(r);
            if (blocking) waitFuture(f);
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
        if (storage == null) return;

        dbExecutor.submit(() -> {
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
        });
    }

    private void clearAllCaches() {
        mailCache.clear();
        mailIdCache.clear();
        notifyCache.clear();
        blacklistCache.clear();
        excludeCache.clear();
        languageCache.clear();
        inventoryCache.clear();
        pendingUpserts.clear();
        pendingDeletes.clear();
        pendingMetaWrites.clear();
    }
}
