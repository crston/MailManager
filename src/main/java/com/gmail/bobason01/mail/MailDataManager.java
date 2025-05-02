package com.gmail.bobason01.mail;

import com.gmail.bobason01.storage.MailFileStorage;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class MailDataManager {

    private static MailDataManager instance;

    private final Map<UUID, List<Mail>> inboxMap = new HashMap<>();
    private final Map<UUID, Set<UUID>> blacklistMap = new HashMap<>();
    private final Set<UUID> notifyEnabled = new HashSet<>();
    private final Map<UUID, Set<UUID>> excludeMap = new HashMap<>();

    private Plugin plugin; // 플러그인 참조 보관

    private MailDataManager() {}

    public static MailDataManager getInstance() {
        if (instance == null) {
            instance = new MailDataManager();
        }
        return instance;
    }

    public void init(Plugin plugin) {
        this.plugin = plugin;
        MailFileStorage.loadAll(plugin, inboxMap, blacklistMap, notifyEnabled, excludeMap);
    }

    public void save() {
        if (plugin != null) {
            MailFileStorage.saveAll(plugin, inboxMap, blacklistMap, notifyEnabled, excludeMap);
        }
    }

    public void removeMail(UUID playerId, Mail mail) {
        List<Mail> mails = inboxMap.getOrDefault(playerId, new ArrayList<>());
        mails.remove(mail);
        save(); // 메일 삭제 후 즉시 저장
    }
    // ===== 메일 처리 =====

    public void addMail(UUID playerId, Mail mail) {
        inboxMap.computeIfAbsent(playerId, k -> new ArrayList<>()).add(mail);
        save(); // 자동 저장 — 비활성화하려면 주석 처리 가능
    }

    public List<Mail> getMails(UUID playerId) {
        return inboxMap.computeIfAbsent(playerId, k -> new ArrayList<>());
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

    // ===== 알림 여부 =====

    public boolean isNotifyEnabled(UUID playerId) {
        return notifyEnabled.contains(playerId);
    }

    public void setNotify(UUID playerId, boolean enabled) {
        if (enabled) notifyEnabled.add(playerId);
        else notifyEnabled.remove(playerId);
        save();
    }

    // ===== 전체 전송 제외 대상 =====

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

    public boolean toggleNotify(UUID uuid) {
        boolean current = isNotifyEnabled(uuid);
        setNotify(uuid, !current);
        return !current;
    }

    public void reload(Plugin plugin) {
        inboxMap.clear();
        blacklistMap.clear();
        notifyEnabled.clear();
        excludeMap.clear();
        MailFileStorage.loadAll(plugin, inboxMap, blacklistMap, notifyEnabled, excludeMap);
    }

    public void reset(UUID uuid) {
        inboxMap.remove(uuid);
        blacklistMap.remove(uuid);
        notifyEnabled.remove(uuid);
        excludeMap.remove(uuid);
        save();
    }
}
