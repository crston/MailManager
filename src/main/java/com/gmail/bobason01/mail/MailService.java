package com.gmail.bobason01.mail;

import com.gmail.bobason01.cache.PlayerCache;
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

    private static final long SESSION_TIMEOUT = 5 * 60 * 1000L; // 5 minutes
    private static final Map<UUID, MailSession> sessions = new ConcurrentHashMap<>();
    private static final ExecutorService sendExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static void init(Plugin plugin) {
        long interval = SESSION_TIMEOUT / 50L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            sessions.entrySet().removeIf(entry -> now - entry.getValue().lastAccess > SESSION_TIMEOUT);
        }, interval, interval);
    }

    public static void sendAll(Player sender, Plugin plugin) {
        final UUID senderId = sender.getUniqueId();
        final MailSession session = sessions.get(senderId);

        if (session == null || isInvalidItem(session.item)) {
            sender.sendMessage("§cCannot send mail: No item attached.");
            return;
        }

        final ItemStack baseItem = session.item.clone();
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime expireAt = buildExpireTime(now, session.time);
        final Set<UUID> exclude = MailDataManager.getInstance().getExclude(senderId);

        CompletableFuture.runAsync(() -> {
            List<Mail> mailsToSend = PlayerCache.getCachedPlayers().parallelStream()
                    .map(OfflinePlayer::getUniqueId)
                    .filter(targetId -> !targetId.equals(senderId))
                    .filter(targetId -> !exclude.contains(targetId))
                    .filter(targetId -> !MailDataManager.getInstance().getBlacklist(targetId).contains(senderId))
                    .map(targetId -> new Mail(senderId, targetId, baseItem, now, expireAt))
                    .toList();

            for (Mail mail : mailsToSend) {
                MailDataManager.getInstance().addMail(mail.getReceiver(), mail);
            }

            int count = mailsToSend.size();

            Bukkit.getScheduler().runTask(plugin, () -> {
                sessions.remove(senderId);
                sender.sendMessage("§aMail sent to §f" + count + " §arecipients.");
            });
        }, sendExecutor);
    }

    public static void send(Player sender, Plugin plugin) {
        UUID senderId = sender.getUniqueId();
        MailSession session = sessions.get(senderId);

        if (!isValidSession(session) || session.target == null) {
            sender.sendMessage("§cCannot send mail: Invalid target or item.");
            return;
        }

        ItemStack item = session.item.clone();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = buildExpireTime(now, session.time);
        Mail mail = new Mail(senderId, session.target, item, now, expireAt);

        MailDataManager.getInstance().addMail(session.target, mail);
        sessions.remove(senderId);

        sender.sendMessage("§aMail successfully sent.");
    }

    public static boolean claim(Player player, Mail mail) {
        UUID playerId = player.getUniqueId();
        if (!playerId.equals(mail.getReceiver())) return false;

        ItemStack item = mail.getItem();
        if (item == null || item.getType() == Material.AIR) return false;

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        if (!leftover.isEmpty()) return false;

        MailDataManager.getInstance().removeMail(playerId, mail);
        player.sendMessage("§aMail item received successfully.");
        return true;
    }

    // ========== Session Management ==========

    public static void setSession(UUID playerId, MailSession session) {
        sessions.put(playerId, session);
    }

    public static MailSession getSession(UUID playerId) {
        MailSession session = sessions.get(playerId);
        if (session != null) session.lastAccess = System.currentTimeMillis();
        return session;
    }

    public static void setTarget(UUID playerId, OfflinePlayer target) {
        getOrCreateSession(playerId).target = target.getUniqueId();
    }

    public static UUID getTarget(UUID playerId) {
        MailSession session = getSession(playerId);
        return session != null ? session.target : null;
    }

    public static OfflinePlayer getTargetPlayer(UUID playerId) {
        UUID targetId = getTarget(playerId);
        return targetId != null ? PlayerCache.getByUUID(targetId) : null;
    }

    public static void setAttachedItem(UUID playerId, ItemStack item) {
        getOrCreateSession(playerId).item = isInvalidItem(item) ? null : item.clone();
    }

    public static ItemStack getAttachedItem(UUID playerId) {
        MailSession session = getSession(playerId);
        return (session != null && session.item != null) ? session.item.clone() : null;
    }

    public static void setTimeData(UUID playerId, Map<String, Integer> time) {
        getOrCreateSession(playerId).time = (time != null) ? new HashMap<>(time) : new HashMap<>();
    }

    public static Map<String, Integer> getTimeData(UUID playerId) {
        MailSession session = getSession(playerId);
        return (session != null && session.time != null) ? new HashMap<>(session.time) : new HashMap<>();
    }

    public static void setContext(UUID playerId, String context) {
        getOrCreateSession(playerId).context = context;
    }

    public static String getContext(UUID playerId) {
        MailSession session = getSession(playerId);
        return (session != null && session.context != null) ? session.context : "";
    }

    private static MailSession getOrCreateSession(UUID playerId) {
        return sessions.compute(playerId, (k, v) -> {
            if (v == null) v = new MailSession();
            v.lastAccess = System.currentTimeMillis();
            return v;
        });
    }

    private static boolean isValidSession(MailSession session) {
        return session != null && session.item != null && session.item.getType() != Material.AIR;
    }

    private static boolean isInvalidItem(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private static LocalDateTime buildExpireTime(LocalDateTime base, Map<String, Integer> timeData) {
        if (timeData == null) return base.plusYears(100);
        return base
                .plusYears(timeData.getOrDefault("year", 0))
                .plusMonths(timeData.getOrDefault("month", 0))
                .plusDays(timeData.getOrDefault("day", 0))
                .plusHours(timeData.getOrDefault("hour", 0))
                .plusMinutes(timeData.getOrDefault("minute", 0))
                .plusSeconds(timeData.getOrDefault("second", 0));
    }

    // ========== Session Structure ==========

    public static class MailSession {
        public ItemStack item;
        public Map<String, Integer> time = new HashMap<>();
        public UUID target;
        public String context = "";
        public long lastAccess = System.currentTimeMillis();
    }

    public static long getExpireTime(UUID playerId) {
        Map<String, Integer> time = getTimeData(playerId);
        LocalDateTime expire = buildExpireTime(LocalDateTime.now(), time);
        return expire.atZone(TimeZone.getDefault().toZoneId()).toInstant().toEpochMilli();
    }
}
