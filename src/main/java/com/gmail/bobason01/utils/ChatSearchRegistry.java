package com.gmail.bobason01.utils;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class ChatSearchRegistry implements Listener {

    private static final long TTL_MILLIS = 2 * 60 * 1000;
    private static final Map<UUID, CallbackEntry> registry = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ChatSearchRegistry-Cleanup");
        t.setDaemon(true);
        return t;
    });

    static {
        cleaner.scheduleAtFixedRate(ChatSearchRegistry::cleanupExpired, TTL_MILLIS, TTL_MILLIS, TimeUnit.MILLISECONDS);
    }

    public static void register(Player player, ChatCallback callback) {
        registry.put(player.getUniqueId(), new CallbackEntry(callback, System.currentTimeMillis()));
    }

    public static ChatCallback consume(Player player) {
        CallbackEntry entry = registry.remove(player.getUniqueId());
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