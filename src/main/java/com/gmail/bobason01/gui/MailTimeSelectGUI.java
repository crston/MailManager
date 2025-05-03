package com.gmail.bobason01.gui;

import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ConfigLoader;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class MailTimeSelectGUI implements Listener {

    private static final List<String> TIME_UNITS = List.of("second", "minute", "hour", "day", "month", "year");

    private static final int UNIT_START_SLOT = 10;
    private static final int PERMANENT_SLOT = 16;
    private static final int CONFIRM_SLOT = 31;
    private static final int BACK_SLOT = 35;

    private final Plugin plugin;

    public MailTimeSelectGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> time = MailService.getTimeData(uuid);

        Inventory inv = Bukkit.createInventory(player, 36, LangUtil.get("gui.mail-time.title"));

        for (int i = 0; i < TIME_UNITS.size(); i++) {
            String unit = TIME_UNITS.get(i);
            int value = time.getOrDefault(unit, 0);
            inv.setItem(UNIT_START_SLOT + i, new ItemBuilder(Material.PAPER)
                    .name("§f" + LangUtil.get("gui.mail-time.unit." + unit) + ": " + value)
                    .build());
        }

        inv.setItem(PERMANENT_SLOT, ConfigLoader.getGuiItem("permanent"));
        inv.setItem(CONFIRM_SLOT, ConfigLoader.getGuiItem("select-complete"));
        inv.setItem(BACK_SLOT, ConfigLoader.getGuiItem("back"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.mail-time.title"))) return;

        e.setCancelled(true);

        UUID uuid = player.getUniqueId();
        Map<String, Integer> time = MailService.getTimeData(uuid);
        int slot = e.getRawSlot();

        if (slot >= UNIT_START_SLOT && slot < UNIT_START_SLOT + TIME_UNITS.size()) {
            int index = slot - UNIT_START_SLOT;
            String unit = TIME_UNITS.get(index);
            int value = time.getOrDefault(unit, 0);

            if (e.getClick() == ClickType.LEFT) value = Math.max(0, value - 1);
            if (e.getClick() == ClickType.RIGHT) value += 1;

            time.put(unit, value);
            MailService.setTimeData(uuid, time);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            open(player);
            return;
        }

        if (slot == PERMANENT_SLOT) {
            MailService.setTimeData(uuid, new HashMap<>());
            player.sendMessage(LangUtil.get("gui.mail-time.permanent-set"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            open(player);
            return;
        }

        if (slot == CONFIRM_SLOT || slot == BACK_SLOT) {
            String context = MailService.getContext(uuid);
            if ("sendall".equals(context)) {
                new MailSendAllGUI(plugin).open(player);
            } else {
                new MailSendGUI(plugin).open(player);
            }
        }
    }
}
