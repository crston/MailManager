package com.gmail.bobason01.utils;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatSearchRegistry {

    private static final Map<UUID, ChatCallback> registry = new HashMap<>();

    public static void register(Player player, ChatCallback callback) {
        registry.put(player.getUniqueId(), callback);
    }

    public static void unregister(UUID uuid) {
        registry.remove(uuid);
    }

    public static boolean has(UUID uuid) {
        return registry.containsKey(uuid);
    }

    public static void handle(UUID uuid, String input) {
        ChatCallback callback = registry.remove(uuid);
        if (callback != null) callback.onChat(input);
    }

    public interface ChatCallback {
        void onChat(String message);
    }
}
