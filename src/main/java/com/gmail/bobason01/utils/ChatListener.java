package com.gmail.bobason01.utils;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ChatListener implements Listener {

    private static final Map<UUID, Consumer<String>> waitingForTarget = new HashMap<>();
    private static final Map<UUID, Consumer<String>> waitingForTime = new HashMap<>();

    public static void setPlayerWaitingForTarget(Player player, Consumer<String> callback) {
        waitingForTarget.put(player.getUniqueId(), callback);
    }

    public static void setPlayerWaitingForTime(Player player, Consumer<String> callback) {
        waitingForTime.put(player.getUniqueId(), callback);
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (waitingForTarget.containsKey(uuid)) {
            event.setCancelled(true);
            Consumer<String> callback = waitingForTarget.remove(uuid);
            String targetName = event.getMessage();
            if (targetName != null && !targetName.isEmpty()) {
                callback.accept(targetName);
            } else {
                player.sendMessage(LangUtil.get(uuid, "message.target.invalid"));
            }

        }
        if (waitingForTime.containsKey(uuid)) {
            event.setCancelled(true);
            Consumer<String> callback = waitingForTime.remove(uuid);
            String time = event.getMessage();
            if (time != null && !time.isEmpty()) {
                callback.accept(time);
            } else {
                player.sendMessage(LangUtil.get(uuid, "message.time.invalid"));
            }
        }
    }
}