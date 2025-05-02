package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.mail.BlacklistManager;
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

import java.util.*;

public class BlacklistSelectGUI implements Listener {

    private final MailManager plugin;
    private final Map<UUID, Inventory> inventoryMap = new HashMap<>();
    private final Map<UUID, Integer> pageMap = new HashMap<>();

    public BlacklistSelectGUI(MailManager plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        UUID playerId = player.getUniqueId();
        int page = pageMap.getOrDefault(playerId, 0);

        Inventory gui = Bukkit.createInventory(player, 54, LangUtil.get(playerId, "gui.blacklist.title"));
        inventoryMap.put(playerId, gui);

        List<OfflinePlayer> allPlayers = Arrays.asList(Bukkit.getOfflinePlayers());
        allPlayers.sort(Comparator.comparing(p -> Optional.ofNullable(p.getName()).orElse("~")));

        int start = page * 36;
        int end = Math.min(start + 36, allPlayers.size());

        int index = 9; // 시작 슬롯
        for (int i = start; i < end; i++) {
            OfflinePlayer target = allPlayers.get(i);
            if (!target.hasPlayedBefore() || target.getName() == null || target.getUniqueId().equals(playerId))
                continue;

            boolean isBlocked = BlacklistManager.isBlocked(playerId, target.getUniqueId());

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            assert meta != null;
            meta.setOwningPlayer(target);
            meta.setDisplayName((isBlocked ? "§c" : "§a") + target.getName());
            List<String> lore = new ArrayList<>();
            lore.add(LangUtil.get(playerId, isBlocked ? "gui.blacklist.is" : "gui.blacklist.not"));
            meta.setLore(lore);
            skull.setItemMeta(meta);

            gui.setItem(index++, skull);
        }

        gui.setItem(48, new ItemBuilder(Material.ARROW).name(LangUtil.get(playerId, "gui.previous")).build());
        gui.setItem(50, new ItemBuilder(Material.ARROW).name(LangUtil.get(playerId, "gui.next")).build());
        gui.setItem(53, new ItemBuilder(Material.BARRIER).name(LangUtil.get(playerId, "gui.back")).build());

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID playerId = player.getUniqueId();

        Inventory gui = inventoryMap.get(playerId);
        if (gui == null || !event.getView().getTopInventory().equals(gui)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot >= 9 && slot < 45) {
            ItemStack clicked = gui.getItem(slot);
            if (clicked == null || !(clicked.getItemMeta() instanceof SkullMeta meta)) return;
            OfflinePlayer target = meta.getOwningPlayer();
            if (target == null) return;

            BlacklistManager.toggle(playerId, target.getUniqueId());
            open(player);

        } else if (slot == 48) {
            int page = pageMap.getOrDefault(playerId, 0);
            if (page > 0) {
                pageMap.put(playerId, page - 1);
                open(player);
            }
        } else if (slot == 50) {
            pageMap.put(playerId, pageMap.getOrDefault(playerId, 0) + 1);
            open(player);
        } else if (slot == 53) {
            player.closeInventory();
        }
    }
}
