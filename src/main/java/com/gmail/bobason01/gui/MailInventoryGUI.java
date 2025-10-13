package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MailInventoryGUI implements Listener, InventoryHolder {

    private static final int SIZE = 36;

    private final int invId;
    private final boolean editable;
    private Inventory inv;
    private UUID viewer;

    public MailInventoryGUI(int invId, boolean editable) {
        this.invId = invId;
        this.editable = editable;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void open(Player player) {
        this.viewer = player.getUniqueId();
        String titleKey = editable ? "gui.mailinv.edit.title" : "gui.mailinv.view.title";
        String title = LangManager.get(viewer, titleKey).replace("%id%", String.valueOf(invId));

        inv = Bukkit.createInventory(this, SIZE, title);
        ItemStack[] contents = MailDataManager.getInstance().getInventory(invId);
        if (contents != null) inv.setContents(contents);
        player.openInventory(inv);
    }

    private void save() {
        if (editable && inv != null) {
            MailDataManager.getInstance().saveInventory(invId, inv.getContents());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailInventoryGUI gui)) return;

        if (!gui.editable) {
            e.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTaskLater(MailManager.getInstance(), gui::save, 1L);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailInventoryGUI gui)) return;

        if (!gui.editable) {
            e.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTaskLater(MailManager.getInstance(), gui::save, 1L);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailInventoryGUI gui)) return;

        if (e.getPlayer() instanceof Player p) {
            UUID uuid = p.getUniqueId();
            if (gui.editable) {
                gui.save();
                p.sendMessage(LangManager.get(uuid, "gui.mailinv.saved")
                        .replace("%id%", String.valueOf(gui.invId)));
            } else {
                p.sendMessage(LangManager.get(uuid, "gui.mailinv.closed")
                        .replace("%id%", String.valueOf(gui.invId)));
            }
        }
    }
}
