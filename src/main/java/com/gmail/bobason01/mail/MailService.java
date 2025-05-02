package com.gmail.bobason01.mail;

import com.gmail.bobason01.storage.MailFileStorage;
import com.gmail.bobason01.utils.LangUtil;
import com.gmail.bobason01.utils.MailNotifySettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class MailService {

    private static final Map<UUID, List<Mail>> inboxMap = new HashMap<>();

    public static void loadAllMail() {
        inboxMap.clear();
        inboxMap.putAll(MailFileStorage.loadAll());
    }

    public static void saveAllMail() {
        MailFileStorage.saveAll(inboxMap);
    }

    public static List<Mail> getInbox(UUID uuid) {
        return inboxMap.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    public static void addMail(UUID target, Mail mail) {
        getInbox(target).add(mail);

        Player onlineTarget = Bukkit.getPlayer(target);
        if (onlineTarget != null && MailNotifySettingsManager.isNotifyEnabled(target)) {
            onlineTarget.sendMessage(LangUtil.get(target, "notify.new-mail"));
            onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
    }

    public static void removeMail(UUID target, Mail mail) {
        getInbox(target).remove(mail);
    }

    public static void clearInbox(UUID target) {
        getInbox(target).clear();
    }
}
