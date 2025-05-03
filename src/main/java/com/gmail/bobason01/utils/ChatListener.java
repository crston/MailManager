package com.gmail.bobason01.utils;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (ChatSearchRegistry.has(e.getPlayer().getUniqueId())) {
            ChatSearchRegistry.handle(e.getPlayer().getUniqueId(), e.getMessage());
            e.setCancelled(true); // 입력 차단 (다른 유저에게 보이지 않게)
        }
    }
}
