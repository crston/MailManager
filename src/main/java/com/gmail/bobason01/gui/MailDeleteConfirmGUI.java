package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MailDeleteConfirmGUI implements Listener, InventoryHolder {

    private static final int SIZE = 27;
    private static final int YES_SLOT = 11;
    private static final int NO_SLOT = 15;

    private final Plugin plugin;
    private final Player player;
    private final List<Mail> mails;
    private Inventory inv;

    public MailDeleteConfirmGUI(Plugin plugin) {
        this.plugin = plugin;
        this.player = null;
        this.mails = null;
    }

    public MailDeleteConfirmGUI(Player player, Plugin plugin, List<Mail> mails) {
        this.plugin = plugin;
        this.player = player;
        this.mails = (mails == null) ? Collections.emptyList() : new ArrayList<>(mails);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        inv = Bukkit.createInventory(this, SIZE, LangManager.get(lang, "gui.delete.title"));

        ItemStack yes = new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.DELETE_GUI_CONFIRM_BUTTON).clone())
                .name(LangManager.get(lang, "gui.delete.yes_name"))
                .lore(LangManager.get(lang, "gui.delete.yes_lore"))
                .build();

        ItemStack no = new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.DELETE_GUI_CANCEL_BUTTON).clone())
                .name(LangManager.get(lang, "gui.delete.no_name"))
                .lore(LangManager.get(lang, "gui.delete.no_lore"))
                .build();

        inv.setItem(YES_SLOT, yes);
        inv.setItem(NO_SLOT, no);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailDeleteConfirmGUI gui)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        e.setCancelled(true);
        if (gui.mails == null || gui.mails.isEmpty()) return;

        int slot = e.getRawSlot();
        UUID uuid = p.getUniqueId();

        if (slot == YES_SLOT) {
            for (Mail mail : gui.mails) {
                MailDataManager.getInstance().removeMail(mail);
            }
            MailDataManager.getInstance().flushNow();

            if (gui.mails.size() > 1) {
                p.sendMessage(LangManager.get(uuid, "mail.deleted_multi")
                        .replace("{count}", String.valueOf(gui.mails.size())));
            } else {
                p.sendMessage(LangManager.get(uuid, "mail.deleted"));
            }
            ConfigManager.playSound(p, ConfigManager.SoundType.MAIL_DELETE_SUCCESS);

            p.closeInventory();
            // 싱글톤 인스턴스로 메일 목록 열기
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    MailManager.getInstance().mailGUI.open(p), 2L
            );
        } else if (slot == NO_SLOT) {
            p.sendMessage(LangManager.get(uuid, "mail.delete_cancel"));
            ConfigManager.playSound(p, ConfigManager.SoundType.GUI_CLICK);

            p.closeInventory();
            // 싱글톤 인스턴스로 메일 목록 열기
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    MailManager.getInstance().mailGUI.open(p), 2L
            );
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MailDeleteConfirmGUI) {
            e.setCancelled(true);
        }
    }
}