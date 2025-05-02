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

public class MailTargetSelectGUI implements Listener {

    private final MailManager plugin;
    private final MailSendGUI caller;
    private final Map<UUID, Inventory> inventoryMap = new HashMap<>();
    private final Map<UUID, Integer> pageMap = new HashMap<>();

    public MailTargetSelectGUI(MailManager plugin, MailSendGUI caller) {
        this.plugin = plugin;
        this.caller = caller;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        int page = pageMap.getOrDefault(uuid, 0);

        Inventory inv = Bukkit.createInventory(player, 54, LangUtil.get(uuid, "gui.target.title"));
        inventoryMap.put(uuid, inv);

        List<OfflinePlayer> players = new ArrayList<>();
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (p.hasPlayedBefore() && p.getName() != null &&
                    !p.getUniqueId().equals(uuid) &&
                    !BlacklistManager.isBlocked(uuid, p.getUniqueId())) {
                players.add(p);
            }
        }
        players.sort(Comparator.comparing(p -> Optional.ofNullable(p.getName()).orElse("~")));

        int start = page * 36;
        int end = Math.min(start + 36, players.size());

        int slot = 9;
        for (int i = start; i < end; i++) {
            OfflinePlayer target = players.get(i);

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            assert meta != null;
            meta.setOwningPlayer(target);
            meta.setDisplayName("§a" + target.getName());
            skull.setItemMeta(meta);

            inv.setItem(slot++, skull);
        }

        inv.setItem(48, new ItemBuilder(Material.ARROW).name(LangUtil.get(uuid, "gui.previous")).build());
        inv.setItem(50, new ItemBuilder(Material.ARROW).name(LangUtil.get(uuid, "gui.next")).build());
        inv.setItem(53, new ItemBuilder(Material.BARRIER).name(LangUtil.get(uuid, "gui.back")).build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Inventory inv = inventoryMap.get(uuid);
        if (inv == null || !event.getInventory().equals(inv)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot >= 9 && slot <= 44) {
            ItemStack item = inv.getItem(slot);
            if (item == null || !(item.getItemMeta() instanceof SkullMeta meta)) return;
            OfflinePlayer target = meta.getOwningPlayer();
            if (target == null || target.getName() == null) return;

            // 대상 설정 및 돌아가기
            caller.setTarget(uuid, target.getName());
            caller.open(player);
        } else if (slot == 48) {
            int page = pageMap.getOrDefault(uuid, 0);
            if (page > 0) {
                pageMap.put(uuid, page - 1);
                open(player);
            }
        } else if (slot == 50) {
            int page = pageMap.getOrDefault(uuid, 0);
            pageMap.put(uuid, page + 1);
            open(player);
        } else if (slot == 53) {
            caller.open(player);
        }
    }
}
