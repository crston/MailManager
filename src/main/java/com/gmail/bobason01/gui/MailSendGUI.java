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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;

public class MailSendGUI implements Listener {

    private final Plugin plugin;

    public MailSendGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, LangUtil.get("gui.mail-send.title"));

        UUID uuid = player.getUniqueId();
        Map<String, Integer> time = MailService.getTimeData(uuid);
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

        if (item != null) {
            inv.setItem(14, item);
        }

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
        if (e.getView().getTitle().equals(LangUtil.get("gui.mail-send.title"))) {
            e.setCancelled(e.getRawSlot() < 27);
            int slot = e.getRawSlot();

            switch (slot) {
                case 10 -> new MailTimeSelectGUI(plugin).open(player);
                case 12 -> new MailTargetSelectGUI(plugin).open(player);
                case 16 -> MailService.send(player);
                case 18 -> player.performCommand("mail");
            }
        }
    }
}
