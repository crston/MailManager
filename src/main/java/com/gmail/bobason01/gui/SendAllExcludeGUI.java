package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
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

public class SendAllExcludeGUI implements Listener {

    private final MailManager plugin;
    private final Map<UUID, Inventory> inventoryMap = new HashMap<>();
    private final Map<UUID, Integer> pageMap = new HashMap<>();

    // 발신자 UUID → 제외 대상 UUID 목록
    private static final Map<UUID, Set<UUID>> excludeMap = new HashMap<>();

    public SendAllExcludeGUI(MailManager plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        UUID sender = player.getUniqueId();
        int page = pageMap.getOrDefault(sender, 0);
        Inventory gui = Bukkit.createInventory(player, 54, LangUtil.get(sender, "gui.exclude.title"));
        inventoryMap.put(sender, gui);

        List<OfflinePlayer> players = Arrays.asList(Bukkit.getOfflinePlayers());
        players.sort(Comparator.comparing(p -> Optional.ofNullable(p.getName()).orElse("~")));

        Set<UUID> excluded = excludeMap.computeIfAbsent(sender, k -> new HashSet<>());

        int start = page * 36;
        int end = Math.min(start + 36, players.size());

        int slot = 9;
        for (int i = start; i < end; i++) {
            OfflinePlayer target = players.get(i);
            if (!target.hasPlayedBefore() || target.getName() == null || target.getUniqueId().equals(sender))
                continue;

            boolean isExcluded = excluded.contains(target.getUniqueId());

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            assert meta != null;
            meta.setOwningPlayer(target);
            meta.setDisplayName((isExcluded ? "§c" : "§a") + target.getName());

            List<String> lore = new ArrayList<>();
            lore.add(LangUtil.get(sender, isExcluded ? "gui.exclude.selected" : "gui.exclude.unselected"));
            meta.setLore(lore);
            skull.setItemMeta(meta);

            gui.setItem(slot++, skull);
        }

        gui.setItem(48, new ItemBuilder(Material.ARROW).name(LangUtil.get(sender, "gui.previous")).build());
        gui.setItem(50, new ItemBuilder(Material.ARROW).name(LangUtil.get(sender, "gui.next")).build());
        gui.setItem(53, new ItemBuilder(Material.BARRIER).name(LangUtil.get(sender, "gui.back")).build());

        player.openInventory(gui);
    }

    public static boolean isExcluded(UUID sender, UUID target) {
        return excludeMap.getOrDefault(sender, Collections.emptySet()).contains(target);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID sender = player.getUniqueId();

        Inventory gui = inventoryMap.get(sender);
        if (gui == null || !event.getInventory().equals(gui)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot >= 9 && slot < 45) {
            ItemStack clicked = gui.getItem(slot);
            if (clicked == null || !(clicked.getItemMeta() instanceof SkullMeta meta)) return;
            OfflinePlayer target = meta.getOwningPlayer();
            if (target == null) return;

            UUID targetId = target.getUniqueId();
            Set<UUID> excluded = excludeMap.computeIfAbsent(sender, k -> new HashSet<>());

            if (excluded.contains(targetId)) {
                excluded.remove(targetId);
            } else {
                excluded.add(targetId);
            }

            open(player); // refresh
        } else if (slot == 48) {
            int page = pageMap.getOrDefault(sender, 0);
            if (page > 0) {
                pageMap.put(sender, page - 1);
                open(player);
            }
        } else if (slot == 50) {
            pageMap.put(sender, pageMap.getOrDefault(sender, 0) + 1);
            open(player);
        } else if (slot == 53) {
            new MailSendAllGUI(plugin).open(player);
        }
    }
}
