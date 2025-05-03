package com.gmail.bobason01.gui;

import com.gmail.bobason01.mail.MailDataManager;
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
import java.util.Set;
import java.util.UUID;

public class BlacklistSelectGUI implements Listener {

    private static final int MAX_PLAYERS = 45;
    private static final int BACK_BUTTON_SLOT = 53;

    private final Plugin plugin;

    public BlacklistSelectGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54, LangUtil.get("gui.blacklist.title"));
        Set<UUID> blocked = MailDataManager.getInstance().getBlacklist(player.getUniqueId());

        int i = 0;
        for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
            if (i >= MAX_PLAYERS) break;
            if (target.getUniqueId().equals(player.getUniqueId())) continue;

            String name = target.getName();
            if (name == null || name.length() > 16 || !target.hasPlayedBefore()) continue;

            ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("§f" + name)
                    .lore(blocked.contains(target.getUniqueId())
                            ? LangUtil.get("gui.blacklist.blocked")
                            : LangUtil.get("gui.blacklist.allowed"))
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

        inv.setItem(BACK_BUTTON_SLOT, ConfigLoader.getGuiItem("back"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.blacklist.title"))) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        ItemStack clicked = e.getCurrentItem();

        if (slot < MAX_PLAYERS) {
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD || !clicked.hasItemMeta()) return;

            String name = Objects.requireNonNull(clicked.getItemMeta()).getDisplayName().replace("§f", "");
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(LangUtil.get("gui.blacklist.cannot-block-self"));
                return;
            }

            UUID targetId = target.getUniqueId();
            Set<UUID> blocked = MailDataManager.getInstance().getBlacklist(player.getUniqueId());

            if (blocked.contains(targetId)) {
                blocked.remove(targetId);
                player.sendMessage("§c" + target.getName() + LangUtil.get("gui.blacklist.removed"));
            } else {
                blocked.add(targetId);
                player.sendMessage("§a" + target.getName() + LangUtil.get("gui.blacklist.added"));
            }

            open(player);
        } else if (slot == BACK_BUTTON_SLOT) {
            new MailSettingGUI(plugin).open(player);
        }
    }
}