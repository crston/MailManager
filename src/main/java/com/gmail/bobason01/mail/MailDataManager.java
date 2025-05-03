package com.gmail.bobason01.mail;

import com.gmail.bobason01.storage.MailFileStorage;
import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class MailDataManager {

    private static MailDataManager instance;

    private final Map<UUID, List<Mail>> inboxMap = new HashMap<>();
    private final Map<UUID, Set<UUID>> blacklistMap = new HashMap<>();
    private final Set<UUID> notifyDisabled = new HashSet<>();
    private final Map<UUID, Set<UUID>> excludeMap = new HashMap<>();
    private final Map<UUID, List<Mail>> mailQueue = new HashMap<>();

    private Plugin plugin;

    private MailDataManager() {}

    public static MailDataManager getInstance() {
        if (instance == null) {
            instance = new MailDataManager();
        }
        return instance;
    }

    public void init(Plugin plugin) {
        this.plugin = plugin;
        MailFileStorage.loadAll(plugin, inboxMap, blacklistMap, notifyDisabled, excludeMap);
    }

    public void save() {
        if (plugin != null) {
            MailFileStorage.saveAll(plugin, inboxMap, blacklistMap, notifyDisabled, excludeMap);
        }
    }

    // ===== 메일 관리 =====

    public void addMail(UUID playerId, Mail mail) {
        inboxMap.computeIfAbsent(playerId, k -> new ArrayList<>()).add(mail);
        save();

        if (isNotifyEnabled(playerId)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(LangUtil.get("mail.notify-received"));
            }
        }
    }

    public List<Mail> getMails(UUID playerId) {
        return inboxMap.computeIfAbsent(playerId, k -> new ArrayList<>());
    }

    public void removeMail(UUID playerId, Mail mail) {
        List<Mail> mails = inboxMap.get(playerId);
        if (mails != null) {
            mails.remove(mail);
        }
        save();
    }

    public void clearMail(UUID playerId) {
        inboxMap.remove(playerId);
        save();
    }

    // ===== 블랙리스트 =====

    public Set<UUID> getBlacklist(UUID playerId) {
        return blacklistMap.computeIfAbsent(playerId, k -> new HashSet<>());
    }

    public void addBlacklist(UUID playerId, UUID blocked) {
        getBlacklist(playerId).add(blocked);
        save();
    }

    public void removeBlacklist(UUID playerId, UUID blocked) {
        getBlacklist(playerId).remove(blocked);
        save();
    }

    // ===== 알림 설정 =====

    public boolean isNotifyEnabled(UUID playerId) {
        return !notifyDisabled.contains(playerId);
    }

    public void setNotify(UUID playerId, boolean enabled) {
        if (enabled) {
            notifyDisabled.remove(playerId);
        } else {
            notifyDisabled.add(playerId);
        }
        save();
    }

    public boolean toggleNotify(UUID playerId) {
        boolean enabled = !isNotifyEnabled(playerId);
        setNotify(playerId, enabled);
        return enabled;
    }

    // ===== 전체전송 제외 =====

    public Set<UUID> getExcluded(UUID playerId) {
        return excludeMap.computeIfAbsent(playerId, k -> new HashSet<>());
    }

    public void addExcluded(UUID playerId, UUID excluded) {
        getExcluded(playerId).add(excluded);
        save();
    }

    public void removeExcluded(UUID playerId, UUID excluded) {
        getExcluded(playerId).remove(excluded);
        save();
    }

    // ===== 큐 처리 (성능 최적화용) =====

    public void queueMail(UUID playerId, Mail mail) {
        mailQueue.computeIfAbsent(playerId, k -> new ArrayList<>()).add(mail);
    }

    public void flushQueue() {
        for (Map.Entry<UUID, List<Mail>> entry : mailQueue.entrySet()) {
            inboxMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
        }
        mailQueue.clear();
        save();
    }

    // ===== 유틸 =====

    public void reload(Plugin plugin) {
        inboxMap.clear();
        blacklistMap.clear();
        notifyDisabled.clear();
        excludeMap.clear();
        MailFileStorage.loadAll(plugin, inboxMap, blacklistMap, notifyDisabled, excludeMap);
    }

    public void reset(UUID uuid) {
        inboxMap.remove(uuid);
        blacklistMap.remove(uuid);
        notifyDisabled.remove(uuid);
        excludeMap.remove(uuid);
        save();
    }
}
