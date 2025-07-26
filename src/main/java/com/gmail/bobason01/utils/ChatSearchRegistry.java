package com.gmail.bobason01.utils;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 플레이어의 채팅 입력을 일시적으로 가로채 특정 GUI나 기능에 연결할 수 있게 해주는 유틸.
 */
public class ChatSearchRegistry implements Listener {

    private static final long TTL_MILLIS = 2 * 60 * 1000;
    private static final Map<UUID, CallbackEntry> registry = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService cleaner = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("ChatSearchRegistry-Cleanup");
        return t;
    });

    static {
        cleaner.scheduleAtFixedRate(ChatSearchRegistry::cleanupExpired, TTL_MILLIS, TTL_MILLIS, TimeUnit.MILLISECONDS);
    }

    public static void register(Player player, ChatCallback callback) {
        registry.compute(player.getUniqueId(), (uuid, existing) -> new CallbackEntry(callback, System.currentTimeMillis()));
    }

    public static void unregister(UUID uuid) {
        registry.remove(uuid);
    }

    public static void handle(UUID uuid, String input) {
        CallbackEntry entry = registry.remove(uuid);
        if (entry != null && !entry.isExpired(System.currentTimeMillis())) {
            entry.callback.onChat(input);
        }
    }

    public static ChatCallback consume(Player player) {
        return consume(player.getUniqueId());
    }

    public static ChatCallback consume(UUID uuid) {
        CallbackEntry entry = registry.remove(uuid);
        return (entry != null && !entry.isExpired(System.currentTimeMillis())) ? entry.callback : null;
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        registry.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        registry.remove(event.getPlayer().getUniqueId());
    }

    private record CallbackEntry(ChatCallback callback, long timestamp) {
        boolean isExpired(long now) {
            return (now - timestamp) > TTL_MILLIS;
        }
    }

    @FunctionalInterface
    public interface ChatCallback {
        void onChat(String message);
    }
}
