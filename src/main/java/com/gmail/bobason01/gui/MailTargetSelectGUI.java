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

import java.util.Objects;
import java.util.UUID;

public class MailTargetSelectGUI implements Listener {

    private static final int SELECT_COMPLETE_SLOT = 45;
    private static final int BACK_SLOT = 53;
    private static final int MAX_TARGETS = 45;

    private final Plugin plugin;

    public MailTargetSelectGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54, LangUtil.get("gui.mail-target.title"));

        int i = 0;
        for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
            if (i >= MAX_TARGETS) break;
            if (target.getUniqueId().equals(player.getUniqueId())) continue;

            String name = target.getName();
            if (name == null || name.length() > 16 || !target.hasPlayedBefore()) continue;

            ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("§f" + name)
                    .build();

            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                try {
                    meta.setOwningPlayer(target);
                    head.setItemMeta(meta);
                } catch (IllegalArgumentException ignored) {}
            }

            inv.setItem(i++, head);
        }

        inv.setItem(SELECT_COMPLETE_SLOT, ConfigLoader.getGuiItem("select-complete"));
        inv.setItem(BACK_SLOT, ConfigLoader.getGuiItem("back"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.mail-target.title"))) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD || !clicked.hasItemMeta()) return;

        int slot = e.getRawSlot();
        UUID playerId = player.getUniqueId();

        if (slot < MAX_TARGETS) {
            String name = Objects.requireNonNull(clicked.getItemMeta()).getDisplayName().replace("§f", "");
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);

            if (target.getUniqueId().equals(playerId)) {
                player.sendMessage(LangUtil.get("gui.mail-target.cannot-target-self"));
            } else {
                MailService.setTarget(playerId, target);
                new MailSendGUI(plugin).open(player);
            }
        } else if (slot == SELECT_COMPLETE_SLOT || slot == BACK_SLOT) {
            new MailSendGUI(plugin).open(player);
        }
    }
}