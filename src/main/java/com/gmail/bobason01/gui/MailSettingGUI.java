package com.gmail.bobason01.gui;

import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ConfigLoader;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class MailSettingGUI implements Listener {
    private final Plugin plugin;

    public MailSettingGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, "⚙ 메일 설정");
        UUID uuid = player.getUniqueId();
        boolean notify = MailDataManager.getInstance().isNotifyEnabled(uuid);

        inv.setItem(11, new ItemBuilder(notify ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(notify ? "§a메일 수신 알림: ON" : "§7메일 수신 알림: OFF")
                .lore("§7메일 수신 시 알림 여부를 설정합니다.")
                .build());

        inv.setItem(15, ConfigLoader.getGuiItem("blacklist"));
        inv.setItem(26, ConfigLoader.getGuiItem("back"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals("⚙ 메일 설정")) return;

        e.setCancelled(true);
        UUID uuid = player.getUniqueId();

        switch (e.getRawSlot()) {
            case 11 -> {
                boolean newState = MailDataManager.getInstance().toggleNotify(uuid);
                player.sendMessage(newState ? "§a메일 수신 알림이 활성화되었습니다." : "§7메일 수신 알림이 비활성화되었습니다.");
                open(player);
            }
            case 15 -> new BlacklistSelectGUI(plugin).open(player);
            case 26 -> new MailGUI(plugin).open(player);
        }
    }
}
