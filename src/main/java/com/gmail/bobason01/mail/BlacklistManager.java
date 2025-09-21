package com.gmail.bobason01.mail;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlacklistManager {

    private static final Map<UUID, Set<UUID>> blacklistMap = new ConcurrentHashMap<>();

    private BlacklistManager() {}

    public static void add(UUID owner, UUID target) {
        getOrCreate(owner).add(target);
    }

    public static void remove(UUID owner, UUID target) {
        Set<UUID> set = blacklistMap.get(owner);
        if (set != null) {
            set.remove(target);
            if (set.isEmpty()) {
                blacklistMap.remove(owner);
            }
        }
    }

    public static void toggle(UUID owner, UUID target) {
        if (isBlocked(owner, target)) {
            remove(owner, target);
        } else {
            add(owner, target);
        }
    }

    public static boolean isBlocked(UUID owner, UUID target) {
        return blacklistMap.getOrDefault(owner, Collections.emptySet()).contains(target);
    }

    public static Set<UUID> getBlacklist(UUID owner) {
        return Collections.unmodifiableSet(blacklistMap.getOrDefault(owner, Collections.emptySet()));
    }

    public static boolean isEmpty(UUID owner) {
        Set<UUID> list = blacklistMap.get(owner);
        return list == null || list.isEmpty();
    }

    public static void clear(UUID owner) {
        blacklistMap.remove(owner);
    }

    public static void clearAll() {
        blacklistMap.clear();
    }

    private static Set<UUID> getOrCreate(UUID owner) {
        return blacklistMap.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet());
    }
}