package com.gmail.bobason01.mail;

import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class Mail {

    private final UUID sender;
    private final UUID receiver;
    private final ItemStack item;
    private final LocalDateTime sentAt;
    private final LocalDateTime expireAt;

    public Mail(UUID sender, UUID receiver, ItemStack item, LocalDateTime sentAt, LocalDateTime expireAt) {
        this.sender = sender;
        this.receiver = receiver;
        this.item = item;
        this.sentAt = sentAt;
        this.expireAt = expireAt;
    }

    public UUID getSender() {
        return sender;
    }

    public UUID getReceiver() {
        return receiver;
    }

    public ItemStack getItem() {
        return item;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public boolean isExpired() {
        return expireAt != null && LocalDateTime.now().isAfter(expireAt);
    }

    public ItemStack toItemStack() {
        ItemStack display = item.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            OfflinePlayer senderPlayer = Bukkit.getOfflinePlayer(sender);
            String senderName = senderPlayer.getName() != null ? senderPlayer.getName() : "Unknown";
            String date = sentAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            meta.setLore(List.of(
                    LangUtil.get("mail.from") + senderName,
                    LangUtil.get("mail.sent-at") + date,
                    (expireAt != null ? LangUtil.get("mail.expires") + expireAt.toLocalDate().toString() : "")
            ));
            display.setItemMeta(meta);
        }
        return display;
    }

    public void give(Player player) {
        player.getInventory().addItem(item.clone());
    }
}
