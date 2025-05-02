package com.gmail.bobason01.utils;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ChatSearchRegistry implements Listener {

    private static final Map<UUID, Consumer<String>> pending = new HashMap<>();

    public static void registerListener(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new ChatSearchRegistry(), plugin);
    }

    public static void startSearch(UUID uuid, Consumer<String> callback) {
        pending.put(uuid, callback);
    }

    public static boolean isSearching(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public static void cancel(UUID uuid) {
        pending.remove(uuid);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!pending.containsKey(uuid)) return;

        event.setCancelled(true);
        Consumer<String> callback = pending.remove(uuid);
        if (callback != null) {
            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(ChatSearchRegistry.class),
                    () -> callback.accept(event.getMessage()));
        }
    }
}