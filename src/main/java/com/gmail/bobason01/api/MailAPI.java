package com.gmail.bobason01.api;

import com.gmail.bobason01.api.events.MailPreSendEvent;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MailAPI {

    private MailAPI() {
    }

    public static boolean sendMail(UUID sender, UUID receiver, List<ItemStack> items, LocalDateTime expireAt) {
        Mail mail = new Mail(sender, receiver, items, LocalDateTime.now(), expireAt);

        MailPreSendEvent event = new MailPreSendEvent(mail, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        MailDataManager.getInstance().addMail(mail);
        return true;
    }

    public static List<Mail> getMails(UUID playerUuid) {
        return MailDataManager.getInstance().getMails(playerUuid);
    }

    public static CompletableFuture<List<Mail>> getMailsAsync(UUID playerUuid) {
        return MailDataManager.getInstance().getMailsAsync(playerUuid);
    }

    public static List<Mail> getUnreadMails(UUID playerUuid) {
        return MailDataManager.getInstance().getUnreadMails(playerUuid);
    }

    public static int getUnreadMailCount(UUID playerUuid) {
        return getUnreadMails(playerUuid).size();
    }

    public static void deleteMail(Mail mail) {
        MailDataManager.getInstance().removeMail(mail);
    }

    public static void clearAllMails(UUID playerUuid) {
        MailDataManager.getInstance().resetPlayerMails(playerUuid);
    }

    public static boolean isBlacklisted(UUID owner, UUID target) {
        return MailDataManager.getInstance().getBlacklist(owner).contains(target);
    }

    public static Set<UUID> getBlacklist(UUID owner) {
        return MailDataManager.getInstance().getBlacklist(owner);
    }

    public static void setBlacklist(UUID owner, Set<UUID> blacklist) {
        MailDataManager.getInstance().setBlacklist(owner, blacklist);
    }

    public static boolean isExcluded(UUID owner, UUID target) {
        return MailDataManager.getInstance().getExclude(owner).contains(target);
    }

    public static Set<UUID> getExcludeList(UUID owner) {
        return MailDataManager.getInstance().getExclude(owner);
    }

    public static void setExcludeList(UUID owner, Set<UUID> excludeList) {
        MailDataManager.getInstance().setExclude(owner, excludeList);
    }

    public static boolean isNotificationEnabled(UUID playerUuid) {
        return MailDataManager.getInstance().isNotify(playerUuid);
    }

    public static void setNotificationEnabled(UUID playerUuid, boolean enabled) {
        MailDataManager.getInstance().setNotify(playerUuid, enabled);
    }

    public static String getLanguage(UUID playerUuid) {
        return LangManager.getLanguage(playerUuid);
    }
}