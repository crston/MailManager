package com.gmail.bobason01.listeners;

import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class MailLoginListener implements Listener {

    private final Plugin plugin;

    public MailLoginListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // [중요] 비동기로 플레이어 데이터(알림 설정 등)를 DB에서 로드
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            MailDataManager.getInstance().loadPlayerData(uuid, player.getName());

            // 로드 후 알림 체크 (메인 스레드에서 UI 처리)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                if (MailDataManager.getInstance().isNotify(uuid)
                        && !MailDataManager.getInstance().getUnreadMails(uuid).isEmpty()) {
                    sendMailNotification(player);
                }
            });
        });
    }

    private void sendMailNotification(Player player) {
        if (!player.isOnline()) return;

        String lang = LangManager.getLanguage(player.getUniqueId());
        int unreadCount = MailDataManager.getInstance().getUnreadMails(player.getUniqueId()).size();

        player.sendTitle(
                LangManager.get(lang, "login.title.main"),
                LangManager.get(lang, "login.title.sub").replace("%count%", String.valueOf(unreadCount)),
                10, 60, 10
        );

        player.sendMessage(LangManager.get(lang, "login.message").replace("%count%", String.valueOf(unreadCount)));
        ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_RECEIVE_NOTIFICATION);
    }
}