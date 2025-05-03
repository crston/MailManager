package com.gmail.bobason01.mail;

import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;
import java.util.*;

public class MailService {

    private static final Map<UUID, OfflinePlayer> targetMap = new HashMap<>();
    private static final Map<UUID, ItemStack> attachedItemMap = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> timeDataMap = new HashMap<>();
    private static final Map<UUID, String> contextMap = new HashMap<>();

    public static void send(Player sender, Plugin plugin) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = sender.getUniqueId();
            OfflinePlayer target = getTarget(uuid);
            ItemStack item = getAttachedItem(uuid);
            Map<String, Integer> timeData = getTimeData(uuid);

            if (target == null || item == null || item.getType() == Material.AIR) {
                sender.sendMessage(LangUtil.get("mail.invalid-args"));
                return;
            }

            giveMail(sender, target, item, timeData);
            clear(uuid);
        });
    }

    public static void sendAll(Player sender, Plugin plugin) {
        UUID senderId = sender.getUniqueId();
        ItemStack item = getAttachedItem(senderId);
        Map<String, Integer> timeData = getTimeData(senderId);

        if (item == null || item.getType() == Material.AIR) {
            sender.sendMessage(LangUtil.get("mail.invalid-args"));
            return;
        }

        Set<UUID> excludeList = MailDataManager.getInstance().getExcluded(senderId);
        List<OfflinePlayer> targets = Arrays.asList(Bukkit.getOfflinePlayers());

        new BukkitRunnable() {
            int index = 0;
            final int batchSize = 10;

            @Override
            public void run() {
                int count = 0;
                while (index < targets.size() && count < batchSize) {
                    OfflinePlayer target = targets.get(index++);
                    UUID targetId = target.getUniqueId();
                    if (!targetId.equals(senderId) && !excludeList.contains(targetId)) {
                        giveMail(sender, target, item, timeData);
                    }
                    count++;
                }
                if (index >= targets.size()) {
                    clear(senderId);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private static boolean giveMail(Player sender, OfflinePlayer target, ItemStack item, Map<String, Integer> timeData) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (MailDataManager.getInstance().getBlacklist(targetId).contains(senderId)) {
            sender.sendMessage(LangUtil.get("mail.blacklisted"));
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = computeExpireTime(now, timeData);

        Mail mail = new Mail(senderId, targetId, item.clone(), now, expire);
        MailDataManager.getInstance().addMail(targetId, mail);

        String targetName = Optional.ofNullable(target.getName()).orElse("Unknown");
        sender.sendMessage(LangUtil.get("mail.sent", Map.of("player", targetName)));
        return true;
    }

    private static LocalDateTime computeExpireTime(LocalDateTime base, Map<String, Integer> timeData) {
        if (timeData == null || timeData.isEmpty()) return null;

        return base
                .plusSeconds(timeData.getOrDefault("second", 0))
                .plusMinutes(timeData.getOrDefault("minute", 0))
                .plusHours(timeData.getOrDefault("hour", 0))
                .plusDays(timeData.getOrDefault("day", 0))
                .plusMonths(timeData.getOrDefault("month", 0))
                .plusYears(timeData.getOrDefault("year", 0));
    }

    public static void setTarget(UUID uuid, OfflinePlayer target) {
        targetMap.put(uuid, target);
    }

    public static OfflinePlayer getTarget(UUID uuid) {
        return targetMap.get(uuid);
    }

    public static void setAttachedItem(UUID uuid, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            attachedItemMap.remove(uuid);
        } else {
            attachedItemMap.put(uuid, item);
        }
    }

    public static ItemStack getAttachedItem(UUID uuid) {
        return attachedItemMap.get(uuid);
    }

    public static void setTimeData(UUID uuid, Map<String, Integer> timeData) {
        timeDataMap.put(uuid, timeData);
    }

    public static Map<String, Integer> getTimeData(UUID uuid) {
        return timeDataMap.getOrDefault(uuid, new HashMap<>());
    }

    public static void setContext(UUID uuid, String context) {
        contextMap.put(uuid, context);
    }

    public static String getContext(UUID uuid) {
        return contextMap.getOrDefault(uuid, "send");
    }

    public static void clear(UUID uuid) {
        targetMap.remove(uuid);
        attachedItemMap.remove(uuid);
        timeDataMap.remove(uuid);
        contextMap.remove(uuid);
    }
}
