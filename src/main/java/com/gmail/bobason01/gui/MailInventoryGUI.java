package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.commands.MailCommand;
import com.gmail.bobason01.lang.LangManager;
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
        ItemStack[] contents = MailCommand.getInventory(invId);
        if (contents != null) inv.setContents(contents);
        player.openInventory(inv);
    }

    private void save() {
        if (editable && inv != null) {
            MailCommand.saveInventory(invId, inv.getContents());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailInventoryGUI gui)) return;

        if (!gui.editable) {
            e.setCancelled(true);
            return;
        }

        // 편집 모드 → 변경사항 즉시 저장
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

        if (gui.editable) {
            gui.save();
            if (e.getPlayer() instanceof Player p) {
                p.sendMessage(LangManager.get(p.getUniqueId(), "gui.mailinv.saved")
                        .replace("%id%", String.valueOf(gui.invId)));
            }
        } else {
            if (e.getPlayer() instanceof Player p) {
                p.sendMessage(LangManager.get(p.getUniqueId(), "gui.mailinv.closed")
                        .replace("%id%", String.valueOf(gui.invId)));
            }
        }
    }
}
