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

import java.util.*;

public class MailAttachGUI implements Listener, InventoryHolder {

    private static final int SIZE = 54;   // 6줄
    private static final int BACK_SLOT = 53; // 뒤로가기 버튼

    private final Plugin plugin;
    private Inventory inv;
    private UUID owner;
    private Class<?> returnGui;

    public MailAttachGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void open(Player player, Class<?> returnGui) {
        this.owner = player.getUniqueId();
        this.returnGui = returnGui;

        String title = LangManager.get(owner, "gui.attach.title");
        inv = Bukkit.createInventory(this, SIZE, title);

        // 기존 첨부 아이템 불러오기
        List<ItemStack> items = MailService.getAttachedItems(owner);
        for (int i = 0; i < Math.min(items.size(), SIZE - 1); i++) {
            inv.setItem(i, items.get(i).clone());
        }

        // 뒤로가기 버튼
        inv.setItem(BACK_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON).clone())
                .name("§c" + LangManager.get(owner, "gui.back.name"))
                .lore(LangManager.get(owner, "gui.back.lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailAttachGUI gui)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= SIZE) return;

        if (rawSlot == BACK_SLOT) {
            e.setCancelled(true);
            gui.saveAttachedItems();
            gui.refreshParent(player);
            MailManager manager = MailManager.getInstance();
            if (returnGui == MailSendGUI.class) {
                manager.mailSendGUI.open(player);
            } else if (returnGui == MailSendAllGUI.class) {
                manager.mailSendAllGUI.open(player);
            }
            return;
        }

        // 아이템 변화 즉시 반영
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            gui.saveAttachedItems();
            gui.refreshParent(player);
        }, 1L);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailAttachGUI gui)) return;

        if (e.getRawSlots().contains(BACK_SLOT)) {
            e.setCancelled(true);
            return;
        }

        // 드래그도 반영
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            gui.saveAttachedItems();
            if (e.getWhoClicked() instanceof Player player) {
                gui.refreshParent(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailAttachGUI gui)) return;
        gui.saveAttachedItems();
        if (e.getPlayer() instanceof Player player) {
            gui.refreshParent(player);
        }
    }

    private void saveAttachedItems() {
        if (owner == null || inv == null) return;

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < SIZE - 1; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }

        MailService.setAttachedItems(owner, items);
    }

    private void refreshParent(Player player) {
        if (returnGui == MailSendGUI.class) {
            MailManager.getInstance().mailSendGUI.refresh(player);
        } else if (returnGui == MailSendAllGUI.class) {
            MailManager.getInstance().mailSendAllGUI.refresh(player);
        }
    }
}
