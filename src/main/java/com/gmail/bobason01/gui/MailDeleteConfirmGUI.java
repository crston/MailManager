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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class MailDeleteConfirmGUI implements Listener, InventoryHolder {

    public static final int YES_SLOT = 11;
    public static final int NO_SLOT = 15;

    private final Player player;
    private final Plugin plugin;
    private final Mail mail;
    private Inventory inv;

    public MailDeleteConfirmGUI(Player player, Plugin plugin, Mail mail) {
        this.player = player;
        this.plugin = plugin;
        this.mail = mail;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void open(Player player) {
        String title = LangManager.get(player.getUniqueId(), "gui.mail.delete.title");
        inv = Bukkit.createInventory(this, 27, title);

        // 예 버튼
        inv.setItem(YES_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.DELETE_GUI_CONFIRM_BUTTON).clone())
                .name(LangManager.get(player.getUniqueId(), "gui.mail.delete.yes"))
                .lore(LangManager.get(player.getUniqueId(), "gui.mail.delete.yes_lore"))
                .build());

        // 아니오 버튼
        inv.setItem(NO_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.DELETE_GUI_CANCEL_BUTTON).clone())
                .name(LangManager.get(player.getUniqueId(), "gui.mail.delete.no"))
                .lore(LangManager.get(player.getUniqueId(), "gui.mail.delete.no_lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailDeleteConfirmGUI gui)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();

        if (slot == YES_SLOT) {
            // 우편 삭제
            MailDataManager.getInstance().removeMail(gui.mail);
            p.sendMessage(LangManager.get(p.getUniqueId(), "mail.deleted"));
            ConfigManager.playSound(p, ConfigManager.SoundType.MAIL_DELETE_SUCCESS);
            p.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> new MailGUI(plugin).open(p), 2L);

        } else if (slot == NO_SLOT) {
            // 취소
            p.sendMessage(LangManager.get(p.getUniqueId(), "mail.delete_cancel"));
            ConfigManager.playSound(p, ConfigManager.SoundType.GUI_CLICK);
            p.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> new MailGUI(plugin).open(p), 2L);
        }
    }
}
