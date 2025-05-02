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
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public class MailTargetSelectGUI implements Listener {

    private final Plugin plugin;

    public MailTargetSelectGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54, LangUtil.get("gui.mail-target.title"));
        int i = 0;
        for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
            if (i >= 45) break;
            if (target.getUniqueId().equals(player.getUniqueId())) continue;

            ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
                    .owner(target.getName())
                    .name("§f" + target.getName())
                    .build();
            inv.setItem(i++, head);
        }

        inv.setItem(45, ConfigLoader.getGuiItem("select-complete"));
        inv.setItem(53, ConfigLoader.getGuiItem("back"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.mail-target.title"))) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = e.getRawSlot();

        if (slot < 45) {
            String name = Objects.requireNonNull(clicked.getItemMeta()).getDisplayName().replace("§f", "");
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(LangUtil.get("gui.mail-target.cannot-target-self"));
                return;
            }
            MailService.setTarget(player.getUniqueId(), target);
            new MailSendGUI(plugin).open(player);
        } else if (slot == 45 || slot == 53) {
            new MailSendGUI(plugin).open(player);
        }
    }
}
