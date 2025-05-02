package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.LangUtil;
import com.gmail.bobason01.utils.MailNotifySettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MailSettingGUI implements Listener {

    private final MailManager plugin;
    private final Map<UUID, Inventory> inventoryMap = new HashMap<>();

    public MailSettingGUI(MailManager plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        Inventory inv = Bukkit.createInventory(player, 27, LangUtil.get(uuid, "gui.setting.title"));
        inventoryMap.put(uuid, inv);

        boolean notify = MailNotifySettingsManager.isNotifyEnabled(uuid);
        Material bell = notify ? Material.BELL : Material.REDSTONE;
        String bellName = LangUtil.get(uuid, notify ? "gui.setting.notify.on" : "gui.setting.notify.off");

        inv.setItem(11, new ItemBuilder(bell).name(bellName).build());
        inv.setItem(15, new ItemBuilder(Material.BOOK).name(LangUtil.get(uuid, "gui.setting.blacklist")).build());
        inv.setItem(26, new ItemBuilder(Material.BARRIER).name(LangUtil.get(uuid, "gui.back")).build());

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

        if (slot == 11) {
            MailNotifySettingsManager.toggle(uuid);
            MailNotifySettingsManager.save();
            player.sendMessage(LangUtil.get(uuid,
                    MailNotifySettingsManager.isNotifyEnabled(uuid)
                            ? "notify.enabled"
                            : "notify.disabled"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            open(player); // 갱신
        } else if (slot == 15) {
            new BlacklistSelectGUI(plugin).open(player);
        } else if (slot == 26) {
            player.closeInventory();
        }
    }
}
