package com.gmail.bobason01.mail;

import com.gmail.bobason01.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Mail implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 2L;
    private static final transient DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final transient Map<UUID, String> nameCache = new HashMap<>();

    private final UUID mailId;
    private final UUID sender;
    private final UUID receiver;
    private final LocalDateTime sentAt;
    private final LocalDateTime expireAt;

    private transient ItemStack item;
    private transient String cachedSenderName;

    public Mail(UUID sender, UUID receiver, ItemStack item, LocalDateTime sentAt, LocalDateTime expireAt) {
        this(UUID.randomUUID(), sender, receiver, item, sentAt, expireAt);
    }

    public Mail(UUID mailId, UUID sender, UUID receiver, ItemStack item, LocalDateTime sentAt, LocalDateTime expireAt) {
        this.mailId = Objects.requireNonNull(mailId);
        this.sender = sender;
        this.receiver = receiver;
        this.item = item != null ? item.clone() : null;
        this.sentAt = sentAt;
        this.expireAt = expireAt;
    }

    public UUID getMailId() { return mailId; }
    public UUID getSender() { return sender; }
    public UUID getReceiver() { return receiver; }
    public ItemStack getItem() { return item != null ? item.clone() : null; }
    public LocalDateTime getSentAt() { return sentAt; }
    public LocalDateTime getExpireAt() { return expireAt; }

    public boolean isExpired() {
        return expireAt != null && LocalDateTime.now().isAfter(expireAt);
    }

    public ItemStack toItemStack(Player viewer) {
        if (item == null) return null;
        ItemStack display = item.clone();
        ItemMeta meta = display.getItemMeta();

        if (meta != null) {
            String lang = LangManager.getLanguage(viewer.getUniqueId());
            if (cachedSenderName == null) {
                cachedSenderName = resolveSenderName(lang);
            }

            List<String> originalLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            originalLore.addAll(buildMailLore(lang));
            meta.setLore(originalLore);
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
            if (isExpired()) {
                lore.add("§c" + LangManager.get(lang, "mail.lore.expired"));
            }
        }
        return lore;
    }

    private String resolveSenderName(String lang) {
        if (sender == null) return LangManager.get(lang, "mail.unknown");

        return nameCache.computeIfAbsent(sender, id -> {
            OfflinePlayer p = Bukkit.getOfflinePlayer(id);
            String name = p.getName();
            return (name != null) ? name : LangManager.get(lang, "mail.unknown");
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Mail mail = (Mail) o;
        return mailId.equals(mail.mailId);
    }

    @Override
    public int hashCode() {
        return mailId.hashCode();
    }

    @java.io.Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        if (item != null) {
            out.writeObject(item.serialize());
        } else {
            out.writeObject(null);
        }
    }

    @java.io.Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        Map<String, Object> itemMap = (Map<String, Object>) in.readObject();
        if (itemMap != null) {
            this.item = ItemStack.deserialize(itemMap);
        }
    }
}