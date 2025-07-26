package com.gmail.bobason01.utils;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatSearchRegistry.ChatCallback callback = ChatSearchRegistry.consume(player);
        if (callback != null) {
            event.setCancelled(true);
            callback.onChat(event.getMessage());
        }
    }
}