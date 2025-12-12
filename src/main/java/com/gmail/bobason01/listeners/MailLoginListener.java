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

        // [중요] 접속 시 글로벌 플레이어 정보 업데이트 (멀티 서버 지원)
        MailDataManager.getInstance().updateGlobalPlayerInfo(uuid, player.getName());

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (!player.isOnline()) return;

            // [중요] 로그인 시 DB에서 최신 메일 목록 강제 로드 (다른 서버에서 보낸 메일 확인)
            MailDataManager.getInstance().forceReloadMails(uuid);

            if (MailDataManager.getInstance().isNotify(uuid)
                    && !MailDataManager.getInstance().getUnreadMails(uuid).isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> sendMailNotification(player));
            }
        }, 60L);
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

        player.sendMessage(LangManager.get(lang, "login.message"));

        ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_RECEIVE_NOTIFICATION);
    }
}