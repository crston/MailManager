package com.gmail.bobason01.task;

import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class MailReminderTask {

    public static void start(Plugin plugin) {
        long interval = 20L * plugin.getConfig().getLong("mail-reminder-interval", 300);

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!MailDataManager.getInstance().isNotifyEnabled(player.getUniqueId())) continue;

                int unreadCount = MailDataManager.getInstance().getUnreadMails(player.getUniqueId()).size();
                if (unreadCount > 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        String lang = LangManager.getLanguage(player.getUniqueId());
                        player.sendMessage(LangManager.get(lang, "mail.notify.message").replace("%count%", String.valueOf(unreadCount)));
                        player.sendTitle(LangManager.get(lang, "mail.notify.title.main"), LangManager.get(lang, "mail.notify.title.sub").replace("%count%", String.valueOf(unreadCount)), 10, 40, 20);
                        player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.MAIL_REMINDER), 1.0f, 1.0f);
                    });
                }
            }
        }, interval, interval);
    }
}