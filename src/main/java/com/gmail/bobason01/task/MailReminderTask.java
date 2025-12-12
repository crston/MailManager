package com.gmail.bobason01.task;

import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class MailReminderTask {

    public static void start(Plugin plugin) {
        long interval = 20L * plugin.getConfig().getLong("mail-reminder-interval", 300);

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();

                // [중요] 타 서버에서 온 메일을 감지하기 위해 주기적으로 DB 리로드
                MailDataManager.getInstance().forceReloadMails(uuid);

                if (!MailDataManager.getInstance().isNotify(uuid)) continue;

                int unreadCount = MailDataManager.getInstance().getUnreadMails(uuid).size();
                if (unreadCount > 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;

                        String lang = LangManager.getLanguage(uuid);

                        player.sendMessage(LangManager.get(lang, "mail.notify.message")
                                .replace("%count%", String.valueOf(unreadCount)));

                        player.sendTitle(
                                LangManager.get(lang, "mail.notify.title.main"),
                                LangManager.get(lang, "mail.notify.title.sub").replace("%count%", String.valueOf(unreadCount)),
                                10, 40, 20
                        );

                        ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_REMINDER);
                    });
                }
            }
        }, interval, interval);
    }
}