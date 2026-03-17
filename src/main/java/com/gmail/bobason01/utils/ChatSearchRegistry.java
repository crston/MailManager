package com.gmail.bobason01.utils;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatSearchRegistry implements Listener {

    private static final Map<UUID, ChatCallback> registry = new ConcurrentHashMap<>();

    public static void register(Player player, ChatCallback callback) {
        registry.put(player.getUniqueId(), callback);
    }

    public static ChatCallback consume(Player player) {
        return registry.remove(player.getUniqueId());
    }

    public static boolean isSearching(Player player) {
        return registry.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        registry.remove(e.getPlayer().getUniqueId());
    }

    @FunctionalInterface
    public interface ChatCallback {
        void onChat(String message);
    }
}