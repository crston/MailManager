package com.gmail.bobason01.mail;

import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.LocalDateTime;
import java.util.*;

public class MailService {

    private static final Map<UUID, OfflinePlayer> targetMap = new HashMap<>();
    private static final Map<UUID, ItemStack> attachedItemMap = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> timeDataMap = new HashMap<>();
    private static final Map<UUID, String> contextMap = new HashMap<>();

    // ===== 전체 전송 =====
    public static void sendAll(Player sender, Plugin plugin) {
        final UUID senderId = sender.getUniqueId();
        final ItemStack item = getAttachedItem(senderId);
        final Map<String, Integer> timeData = getTimeData(senderId);

        if (item == null || item.getType() == Material.AIR) {
            sender.sendMessage(LangUtil.get("mail.invalid-args"));
            return;
        }

        final Set<UUID> excludeList = MailDataManager.getInstance().getExcluded(senderId);
        final OfflinePlayer[] targets = Bukkit.getOfflinePlayers();

        final LocalDateTime createdAt = LocalDateTime.now();
        final LocalDateTime expireAt = createdAt
                .plusYears(timeData.getOrDefault("year", 0))
                .plusMonths(timeData.getOrDefault("month", 0))
                .plusDays(timeData.getOrDefault("day", 0))
                .plusHours(timeData.getOrDefault("hour", 0))
                .plusMinutes(timeData.getOrDefault("minute", 0));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int successCount = 0;

            for (OfflinePlayer target : targets) {
                final UUID targetId = target.getUniqueId();

                if (targetId.equals(senderId) || excludeList.contains(targetId)) continue;
                if (MailDataManager.getInstance().getBlacklist(targetId).contains(senderId)) continue;

                Mail mail = new Mail(
                        senderId,
                        targetId,
                        item.clone(),
                        createdAt,
                        expireAt
                );

                MailDataManager.getInstance().queueMail(targetId, mail);
                successCount++;
            }

            final int finalSuccessCount = successCount;
            Bukkit.getScheduler().runTask(plugin, () -> {
                MailDataManager.getInstance().flushQueue();
                clear(senderId);
                sender.sendMessage(LangUtil.get("mail.sent-all")
                        .replace("{count}", String.valueOf(finalSuccessCount)));
            });
        });
    }

    // ===== 단일 전송 =====
    public static void send(Player sender, Plugin plugin) {
        final UUID senderId = sender.getUniqueId();
        final OfflinePlayer target = getTarget(senderId);
        final ItemStack item = getAttachedItem(senderId);
        final Map<String, Integer> timeData = getTimeData(senderId);

        if (target == null || item == null || item.getType() == Material.AIR) {
            sender.sendMessage(LangUtil.get("mail.invalid-args"));
            return;
        }

        final LocalDateTime createdAt = LocalDateTime.now();
        final LocalDateTime expireAt = createdAt
                .plusYears(timeData.getOrDefault("year", 0))
                .plusMonths(timeData.getOrDefault("month", 0))
                .plusDays(timeData.getOrDefault("day", 0))
                .plusHours(timeData.getOrDefault("hour", 0))
                .plusMinutes(timeData.getOrDefault("minute", 0));

        Mail mail = new Mail(
                senderId,
                target.getUniqueId(),
                item.clone(),
                createdAt,
                expireAt
        );

        MailDataManager.getInstance().addMail(target.getUniqueId(), mail);
        clear(senderId);
        sender.sendMessage(LangUtil.get("mail.sent-single"));
    }

    // ===== Getter / Setter =====

    public static OfflinePlayer getTarget(UUID uuid) {
        return targetMap.get(uuid);
    }

    public static void setTarget(UUID uuid, OfflinePlayer target) {
        targetMap.put(uuid, target);
    }

    public static ItemStack getAttachedItem(UUID uuid) {
        return attachedItemMap.get(uuid);
    }

    public static void setAttachedItem(UUID uuid, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            attachedItemMap.remove(uuid);
        } else {
            attachedItemMap.put(uuid, item);
        }
    }

    public static Map<String, Integer> getTimeData(UUID uuid) {
        return timeDataMap.getOrDefault(uuid, new HashMap<>());
    }

    public static void setTimeData(UUID uuid, Map<String, Integer> timeData) {
        timeDataMap.put(uuid, timeData);
    }

    public static void setContext(UUID uuid, String context) {
        contextMap.put(uuid, context);
    }

    public static String getContext(UUID uuid) {
        return contextMap.getOrDefault(uuid, "");
    }

    public static void clear(UUID uuid) {
        attachedItemMap.remove(uuid);
        timeDataMap.remove(uuid);
        contextMap.remove(uuid);
        targetMap.remove(uuid);
    }
}
