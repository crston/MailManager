package com.gmail.bobason01.cache;

import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlayerCache {

    private static final Set<UUID> knownUuids = ConcurrentHashMap.newKeySet();
    private static final CopyOnWriteArrayList<OfflinePlayer> cachedPlayers = new CopyOnWriteArrayList<>();

    private PlayerCache() {
    }

    public static List<OfflinePlayer> getCachedPlayers() {
        return new ArrayList<>(cachedPlayers);
    }

    public static OfflinePlayer getByUUID(UUID uuid) {
        if (uuid == null) return null;
        return Bukkit.getOfflinePlayer(uuid);
    }

    public static OfflinePlayer getByName(String name) {
        if (name == null || name.isBlank()) return null;
        String target = name.trim();

        for (OfflinePlayer p : cachedPlayers) {
            if (p == null) continue;
            String n = p.getName();
            if (n != null && n.equalsIgnoreCase(target)) return p;
        }

        Player online = Bukkit.getPlayerExact(target);
        if (online != null) return online;

        return null;
    }

    public static void add(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        if (uuid == null) return;

        if (knownUuids.add(uuid)) {
            cachedPlayers.add(player);
            sortCached();
        } else {
            boolean exists = false;
            for (OfflinePlayer p : cachedPlayers) {
                if (p != null && uuid.equals(p.getUniqueId())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                cachedPlayers.add(player);
                sortCached();
            }
        }
    }

    public static void remove(UUID uuid) {
        if (uuid == null) return;
        knownUuids.remove(uuid);
        cachedPlayers.removeIf(p -> p != null && uuid.equals(p.getUniqueId()));
    }

    public static void refresh(Plugin plugin) {
        MailDataManager.getInstance().getAllGlobalUUIDsAsync().thenAccept(globalSet -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                applyRefresh(globalSet);
            });
        });
    }

    private static void applyRefresh(Set<UUID> globalSet) {
        if (globalSet == null) globalSet = Collections.emptySet();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null) continue;
            UUID id = p.getUniqueId();
            if (id != null) globalSet = unionAdd(globalSet, id);
        }

        knownUuids.clear();
        knownUuids.addAll(globalSet);

        List<OfflinePlayer> newList = new ArrayList<>(knownUuids.size());
        for (UUID id : knownUuids) {
            if (id == null) continue;
            newList.add(Bukkit.getOfflinePlayer(id));
        }

        newList.removeIf(p -> p == null || p.getName() == null);

        newList.sort(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER));

        cachedPlayers.clear();
        cachedPlayers.addAll(newList);
    }

    private static Set<UUID> unionAdd(Set<UUID> base, UUID add) {
        if (base.contains(add)) return base;
        Set<UUID> copy = ConcurrentHashMap.newKeySet();
        copy.addAll(base);
        copy.add(add);
        return copy;
    }

    private static void sortCached() {
        List<OfflinePlayer> list = new ArrayList<>(cachedPlayers);
        list.removeIf(p -> p == null || p.getName() == null);
        list.sort(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER));
        cachedPlayers.clear();
        cachedPlayers.addAll(list);
    }
}
