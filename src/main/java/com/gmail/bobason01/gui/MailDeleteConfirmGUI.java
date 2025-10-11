package com.gmail.bobason01.gui;

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
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class MailDeleteConfirmGUI implements Listener, InventoryHolder {

    public static final int YES_SLOT = 11;
    public static final int NO_SLOT = 15;

    private static Plugin plugin;
    private final Player player;
    private final List<Mail> mails;
    private Inventory inv;

    public static void init(Plugin pl) {
        plugin = pl;
    }

    // 단일 메일 삭제 생성자
    public MailDeleteConfirmGUI(Player player, Plugin plugin, Mail mail) {
        this.player = player;
        this.mails = Collections.singletonList(mail);
    }

    // 다중 메일 삭제 생성자
    public MailDeleteConfirmGUI(Player player, Plugin plugin, List<Mail> mails) {
        this.player = player;
        this.mails = mails;
    }

    // 기본 생성자
    public MailDeleteConfirmGUI() {
        this.player = null;
        this.mails = null;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void open(Player player) {
        String title = LangManager.get(player.getUniqueId(), "gui.delete.title");
        inv = Bukkit.createInventory(this, 27, title);

        inv.setItem(YES_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.DELETE_GUI_CONFIRM_BUTTON).clone())
                .name(LangManager.get(player.getUniqueId(), "gui.delete.yes_name"))
                .lore(LangManager.get(player.getUniqueId(), "gui.delete.yes_lore"))
                .build());

        inv.setItem(NO_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.DELETE_GUI_CANCEL_BUTTON).clone())
                .name(LangManager.get(player.getUniqueId(), "gui.delete.no_name"))
                .lore(LangManager.get(player.getUniqueId(), "gui.delete.no_lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailDeleteConfirmGUI gui)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();

        if (gui.mails == null) return;

        if (slot == YES_SLOT) {
            for (Mail mail : gui.mails) {
                MailDataManager.getInstance().removeMail(mail);
            }
            if (gui.mails.size() > 1) {
                p.sendMessage(LangManager.get(p.getUniqueId(), "mail.deleted_multi")
                        .replace("{count}", String.valueOf(gui.mails.size())));
            } else {
                p.sendMessage(LangManager.get(p.getUniqueId(), "mail.deleted"));
            }
            ConfigManager.playSound(p, ConfigManager.SoundType.MAIL_DELETE_SUCCESS);
            p.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> new MailGUI(plugin).open(p), 2L);

        } else if (slot == NO_SLOT) {
            p.sendMessage(LangManager.get(p.getUniqueId(), "mail.delete_cancel"));
            ConfigManager.playSound(p, ConfigManager.SoundType.GUI_CLICK);
            p.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> new MailGUI(plugin).open(p), 2L);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MailDeleteConfirmGUI) {
            e.setCancelled(true);
        }
    }
}
