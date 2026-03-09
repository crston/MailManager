package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MailAttachGUI implements Listener {

    private static final int SIZE = 54;
    private static final int BACK_SLOT = 53;
    private final Plugin plugin;

    public MailAttachGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    private static class AttachHolder implements InventoryHolder {
        private final Inventory inv;
        private final UUID owner;
        private final Class<?> returnGuiClass;

        public AttachHolder(UUID owner, Class<?> returnGuiClass, String title) {
            this.owner = owner;
            this.returnGuiClass = returnGuiClass;
            this.inv = Bukkit.createInventory(this, SIZE, title);
        }

        @Override
        public @NotNull Inventory getInventory() { return inv; }
        public UUID getOwner() { return owner; }
        public Class<?> getReturnGuiClass() { return returnGuiClass; }
    }

    public void open(Player player, Class<?> returnGuiClass) {
        UUID uuid = player.getUniqueId();
        AttachHolder holder = new AttachHolder(uuid, returnGuiClass, LangManager.get(uuid, "gui.attach.title"));
        Inventory inv = holder.getInventory();

        List<ItemStack> items = MailService.getAttachedItems(uuid);
        for (int i = 0; i < Math.min(items.size(), SIZE - 1); i++) {
            ItemStack item = items.get(i);
            if (item != null && item.getType() != Material.AIR) {
                inv.setItem(i, item.clone());
            }
        }

        inv.setItem(BACK_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name(LangManager.get(uuid, "gui.back.name"))
                .lore(LangManager.getList(uuid, "gui.back.lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AttachHolder holder)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        if (e.getRawSlot() == BACK_SLOT) {
            e.setCancelled(true);
            saveAttachedItems(holder);
            returnToParent(player, holder.getReturnGuiClass());
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof AttachHolder && e.getRawSlots().contains(BACK_SLOT)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof AttachHolder holder) {
            saveAttachedItems(holder);
        }
    }

    private void saveAttachedItems(AttachHolder holder) {
        Inventory inv = holder.getInventory();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < SIZE - 1; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        MailService.setAttachedItems(holder.getOwner(), items);
    }

    private void returnToParent(Player player, Class<?> returnClass) {
        if (returnClass == MailSendGUI.class) {
            MailManager.getInstance().mailSendGUI.open(player);
        } else if (returnClass == MailSendAllGUI.class) {
            MailManager.getInstance().mailSendAllGUI.open(player);
        }
    }
}