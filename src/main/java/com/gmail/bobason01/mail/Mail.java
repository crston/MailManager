package com.gmail.bobason01.mail;

import org.bukkit.inventory.ItemStack;

import java.io.Serializable;
import java.util.UUID;

public class Mail implements Serializable {

    private final UUID sender;
    private final ItemStack item;
    private final long expireAtMillis;

    public Mail(UUID sender, ItemStack item, long expireAtMillis) {
        this.sender = sender;
        this.item = item;
        this.expireAtMillis = expireAtMillis;
    }

    public UUID getSender() {
        return sender;
    }

    public ItemStack getItem() {
        return item;
    }

    public long getExpireAtMillis() {
        return expireAtMillis;
    }
}
