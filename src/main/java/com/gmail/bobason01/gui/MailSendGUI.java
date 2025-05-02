package com.gmail.bobason01.gui;

import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ConfigLoader;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class MailSendGUI implements Listener {

    private final Plugin plugin;
    private final Set<UUID> sentSet = new HashSet<>();

    public MailSendGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, LangUtil.get("gui.mail-send.title"));
        UUID uuid = player.getUniqueId();

        OfflinePlayer target = MailService.getTarget(uuid);
        ItemStack item = MailService.getAttachedItem(uuid);

        inv.setItem(10, new ItemBuilder(Material.CLOCK)
                .name(LangUtil.get("gui.mail-send.time"))
                .lore(LangUtil.get("gui.mail-send.time-lore"))
                .build());

        ItemStack targetItem = new ItemBuilder(Material.PLAYER_HEAD)
                .name(LangUtil.get("gui.mail-send.target"))
                .lore(LangUtil.get("gui.mail-send.target-lore"))
                .build();
        if (target != null) {
            SkullMeta meta = (SkullMeta) targetItem.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                targetItem.setItemMeta(meta);
            }
        }
        inv.setItem(12, targetItem);

        if (item != null) inv.setItem(14, item);

        inv.setItem(16, new ItemBuilder(Material.GREEN_WOOL)
                .name(LangUtil.get("gui.mail-send.send"))
                .lore(LangUtil.get("gui.mail-send.send-lore"))
                .build());

        inv.setItem(18, ConfigLoader.getGuiItem("back"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.mail-send.title"))) return;

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
            case 12 -> new MailTargetSelectGUI(plugin).open(player);
            case 14 -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack newItem = e.getInventory().getItem(14);
                MailService.setAttachedItem(uuid, (newItem != null && !newItem.getType().isAir()) ? newItem.clone() : null);
            }, 1L);
            case 16 -> {
                ItemStack currentItem = e.getInventory().getItem(14);
                MailService.setAttachedItem(uuid, (currentItem != null && !currentItem.getType().isAir()) ? currentItem.clone() : null);
                boolean success = MailService.send(player);
                if (success) sentSet.add(uuid);
                player.closeInventory();
            }
            case 18 -> player.performCommand("mail");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.mail-send.title"))) return;

        UUID uuid = player.getUniqueId();

        if (sentSet.remove(uuid)) return;

        ItemStack item = e.getInventory().getItem(14);
        if (item != null && item.getType() != Material.AIR) {
            player.getInventory().addItem(item);
        }

        MailService.setAttachedItem(uuid, null);
    }
}
