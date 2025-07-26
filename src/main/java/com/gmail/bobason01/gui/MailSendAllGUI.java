package com.gmail.bobason01.gui;

import com.gmail.bobason01.config.ConfigLoader;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, "Send Mail to All");
        UUID uuid = player.getUniqueId();

        inv.setItem(SLOT_TIME, new ItemBuilder(Material.CLOCK)
                .name("§eExpiration Time")
                .lore("§7Click to set mail expiration time.")
                .build());

        inv.setItem(SLOT_EXCLUDE, new ItemBuilder(Material.BARRIER)
                .name("§cExclude Players")
                .lore("§7Click to select players to exclude.")
                .build());

        ItemStack attached = MailService.getAttachedItem(uuid);
        if (attached != null) {
            inv.setItem(SLOT_ITEM, attached);
        }

        inv.setItem(SLOT_CONFIRM, new ItemBuilder(Material.GREEN_WOOL)
                .name("§aSend to All")
                .lore("§7Click to send mail to all players.\n§7Make sure an item is attached.")
                .build());

        inv.setItem(SLOT_BACK, ConfigLoader.getGuiItem("back"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals("Send Mail to All")) return;

        e.setCancelled(true);

        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();

        switch (slot) {
            case SLOT_TIME -> new MailTimeSelectGUI(plugin).open(player);
            case SLOT_EXCLUDE -> new SendAllExcludeGUI(plugin).open(player);
            case SLOT_ITEM -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack newItem = e.getInventory().getItem(SLOT_ITEM);
                if (newItem != null && !newItem.getType().isAir()) {
                    MailService.setAttachedItem(uuid, newItem);
                }
            }, 1L);
            case SLOT_CONFIRM -> {
                if (sentSet.contains(uuid)) {
                    player.sendMessage("§c[Mail] You've already sent mail. Please wait.");
                    return;
                }
                ItemStack item = MailService.getAttachedItem(uuid);
                if (item == null || item.getType().isAir()) {
                    player.sendMessage("§c[Mail] You must attach an item before sending.");
                    return;
                }
                MailService.sendAll(player, plugin);
                sentSet.add(uuid);
                player.sendMessage("§a[Mail] Mail successfully sent to all players.");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.closeInventory();
            }
            case SLOT_BACK -> new MailGUI(plugin).open(player);
        }
    }
}
