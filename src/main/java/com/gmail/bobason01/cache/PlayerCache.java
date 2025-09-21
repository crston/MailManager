package com.gmail.bobason01.cache;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class PlayerCache {

    private static volatile List<OfflinePlayer> cachedPlayers = List.of();
    private static final Map<String, OfflinePlayer> nameMap = new ConcurrentHashMap<>();
    private static final Map<UUID, OfflinePlayer> uuidMap = new ConcurrentHashMap<>();

    private static final AtomicBoolean isRefreshing = new AtomicBoolean(false);

    public static void refresh(JavaPlugin plugin) {
        if (!isRefreshing.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<OfflinePlayer> players = Arrays.stream(Bukkit.getOfflinePlayers())
                        .filter(p -> {
                            String name = p.getName();
                            return name != null && !name.isEmpty() && name.length() <= 16;
                        })
                        .collect(Collectors.toList());

                Map<String, OfflinePlayer> newNameMap = new ConcurrentHashMap<>(players.size());
                Map<UUID, OfflinePlayer> newUuidMap = new ConcurrentHashMap<>(players.size());

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
            } finally {
                isRefreshing.set(false);
            }
        });
    }

    public static List<OfflinePlayer> getCachedPlayers() {
        return cachedPlayers;
    }

    public static OfflinePlayer getByUUID(UUID uuid) {
        if (uuid == null) return null;
        return uuidMap.get(uuid);
    }

    public static OfflinePlayer getByName(String name) {
        if (name == null) return null;
        return nameMap.get(name.toLowerCase(Locale.ROOT));
    }

    public static boolean isRefreshing() {
        return isRefreshing.get();
    }
}