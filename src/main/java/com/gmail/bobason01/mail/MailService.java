package com.gmail.bobason01.mail;

import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

public class MailService {

    private static final long SESSION_TIMEOUT = 5 * 60 * 1000L;
    private static final Map<UUID, MailSession> sessions = new ConcurrentHashMap<>();
    private static final ExecutorService sendExecutor =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static void init(Plugin plugin) {
        long interval = SESSION_TIMEOUT / 50L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            sessions.entrySet().removeIf(entry -> now - entry.getValue().lastAccess > SESSION_TIMEOUT);
        }, interval, interval);
    }

    public static void shutdown() {
        sendExecutor.shutdown();
    }

    public static void sendAll(Player sender, Plugin plugin) {
        UUID senderId = sender.getUniqueId();
        MailSession session = sessions.get(senderId);
        String lang = LangManager.getLanguage(senderId);

        if (session == null || session.items.isEmpty()) {
            sender.sendMessage(LangManager.get(lang, "mail.sendall.invalid"));
            return;
        }

        List<ItemStack> baseItems = cloneValidItems(session.items);
        if (baseItems.isEmpty()) {
            sender.sendMessage(LangManager.get(lang, "mail.sendall.no_item"));
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = buildExpireTime(now, session.time);
        Set<UUID> exclude = MailDataManager.getInstance().getExclude(senderId);

        CompletableFuture.runAsync(() -> {
            List<Mail> mailsToSend = PlayerCache.getCachedPlayers().parallelStream()
                    .map(OfflinePlayer::getUniqueId)
                    .filter(targetId -> !targetId.equals(senderId))
                    .filter(targetId -> !exclude.contains(targetId))
                    .filter(targetId -> !MailDataManager.getInstance().getBlacklist(targetId).contains(senderId))
                    .map(targetId -> new Mail(senderId, targetId, baseItems, now, expireAt))
                    .toList();

            for (Mail mail : mailsToSend) {
                MailDataManager.getInstance().addMail(mail);
            }

            int count = mailsToSend.size();
            Bukkit.getScheduler().runTask(plugin, () -> {
                sessions.remove(senderId);
                sender.sendMessage(LangManager.get(lang, "mail.sendall.success")
                        .replace("%count%", String.valueOf(count)));
            });
        }, sendExecutor);
    }

    public static void send(Player sender, Plugin plugin) {
        UUID senderId = sender.getUniqueId();
        String lang = LangManager.getLanguage(senderId);
        MailSession session = sessions.get(senderId);

        if (!isValidSession(session) || session.target == null) {
            sender.sendMessage(LangManager.get(lang, "mail.send.invalid"));
            return;
        }

        List<ItemStack> baseItems = cloneValidItems(session.items);
        if (baseItems.isEmpty()) {
            sender.sendMessage(LangManager.get(lang, "mail.send.no_item"));
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = buildExpireTime(now, session.time);

        Mail mail = new Mail(senderId, session.target, baseItems, now, expireAt);
        MailDataManager.getInstance().addMail(mail);
        sessions.remove(senderId);

        sender.sendMessage(LangManager.get(lang, "mail.send.success"));
    }

    private static MailSession getOrCreateSession(UUID playerId) {
        return sessions.compute(playerId, (k, v) -> {
            if (v == null) v = new MailSession();
            v.lastAccess = System.currentTimeMillis();
            return v;
        });
    }

    public static MailSession getSession(UUID playerId) {
        MailSession session = sessions.get(playerId);
        if (session != null) session.lastAccess = System.currentTimeMillis();
        return session;
    }

    public static void setTarget(UUID playerId, OfflinePlayer target) {
        getOrCreateSession(playerId).target = target.getUniqueId();
    }

    public static OfflinePlayer getTargetPlayer(UUID playerId) {
        MailSession session = getSession(playerId);
        UUID targetId = (session != null) ? session.target : null;
        return targetId != null ? PlayerCache.getByUUID(targetId) : null;
    }

    public static void setAttachedItems(UUID playerId, List<ItemStack> items) {
        getOrCreateSession(playerId).items = cloneValidItems(items);
    }

    public static List<ItemStack> getAttachedItems(UUID playerId) {
        MailSession session = getSession(playerId);
        return (session == null) ? new ArrayList<>() : cloneValidItems(session.items);
    }

    public static void clearAttached(UUID playerId) {
        MailSession session = getSession(playerId);
        if (session != null) session.items.clear();
    }

    public static void setTimeData(UUID playerId, Map<String, Integer> time) {
        getOrCreateSession(playerId).time = (time != null) ? new HashMap<>(time) : new HashMap<>();
    }

    public static Map<String, Integer> getTimeData(UUID playerId) {
        MailSession session = getSession(playerId);
        return (session != null && session.time != null) ? new HashMap<>(session.time) : new HashMap<>();
    }

    public static long getExpireTime(UUID playerId) {
        Map<String, Integer> time = getTimeData(playerId);
        LocalDateTime expire = buildExpireTime(LocalDateTime.now(), time);
        return expire.atZone(TimeZone.getDefault().toZoneId()).toInstant().toEpochMilli();
    }

    public static boolean isReadyToAttach(UUID playerId) {
        MailSession session = getSession(playerId);
        return session != null && (session.time != null && !session.time.isEmpty());
    }

    private static boolean isValidSession(MailSession session) {
        return session != null && session.items != null && !session.items.isEmpty();
    }

    private static LocalDateTime buildExpireTime(LocalDateTime base, Map<String, Integer> timeData) {
        if (timeData == null || timeData.isEmpty()) {
            return base.plusYears(100);
        }
        return base
                .plusYears(timeData.getOrDefault("year", 0))
                .plusMonths(timeData.getOrDefault("month", 0))
                .plusDays(timeData.getOrDefault("day", 0))
                .plusHours(timeData.getOrDefault("hour", 0))
                .plusMinutes(timeData.getOrDefault("minute", 0))
                .plusSeconds(timeData.getOrDefault("second", 0));
    }

    private static List<ItemStack> cloneValidItems(List<ItemStack> items) {
        List<ItemStack> clones = new ArrayList<>();
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    clones.add(item.clone());
                }
            }
        }
        return clones;
    }

    public static class MailSession {
        public List<ItemStack> items = new ArrayList<>();
        public Map<String, Integer> time = new HashMap<>();
        public UUID target;
        public long lastAccess = System.currentTimeMillis();
    }
}
