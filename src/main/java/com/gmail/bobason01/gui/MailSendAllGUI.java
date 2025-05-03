package com.gmail.bobason01.gui;

import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ConfigLoader;
import com.gmail.bobason01.utils.LangUtil;
import com.gmail.bobason01.utils.TimeUtil;
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MailSendAllGUI implements Listener {

    private final Plugin plugin;
    private final Set<UUID> sentSet = new HashSet<>();

    public MailSendAllGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        MailService.setContext(uuid, "sendall");

        Inventory inv = Bukkit.createInventory(player, 27, LangUtil.get("gui.mail-sendall.title"));

        Map<String, Integer> timeData = MailService.getTimeData(uuid);
        String formattedTime = TimeUtil.format(timeData);
        ItemStack clockItem = new ItemBuilder(Material.CLOCK)
                .name("§e" + LangUtil.get("gui.mail-send.time"))
                .lore("§7" + formattedTime)
                .build();

        inv.setItem(10, clockItem);
        inv.setItem(12, ConfigLoader.getGuiItem("exclude"));

        ItemStack item = MailService.getAttachedItem(uuid);
        if (item != null) inv.setItem(14, item);

        inv.setItem(16, ConfigLoader.getGuiItem("confirm"));
        inv.setItem(18, ConfigLoader.getGuiItem("back"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.mail-sendall.title"))) return;

        ClickType click = e.getClick();
        if (click.isShiftClick() || click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
            return;
        }

        int slot = e.getRawSlot();
        UUID uuid = player.getUniqueId();

        Set<Integer> allowedSlots = Set.of(10, 12, 14, 16, 18);
        if (slot < 27 && !allowedSlots.contains(slot)) {
            e.setCancelled(true);
            return;
        }

        switch (slot) {
            case 10 -> new MailTimeSelectGUI(plugin).open(player);
            case 12 -> new SendAllExcludeGUI(plugin).open(player);
            case 14 -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack newItem = e.getInventory().getItem(14);
                MailService.setAttachedItem(uuid, (newItem != null && !newItem.getType().isAir()) ? newItem.clone() : null);
            }, 1L);
            case 16 -> {
                ItemStack currentItem = e.getInventory().getItem(14);
                MailService.setAttachedItem(uuid, (currentItem != null && !currentItem.getType().isAir()) ? currentItem.clone() : null);

                ItemStack item = MailService.getAttachedItem(uuid);
                if (item == null || item.getType() == Material.AIR) {
                    player.sendMessage(LangUtil.get("mail.invalid-args"));
                    return;
                }

                MailService.sendAll(player, plugin);
                sentSet.add(uuid);
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
            }
            case 18 -> player.performCommand("mail");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.mail-sendall.title"))) return;

        UUID uuid = player.getUniqueId();

        if (sentSet.remove(uuid)) return;

        ItemStack item = e.getInventory().getItem(14);
        if (item != null && item.getType() != Material.AIR) {
            player.getInventory().addItem(item);
        }

        MailService.setAttachedItem(uuid, null);
    }
}