package com.gmail.bobason01.mail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MailDataManager {

    private static final MailDataManager INSTANCE = new MailDataManager();
    public static MailDataManager getInstance() {
        return INSTANCE;
    }

    private static final String DATA_FILE_NAME = "data.json";
    private static final int SAVE_INITIAL_DELAY_SEC = 5;
    private static final int SAVE_INTERVAL_SEC = 20;

    private final Map<UUID, ConcurrentLinkedDeque<Mail>> mailMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> blacklistMap = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> notifyMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> excludeFromAllMap = new ConcurrentHashMap<>();

    private final BlockingQueue<Mail> queuedMails = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final Object fileLock = new Object();

    private File dataFile;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ItemStack.class, new MailSerializer.ItemStackAdapter())
            .registerTypeAdapter(LocalDateTime.class, new MailSerializer.LocalDateTimeAdapter())
            .create();

    public void load(JavaPlugin plugin) {
        dataFile = new File(plugin.getDataFolder(), DATA_FILE_NAME);
        loadFromDisk();

        scheduler.scheduleWithFixedDelay(() -> {
            if (dirty.compareAndSet(true, false)) {
                saveToDisk();
            }
        }, SAVE_INITIAL_DELAY_SEC, SAVE_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void unload() {
        saveToDisk();
        scheduler.shutdownNow();
    }

    // ============ 메일 ============

    public void addMail(UUID receiver, Mail mail) {
        mailMap.computeIfAbsent(receiver, k -> new ConcurrentLinkedDeque<>()).addLast(mail);
        dirty.set(true);
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

    public void queueMail(Mail mail) {
        queuedMails.add(mail);
    }

    public void flushQueuedMails() {
        if (queuedMails.isEmpty()) return;
        List<Mail> buffer = new ArrayList<>();
        queuedMails.drainTo(buffer);
        for (Mail mail : buffer) {
            if (mail != null && mail.getReceiver() != null) {
                addMail(mail.getReceiver(), mail);
            }
        }
    }

    // ============ 블랙리스트 ============

    public Set<UUID> getBlacklist(UUID uuid) {
        return blacklistMap.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public void setBlacklist(UUID uuid, Set<UUID> list) {
        if (!Objects.equals(blacklistMap.get(uuid), list)) {
            blacklistMap.put(uuid, list);
            dirty.set(true);
        }
    }

    public void toggleBlacklist(UUID owner, UUID target) {
        Set<UUID> blacklist = blacklistMap.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet());
        if (blacklist.contains(target)) {
            blacklist.remove(target);
        } else {
            blacklist.add(target);
        }
        dirty.set(true);
    }

    // ============ 알림 설정 ============

    public boolean isNotifyEnabled(UUID uuid) {
        return notifyMap.getOrDefault(uuid, true);
    }

    public void setNotify(UUID uuid, boolean value) {
        if (!Objects.equals(notifyMap.get(uuid), value)) {
            notifyMap.put(uuid, value);
            dirty.set(true);
        }
    }

    public boolean toggleNotification(UUID uuid) {
        boolean current = isNotifyEnabled(uuid);
        setNotify(uuid, !current);
        return current;
    }

    // ============ 전체 메일 제외 대상 ============

    public Set<UUID> getExclude(UUID uuid) {
        return excludeFromAllMap.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public void setExclude(UUID uuid, Set<UUID> excludes) {
        if (!Objects.equals(excludeFromAllMap.get(uuid), excludes)) {
            excludeFromAllMap.put(uuid, excludes);
            dirty.set(true);
        }
    }

    // ============ 저장 ============

    public void save() {
        saveToDisk();
    }

    private void saveToDisk() {
        synchronized (fileLock) {
            try (Writer writer = new FileWriter(dataFile)) {
                MailDataSnapshot snapshot = new MailDataSnapshot();
                snapshot.mails = mailMap;
                snapshot.blacklist = blacklistMap;
                snapshot.notify = notifyMap;
                snapshot.exclude = excludeFromAllMap;
                gson.toJson(snapshot, writer);
            } catch (Exception e) {
                Bukkit.getLogger().severe("[MailManager] Failed to save data: " + e.getMessage());
            }
        }
    }

    private void loadFromDisk() {
        synchronized (fileLock) {
            if (!dataFile.exists()) return;
            try (Reader reader = new FileReader(dataFile)) {
                MailDataSnapshot snapshot = gson.fromJson(reader, MailDataSnapshot.class);
                if (snapshot != null) {
                    if (snapshot.mails != null) {
                        mailMap.clear();
                        mailMap.putAll(snapshot.mails);
                    }
                    if (snapshot.blacklist != null) {
                        blacklistMap.clear();
                        blacklistMap.putAll(snapshot.blacklist);
                    }
                    if (snapshot.notify != null) {
                        notifyMap.clear();
                        notifyMap.putAll(snapshot.notify);
                    }
                    if (snapshot.exclude != null) {
                        excludeFromAllMap.clear();
                        excludeFromAllMap.putAll(snapshot.exclude);
                    }
                }
            } catch (Exception e) {
                Bukkit.getLogger().severe("[MailManager] Failed to load data: " + e.getMessage());
            }
        }
    }

    static class MailDataSnapshot {
        Map<UUID, ConcurrentLinkedDeque<Mail>> mails = new ConcurrentHashMap<>();
        Map<UUID, Set<UUID>> blacklist = new ConcurrentHashMap<>();
        Map<UUID, Boolean> notify = new ConcurrentHashMap<>();
        Map<UUID, Set<UUID>> exclude = new ConcurrentHashMap<>();
    }
}
