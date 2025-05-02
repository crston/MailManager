package com.gmail.bobason01.mail;

import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.*;

public class MailService {

    private static final Map<UUID, OfflinePlayer> targetMap = new HashMap<>();
    private static final Map<UUID, ItemStack> attachedItemMap = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> timeDataMap = new HashMap<>();
    private static final Map<UUID, String> contextMap = new HashMap<>(); // send or sendall

    public static void giveMail(Player sender, OfflinePlayer target, ItemStack item, Map<String, Integer> timeData) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (MailDataManager.getInstance().getBlacklist(targetId).contains(senderId)) {
            sender.sendMessage(LangUtil.get("mail.blacklisted"));
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = null;

        if (!timeData.isEmpty()) {
            expire = now
                    .plusSeconds(timeData.getOrDefault("second", 0))
                    .plusMinutes(timeData.getOrDefault("minute", 0))
                    .plusHours(timeData.getOrDefault("hour", 0))
                    .plusDays(timeData.getOrDefault("day", 0))
                    .plusMonths(timeData.getOrDefault("month", 0))
                    .plusYears(timeData.getOrDefault("year", 0));
        }

        Mail mail = new Mail(senderId, targetId, item.clone(), now, expire);
        MailDataManager.getInstance().addMail(targetId, mail);

        sender.sendMessage(LangUtil.get("mail.sent").replace("{player}", Objects.requireNonNull(target.getName())));
    }

    public static void send(Player sender) {
        UUID uuid = sender.getUniqueId();
        OfflinePlayer target = getTarget(uuid);
        ItemStack item = getAttachedItem(uuid);
        Map<String, Integer> timeData = getTimeData(uuid);

        if (target == null || item == null) {
            sender.sendMessage(LangUtil.get("mail.invalid-args"));
            return;
        }

        giveMail(sender, target, item, timeData);

        clear(uuid);
    }

    public static void sendAll(Player sender) {
        UUID senderId = sender.getUniqueId();
        ItemStack item = getAttachedItem(senderId);
        Map<String, Integer> timeData = getTimeData(senderId);

        if (item == null) {
            sender.sendMessage(LangUtil.get("mail.invalid-args"));
            return;
        }

        Set<UUID> excludeList = MailDataManager.getInstance().getExcluded(senderId);

        for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
            if (target.getUniqueId().equals(senderId)) continue;
            if (excludeList.contains(target.getUniqueId())) continue;
            giveMail(sender, target, item, timeData);
        }

        clear(senderId);
    }

    public static void clear(UUID uuid) {
        targetMap.remove(uuid);
        attachedItemMap.remove(uuid);
        timeDataMap.remove(uuid);
        contextMap.remove(uuid);
    }

    public static void setTarget(UUID uuid, OfflinePlayer target) {
        targetMap.put(uuid, target);
    }

    public static OfflinePlayer getTarget(UUID uuid) {
        return targetMap.get(uuid);
    }

    public static void setAttachedItem(UUID uuid, ItemStack item) {
        attachedItemMap.put(uuid, item);
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
}
