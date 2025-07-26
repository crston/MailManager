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

    // 세션 유지 시간 (5분)
    private static final long SESSION_TIMEOUT = 5 * 60 * 1000L;

    // 플레이어별 우편 작성 세션
    private static final Map<UUID, MailSession> sessions = new ConcurrentHashMap<>();

    // 병렬 우편 발송을 위한 스레드 풀
    private static final ExecutorService sendExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    // 세션 정리 주기 등록
    public static void init(Plugin plugin) {
        long interval = SESSION_TIMEOUT / 50L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            sessions.entrySet().removeIf(entry -> now - entry.getValue().lastAccess > SESSION_TIMEOUT);
        }, interval, interval);
    }

    // 전체 플레이어에게 우편 발송
    public static void sendAll(Player sender, Plugin plugin) {
        final UUID senderId = sender.getUniqueId();
        final MailSession session = sessions.get(senderId);

        if (session == null || isInvalidItem(session.item)) {
            sender.sendMessage("§c[우편] 아이템이 첨부되지 않아 발송할 수 없습니다.");
            return;
        }

        final ItemStack baseItem = session.item.clone();
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime expireAt = buildExpireTime(now, session.time);
        final Set<UUID> exclude = MailDataManager.getInstance().getExclude(senderId);

        CompletableFuture.runAsync(() -> {
            List<Mail> mailsToSend = PlayerCache.getCachedPlayers().parallelStream()
                    .map(OfflinePlayer::getUniqueId)
                    .filter(targetId -> !targetId.equals(senderId)) // 자기 자신 제외
                    .filter(targetId -> !exclude.contains(targetId)) // 제외 대상 제외
                    .filter(targetId -> !MailDataManager.getInstance().getBlacklist(targetId).contains(senderId)) // 차단 대상 제외
                    .map(targetId -> new Mail(senderId, targetId, baseItem, now, expireAt))
                    .toList();

            for (Mail mail : mailsToSend) {
                MailDataManager.getInstance().addMail(mail.getReceiver(), mail);
            }

            int count = mailsToSend.size();

            Bukkit.getScheduler().runTask(plugin, () -> {
                sessions.remove(senderId);
                sender.sendMessage("§a[우편] 총 §f" + count + "명§a에게 우편을 보냈습니다.");
            });
        }, sendExecutor);
    }

    // 개별 플레이어에게 우편 발송
    public static void send(Player sender, Plugin plugin) {
        UUID senderId = sender.getUniqueId();
        MailSession session = sessions.get(senderId);

        if (!isValidSession(session) || session.target == null) {
            sender.sendMessage("§c[우편] 수신자 또는 아이템이 유효하지 않습니다.");
            return;
        }

        ItemStack item = session.item.clone();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = buildExpireTime(now, session.time);
        Mail mail = new Mail(senderId, session.target, item, now, expireAt);

        MailDataManager.getInstance().addMail(session.target, mail);
        sessions.remove(senderId);

        sender.sendMessage("§a[우편] 우편이 성공적으로 발송되었습니다.");
    }

    // 우편 수령 처리
    public static boolean claim(Player player, Mail mail) {
        UUID playerId = player.getUniqueId();
        if (!playerId.equals(mail.getReceiver())) return false;

        ItemStack item = mail.getItem();
        if (item == null || item.getType() == Material.AIR) return false;

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        if (!leftover.isEmpty()) return false;

        MailDataManager.getInstance().removeMail(playerId, mail);
        player.sendMessage("§a[우편] 아이템을 성공적으로 수령했습니다.");
        return true;
    }

    // ========== 세션 관리 ==========

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

    // 시간 정보로부터 만료 시각 계산
    private static LocalDateTime buildExpireTime(LocalDateTime base, Map<String, Integer> timeData) {
        if (timeData == null) return base.plusYears(100); // 기본 100년 후
        return base
                .plusYears(timeData.getOrDefault("year", 0))
                .plusMonths(timeData.getOrDefault("month", 0))
                .plusDays(timeData.getOrDefault("day", 0))
                .plusHours(timeData.getOrDefault("hour", 0))
                .plusMinutes(timeData.getOrDefault("minute", 0))
                .plusSeconds(timeData.getOrDefault("second", 0));
    }

    // ========== 세션 클래스 ==========

    public static class MailSession {
        public ItemStack item;
        public Map<String, Integer> time = new HashMap<>();
        public UUID target;
        public String context = "";
        public long lastAccess = System.currentTimeMillis();
    }

    // 타임 데이터 기반 만료 타임스탬프 반환 (ms)
    public static long getExpireTime(UUID playerId) {
        Map<String, Integer> time = getTimeData(playerId);
        LocalDateTime expire = buildExpireTime(LocalDateTime.now(), time);
        return expire.atZone(TimeZone.getDefault().toZoneId()).toInstant().toEpochMilli();
    }
}
