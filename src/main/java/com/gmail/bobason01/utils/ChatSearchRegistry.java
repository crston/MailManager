package com.gmail.bobason01.utils;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채팅 입력을 통해 특정 목적(검색 등)의 응답을 처리하는 유틸 클래스
 */
public class ChatSearchRegistry {

    private static final Map<UUID, ChatCallback> registry = new ConcurrentHashMap<>();

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
        if (callback != null) {
            callback.onChat(input);
        }
    }

    @FunctionalInterface
    public interface ChatCallback {
        void onChat(String message);
    }
}
