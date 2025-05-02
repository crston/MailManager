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

    private final Plugin plugin;
    private final List<String> units = List.of("second", "minute", "hour", "day", "month", "year");

    public MailTimeSelectGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 36, LangUtil.get("gui.mail-time.title"));
        UUID uuid = player.getUniqueId();
        Map<String, Integer> time = MailService.getTimeData(uuid);

        for (int i = 0; i < units.size(); i++) {
            String unit = units.get(i);
            int value = time.getOrDefault(unit, 0);
            inv.setItem(10 + i, new ItemBuilder(Material.PAPER)
                    .name("§f" + LangUtil.get("gui.mail-time.unit." + unit) + ": " + value)
                    .build());
        }

        inv.setItem(16, ConfigLoader.getGuiItem("permanent"));
        inv.setItem(31, ConfigLoader.getGuiItem("select-complete"));
        inv.setItem(35, ConfigLoader.getGuiItem("back"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.mail-time.title"))) return;

        e.setCancelled(true);
        UUID uuid = player.getUniqueId();
        Map<String, Integer> time = MailService.getTimeData(uuid);
        int raw = e.getRawSlot();

        if (raw >= 10 && raw <= 15) {
            int index = raw - 10;
            String unit = units.get(index);
            int value = time.getOrDefault(unit, 0);
            if (e.getClick() == ClickType.LEFT) value--;
            if (e.getClick() == ClickType.RIGHT) value++;
            value = Math.max(value, 0);
            time.put(unit, value);
            MailService.setTimeData(uuid, time);
            open(player);
        }

        if (raw == 16) {
            MailService.setTimeData(uuid, new HashMap<>());
            player.sendMessage(LangUtil.get("gui.mail-time.permanent-set"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            open(player);
        }

        if (raw == 31 || raw == 35) {
            String context = MailService.getContext(uuid);
            if (context.equals("sendall")) {
                new MailSendAllGUI(plugin).open(player);
            } else {
                new MailSendGUI(plugin).open(player);
            }
        }
    }
}
