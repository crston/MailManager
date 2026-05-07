package com.gmail.bobason01.api.events;

import com.gmail.bobason01.mail.Mail;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class MailClaimEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private final Player player;
    private final Mail mail;
    private final List<ItemStack> claimedItems;

    public MailClaimEvent(Player player, Mail mail, List<ItemStack> claimedItems) {
        this.player = player;
        this.mail = mail;
        this.claimedItems = claimedItems;
    }

    public Player getPlayer() {
        return player;
    }

    public Mail getMail() {
        return mail;
    }

    public List<ItemStack> getClaimedItems() {
        return claimedItems;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}