package com.gmail.bobason01.mail;

import com.gmail.bobason01.storage.MailFileStorage;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class MailDataManager {

    private static final MailDataManager instance = new MailDataManager();
    public static MailDataManager getInstance() {
        return instance;
    }

    private final Map<UUID, List<Mail>> inboxMap = new HashMap<>();
    private final Map<UUID, Set<UUID>> blacklistMap = new HashMap<>();
    private final Map<UUID, Set<UUID>> excludeMap = new HashMap<>();
    private final Set<UUID> notifyEnabled = new HashSet<>();

    public void load(Plugin plugin) {
        MailFileStorage.loadAll(plugin, inboxMap, blacklistMap, notifyEnabled, excludeMap);
    }

    public void save(Plugin plugin) {
        MailFileStorage.saveAll(plugin, inboxMap, blacklistMap, notifyEnabled, excludeMap);
    }

    public void reset(UUID uniqueId) {
        inboxMap.clear();
        blacklistMap.clear();
        excludeMap.clear();
        notifyEnabled.clear();
    }

    public List<Mail> getMails(UUID player) {
        return inboxMap.computeIfAbsent(player, k -> new ArrayList<>());
    }

    public void addMail(UUID player, Mail mail) {
        getMails(player).add(mail);
    }

    public boolean removeMail(UUID player, Mail mail) {
        List<Mail> inbox = inboxMap.get(player);
        return inbox != null && inbox.remove(mail);
    }

    public Set<UUID> getBlacklist(UUID player) {
        return blacklistMap.computeIfAbsent(player, k -> new HashSet<>());
    }

    public Set<UUID> getExcluded(UUID player) {
        return excludeMap.computeIfAbsent(player, k -> new HashSet<>());
    }

    public boolean isNotifyEnabled(UUID player) {
        return notifyEnabled.contains(player);
    }

    public boolean toggleNotify(UUID player) {
        if (notifyEnabled.contains(player)) {
            notifyEnabled.remove(player);
            return false;
        } else {
            notifyEnabled.add(player);
            return true;
        }
    }

    public void reload(Plugin plugin) {
        inboxMap.clear();
        blacklistMap.clear();
        excludeMap.clear();
        notifyEnabled.clear();
        load(plugin);
    }
}
