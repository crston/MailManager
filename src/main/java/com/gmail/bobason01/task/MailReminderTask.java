package com.gmail.bobason01.task;

import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class MailReminderTask {

    public static void start(Plugin plugin) {
        // 5분 = 5 * 60 * 20 ticks = 6000
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!MailDataManager.getInstance().isNotifyEnabled(player.getUniqueId())) continue;

                int unreadCount = MailDataManager.getInstance().getUnreadMails(player.getUniqueId()).size();
                if (unreadCount > 0) {
                    player.sendMessage("§e[우편] 아직 읽지 않은 우편이 §f" + unreadCount + "개§e 있습니다");
                    player.sendTitle("§6✉ 우편 알림", "§f" + unreadCount + "개의 우편을 확인하세요", 10, 40, 20);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }
        }, 6000L, 6000L);
    }
}
