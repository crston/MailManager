package com.gmail.bobason01.cache;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerCache {

    private static volatile List<OfflinePlayer> cachedPlayers = List.of();
    private static final Map<String, OfflinePlayer> nameMap = new ConcurrentHashMap<>();
    private static final Map<UUID, OfflinePlayer> uuidMap = new ConcurrentHashMap<>();
    private static volatile long lastUpdate = 0L;

    private static final AtomicBoolean isRefreshing = new AtomicBoolean(false);

    /**
     * Refresh the player cache asynchronously. No-op if refreshed within interval.
     */
    public static void refresh(JavaPlugin plugin, long intervalMillis) {
        long now = System.currentTimeMillis();
        if (now - lastUpdate < intervalMillis || isRefreshing.get()) return;

        isRefreshing.set(true);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<OfflinePlayer> players = Arrays.stream(Bukkit.getOfflinePlayers())
                        .filter(p -> {
                            String name = p.getName();
                            return name != null && name.length() <= 16 && p.hasPlayedBefore();
                        })
                        .sorted(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)))
                        .toList();

                Map<String, OfflinePlayer> newNameMap = new ConcurrentHashMap<>();
                Map<UUID, OfflinePlayer> newUuidMap = new ConcurrentHashMap<>();

                for (OfflinePlayer p : players) {
                    String name = p.getName();
                    if (name != null) {
                        newNameMap.put(name.toLowerCase(Locale.ROOT), p);
                        newUuidMap.put(p.getUniqueId(), p);
                    }
                }

                nameMap.clear();
                nameMap.putAll(newNameMap);
                uuidMap.clear();
                uuidMap.putAll(newUuidMap);
                cachedPlayers = players;
                lastUpdate = System.currentTimeMillis();
            } finally {
                isRefreshing.set(false);
            }
        });
    }

    /**
     * Get the cached list of known OfflinePlayers (read-only).
     */
    public static List<OfflinePlayer> getCachedPlayers() {
        return cachedPlayers;
    }

    /**
     * Get player by UUID from cache.
     */
    public static OfflinePlayer getByUUID(UUID uuid) {
        if (uuid == null) return null;
        return uuidMap.get(uuid);
    }

    /**
     * Get player by name (case-insensitive).
     */
    public static OfflinePlayer getByName(String name) {
        if (name == null) return null;
        return nameMap.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Timestamp of last cache refresh.
     */
    public static long getLastUpdateTime() {
        return lastUpdate;
    }

    /**
     * Whether the cache is currently being refreshed.
     */
    public static boolean isRefreshing() {
        return isRefreshing.get();
    }
}
