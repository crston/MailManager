package com.gmail.bobason01.utils;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    // MONITOR가 아닌 HIGHEST나 LOWEST를 사용하여 다른 플러그인보다 먼저/확실하게 처리
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // 검색 중인지 확인하고 콜백 추출
        ChatSearchRegistry.ChatCallback callback = ChatSearchRegistry.consume(player);

        if (callback != null) {
            // 채팅이 일반 채팅으로 나가지 않도록 완전 차단
            event.setCancelled(true);

            // 콜백 실행 (비동기 스레드에서 실행됨을 주의)
            callback.onChat(event.getMessage());
        }
    }
}