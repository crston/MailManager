package com.gmail.bobason01.gui;

import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public class MailDeleteConfirmGUI implements Listener {

    private final Plugin plugin;
    private final Mail mail;
    private final MailGUI parentGui;
    private final int returnPage;

    private static final int YES_SLOT = 11;
    private static final int NO_SLOT = 15;

    public MailDeleteConfirmGUI(Plugin plugin, Mail mail, MailGUI parentGui, int returnPage) {
        this.plugin = plugin;
        this.mail = mail;
        this.parentGui = parentGui;
        this.returnPage = returnPage;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        String title = LangManager.get(player.getUniqueId(), "gui.delete.title");
        Inventory inv = Bukkit.createInventory(player, 27, title);

        inv.setItem(YES_SLOT, new ItemBuilder(Material.RED_WOOL)
                .name(LangManager.get(player.getUniqueId(), "gui.delete.yes"))
                .lore(LangManager.get(player.getUniqueId(), "gui.delete.yes_lore"))
                .build());

        inv.setItem(NO_SLOT, new ItemBuilder(Material.GREEN_WOOL)
                .name(LangManager.get(player.getUniqueId(), "gui.delete.no"))
                .lore(LangManager.get(player.getUniqueId(), "gui.delete.no_lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        String expectedTitle = LangManager.get(player.getUniqueId(), "gui.delete.title");
        if (!e.getView().getTitle().equals(expectedTitle)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();

        if (slot == YES_SLOT) {
            MailDataManager.getInstance().removeMail(player.getUniqueId(), mail);
            player.sendMessage(LangManager.get(player.getUniqueId(), "mail.deleted"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            parentGui.open(player, returnPage);
        } else if (slot == NO_SLOT) {
            player.sendMessage(LangManager.get(player.getUniqueId(), "mail.delete_cancel"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            parentGui.open(player, returnPage);
        }
    }
}
