package com.gmail.bobason01.mail;

import com.gmail.bobason01.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Mail implements Serializable {

    @Serial
    private static final long serialVersionUID = 4L;

    private static final transient DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final transient Map<UUID, String> nameCache = new HashMap<>();

    private final UUID mailId;
    private final UUID sender;
    private final UUID receiver;
    private final LocalDateTime sentAt;
    private final LocalDateTime expireAt;
    private final List<ItemStack> items;
    private transient String cachedSenderName;

    public Mail(UUID sender, UUID receiver, List<ItemStack> items, LocalDateTime sentAt, LocalDateTime expireAt) {
        this(UUID.randomUUID(), sender, receiver, items, sentAt, expireAt);
    }

    public Mail(UUID mailId, UUID sender, UUID receiver, List<ItemStack> items, LocalDateTime sentAt, LocalDateTime expireAt) {
        this.mailId = Objects.requireNonNull(mailId, "mailId");
        this.sender = sender;
        this.receiver = receiver;
        this.items = new ArrayList<>();
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null) this.items.add(item.clone());
            }
        }
        this.sentAt = sentAt != null ? sentAt : LocalDateTime.now();
        this.expireAt = expireAt;
    }

    public UUID getMailId() { return mailId; }
    public UUID getSender() { return sender; }
    public UUID getReceiver() { return receiver; }
    public List<ItemStack> getItems() { return items; }

    public void setItems(List<ItemStack> newItems) {
        items.clear();
        if (newItems != null) {
            for (ItemStack item : newItems) {
                if (item != null) items.add(item.clone());
            }
        }
    }

    public LocalDateTime getSentAt() { return sentAt; }
    public LocalDateTime getExpireAt() { return expireAt; }

    // 호환성을 위해 추가 (GUI 정렬에서 사용)
    public long getCreatedAt() {
        return sentAt.atZone(java.time.ZoneOffset.UTC).toEpochSecond();
    }

    public boolean isExpired() {
        return expireAt != null && LocalDateTime.now().isAfter(expireAt);
    }

    public ItemStack toItemStack(Player viewer) {
        if (items == null || items.isEmpty()) return null;
        ItemStack display = items.get(0).clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            String lang = LangManager.getLanguage(viewer.getUniqueId());
            if (cachedSenderName == null) cachedSenderName = resolveSenderName(lang);
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.addAll(buildMailLore(lang));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private List<String> buildMailLore(String lang) {
        List<String> lore = new ArrayList<>();
        lore.add("§r");
        lore.add("§7" + LangManager.get(lang, "mail.lore.sender").replace("%sender%", cachedSenderName));
        lore.add("§7" + LangManager.get(lang, "mail.lore.sent").replace("%time%", FORMATTER.format(sentAt)));
        if (expireAt != null) {
            lore.add("§7" + LangManager.get(lang, "mail.lore.expire").replace("%time%", FORMATTER.format(expireAt)));
            if (isExpired()) lore.add("§c" + LangManager.get(lang, "mail.lore.expired"));
        }
        lore.add("§a" + LangManager.get(lang, "mail.send.amount") + " - " + items.size());
        return lore;
    }

    private String resolveSenderName(String lang) {
        if (sender == null) return LangManager.get(lang, "mail.unknown");
        String cached = nameCache.get(sender);
        if (cached != null) return cached;
        OfflinePlayer p = Bukkit.getOfflinePlayer(sender);
        String name = p.getName();
        String result = name != null ? name : LangManager.get(lang, "mail.unknown");
        nameCache.put(sender, result);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mail)) return false;
        Mail mail = (Mail) o;
        return mailId.equals(mail.mailId);
    }

    @Override
    public int hashCode() {
        return mailId.hashCode();
    }
}
