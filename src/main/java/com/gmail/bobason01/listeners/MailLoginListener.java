package com.gmail.bobason01.listeners;

import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class MailLoginListener implements Listener {

    private static final long REMINDER_INTERVAL_TICKS = 20L * 300; // 5 minutes

    private final Plugin plugin;

    public MailLoginListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);

        new BukkitRunnable() {
            @Override
            public void run() {
                List<String> unreadTitles = MailDataManager.getInstance().getUnreadMails(uuid).stream()
                        .map(mail -> {
                            try {
                                var item = mail.toItemStack();
                                var meta = item != null ? item.getItemMeta() : null;
                                return (meta != null) ? meta.getDisplayName() : null;
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .filter(title -> !title.isEmpty())
                        .toList();

                if (!unreadTitles.isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;

                        sendMailNotification(player, lang);

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!player.isOnline()) {
                                    cancel();
                                    return;
                                }

                                List<Mail> unread = MailDataManager.getInstance().getUnreadMails(uuid);
                                if (!unread.isEmpty()) {
                                    sendMailNotification(player, lang);
                                } else {
                                    cancel();
                                }
                            }
                        }.runTaskTimer(plugin, REMINDER_INTERVAL_TICKS, REMINDER_INTERVAL_TICKS);
                    });
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void sendMailNotification(Player player, String lang) {
        player.sendTitle(
                LangManager.get(lang, "login.title.main"),
                LangManager.get(lang, "login.title.sub"),
                10, 60, 10
        );

        player.sendMessage(LangManager.get(lang, "login.message"));
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1.0f, 1.2f);
    }
}
