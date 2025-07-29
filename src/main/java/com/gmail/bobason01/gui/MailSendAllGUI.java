package com.gmail.bobason01.gui;

import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class MailSendAllGUI implements Listener {

    private static final int SLOT_TIME = 10;
    private static final int SLOT_EXCLUDE = 12;
    private static final int SLOT_ITEM = 14;
    private static final int SLOT_CONFIRM = 16;
    private static final int SLOT_BACK = 26;

    private final Plugin plugin;
    private final Set<UUID> sentSet = new HashSet<>();

    public MailSendAllGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(uuid, "gui.sendall.title");
        Inventory inv = Bukkit.createInventory(player, 27, title);

        inv.setItem(SLOT_TIME, new ItemBuilder(Material.CLOCK)
                .name(LangManager.get(uuid, "gui.sendall.expire.name"))
                .lore(LangManager.get(uuid, "gui.sendall.expire.lore"))
                .build());

        inv.setItem(SLOT_EXCLUDE, new ItemBuilder(Material.BARRIER)
                .name(LangManager.get(uuid, "gui.sendall.exclude.name"))
                .lore(LangManager.get(uuid, "gui.sendall.exclude.lore"))
                .build());

        ItemStack attached = MailService.getAttachedItem(uuid);
        if (attached != null) {
            inv.setItem(SLOT_ITEM, attached);
        }

        inv.setItem(SLOT_CONFIRM, new ItemBuilder(Material.GREEN_WOOL)
                .name(LangManager.get(uuid, "gui.sendall.confirm.name"))
                .lore(LangManager.get(uuid, "gui.sendall.confirm.lore"))
                .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name("§c" + LangManager.get(uuid, "gui.back.name"))
                .lore(Collections.singletonList("§7" + LangManager.get(uuid, "gui.back.lore")))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        String expectedTitle = LangManager.get(uuid, "gui.sendall.title");
        if (!e.getView().getTitle().equals(expectedTitle)) return;

        int slot = e.getRawSlot();
        ClickType click = e.getClick();
        Inventory inv = e.getInventory();

        if (slot == SLOT_ITEM) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack newItem = inv.getItem(SLOT_ITEM);
                if (newItem != null && !newItem.getType().isAir()) {
                    MailService.setAttachedItem(uuid, newItem.clone());
                } else {
                    MailService.setAttachedItem(uuid, null);
                }
            }, 1L);
            return;
        }

        if (click.isShiftClick() || click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
            return;
        }

        if (slot >= inv.getSize()) return;
        e.setCancelled(true);

        switch (slot) {
            case SLOT_TIME -> new MailTimeSelectGUI(plugin).open(player);
            case SLOT_EXCLUDE -> new SendAllExcludeGUI(plugin).open(player);
            case SLOT_CONFIRM -> {
                if (sentSet.contains(uuid)) {
                    player.sendMessage(LangManager.get(uuid, "mail.sendall.cooldown"));
                    return;
                }

                ItemStack item = MailService.getAttachedItem(uuid);
                if (item == null || item.getType().isAir()) {
                    player.sendMessage(LangManager.get(uuid, "mail.sendall.no_item"));
                    return;
                }

                MailService.sendAll(player, plugin);
                MailService.setAttachedItem(uuid, null);
                sentSet.add(uuid);
                player.sendMessage(LangManager.get(uuid, "mail.sendall.success"));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.closeInventory();
            }
            case SLOT_BACK -> new MailGUI(plugin).open(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        String expectedTitle = LangManager.get(player.getUniqueId(), "gui.sendall.title");
        if (!e.getView().getTitle().equals(expectedTitle)) return;

        UUID uuid = player.getUniqueId();

        if (sentSet.contains(uuid)) return;

        ItemStack item = e.getInventory().getItem(SLOT_ITEM);
        if (item != null && !item.getType().isAir()) {
            player.getInventory().addItem(item);
            MailService.setAttachedItem(uuid, null);
        }
    }
}
