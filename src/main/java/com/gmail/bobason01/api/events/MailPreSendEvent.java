package com.gmail.bobason01.api.events;

import com.gmail.bobason01.mail.Mail;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class MailPreSendEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private final Mail mail;

    public MailPreSendEvent(Mail mail, boolean isAsync) {
        super(isAsync);
        this.mail = mail;
    }

    public Mail getMail() {
        return mail;
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