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

public class SendAllExcludeGUI implements Listener {

    private final Plugin plugin;

    public SendAllExcludeGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54, LangUtil.get("gui.sendall-exclude.title"));
        Set<UUID> excluded = MailDataManager.getInstance().getExcluded(player.getUniqueId());

        int i = 0;
        for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
            if (i >= 45) break;
            if (target.getUniqueId().equals(player.getUniqueId())) continue;

            String name = target.getName();
            if (name == null || name.length() > 16 || !target.hasPlayedBefore()) continue;

            ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("§f" + name)
                    .lore(excluded.contains(target.getUniqueId())
                            ? LangUtil.get("gui.sendall-exclude.excluded")
                            : LangUtil.get("gui.sendall-exclude.included"))
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

        inv.setItem(53, ConfigLoader.getGuiItem("back"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.sendall-exclude.title"))) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        Set<UUID> excluded = MailDataManager.getInstance().getExcluded(player.getUniqueId());

        if (slot < 45) {
            String name = Objects.requireNonNull(clicked.getItemMeta()).getDisplayName().replace("§f", "");
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(LangUtil.get("gui.sendall-exclude.cannot-exclude-self"));
                return;
            }

            UUID uuid = target.getUniqueId();
            if (excluded.contains(uuid)) excluded.remove(uuid);
            else excluded.add(uuid);

            open(player);
        } else if (slot == 53) {
            new MailSendAllGUI(plugin).open(player);
        }
    }
}