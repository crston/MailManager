package com.gmail.bobason01.mail;

import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Mail {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            List<String> lore = new ArrayList<>();

            OfflinePlayer senderPlayer = Bukkit.getOfflinePlayer(sender);
            String senderName = senderPlayer.getName() != null ? senderPlayer.getName() : "Unknown";
            lore.add(LangUtil.get("mail.from") + senderName);
            lore.add(LangUtil.get("mail.sent-at") + sentAt.format(FORMATTER));

            if (expireAt != null) {
                lore.add(LangUtil.get("mail.expires") + expireAt.toLocalDate());
            }

            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    public void give(Player player) {
        player.getInventory().addItem(item.clone());
    }
}
