package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.*;

public class MailTimeSelectGUI implements Listener {

    private final MailManager plugin;
    private final MailSendGUI caller;
    private final Map<UUID, Inventory> inventoryMap = new HashMap<>();
    private final Map<UUID, long[]> timeMap = new HashMap<>();

    public MailTimeSelectGUI(MailManager plugin, MailSendGUI caller) {
        this.plugin = plugin;
        this.caller = caller;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        Inventory inv = Bukkit.createInventory(player, 36, LangUtil.get(uuid, "gui.time.title"));
        inventoryMap.put(uuid, inv);
        timeMap.put(uuid, new long[]{0, 0, 0, 0, 0, 0}); // 초, 분, 시, 일, 월, 년

        inv.setItem(10, new ItemBuilder(Material.REDSTONE).name("초").build());
        inv.setItem(11, new ItemBuilder(Material.REDSTONE).name("분").build());
        inv.setItem(12, new ItemBuilder(Material.REDSTONE).name("시").build());
        inv.setItem(13, new ItemBuilder(Material.REDSTONE).name("일").build());
        inv.setItem(14, new ItemBuilder(Material.REDSTONE).name("월").build());
        inv.setItem(15, new ItemBuilder(Material.REDSTONE).name("년").build());
        inv.setItem(16, new ItemBuilder(Material.BEDROCK).name(LangUtil.get(uuid, "gui.time.permanent")).build());

        inv.setItem(31, new ItemBuilder(Material.LIME_CONCRETE).name(LangUtil.get(uuid, "gui.confirm")).build());
        inv.setItem(35, new ItemBuilder(Material.BARRIER).name(LangUtil.get(uuid, "gui.back")).build());

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
        long[] time = timeMap.get(uuid);

        if (slot >= 10 && slot <= 15 && event.getClick().isLeftClick()) {
            time[slot - 10] = Math.max(0, time[slot - 10] - 1);
        } else if (slot >= 10 && slot <= 15 && event.getClick().isRightClick()) {
            time[slot - 10]++;
        } else if (slot == 16) {
            caller.setExpire(uuid, Long.MAX_VALUE);
            caller.open(player);
        } else if (slot == 31) {
            long expireMillis = System.currentTimeMillis()
                    + time[0] * 1000L           // 초
                    + time[1] * 60 * 1000L      // 분
                    + time[2] * 3600 * 1000L    // 시
                    + time[3] * 86400 * 1000L   // 일
                    + time[4] * 2592000 * 1000L // 월 (30일)
                    + time[5] * 31536000 * 1000L; // 년 (365일)

            caller.setExpire(uuid, expireMillis);
            caller.open(player);
        } else if (slot == 35) {
            caller.open(player);
        }
    }
}
