package com.gmail.bobason01.gui;

import com.gmail.bobason01.config.ConfigLoader;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class MailSettingGUI implements Listener {

    private static final int NOTIFY_SLOT = 11;
    private static final int BLACKLIST_SLOT = 15;
    private static final int BACK_SLOT = 26;

    private static final String GUI_TITLE = "우편 설정";

    private final Plugin plugin;

    public MailSettingGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        player.openInventory(createInventory(player));
    }

    private Inventory createInventory(Player player) {
        UUID uuid = player.getUniqueId();
        boolean notifyEnabled = MailDataManager.getInstance().isNotifyEnabled(uuid);

        String displayName = notifyEnabled ? "§a알림: 켜짐" : "§7알림: 꺼짐";
        Material dye = notifyEnabled ? Material.LIME_DYE : Material.GRAY_DYE;

        ItemStack notifyItem = new ItemBuilder(dye)
                .name(displayName)
                .lore("§7우편 수신 시 알림을 받을지 여부를 설정합니다.")
                .build();

        Inventory inv = Bukkit.createInventory(player, 27, GUI_TITLE);
        inv.setItem(NOTIFY_SLOT, notifyItem);
        inv.setItem(BLACKLIST_SLOT, ConfigLoader.getGuiItem("blacklist")); // 블랙리스트 항목은 ConfigLoader에서 다국어화 가능
        inv.setItem(BACK_SLOT, ConfigLoader.getGuiItem("back"));

        return inv;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;

        UUID uuid = player.getUniqueId();

        switch (e.getRawSlot()) {
            case NOTIFY_SLOT -> {
                boolean newState = MailDataManager.getInstance().toggleNotification(uuid);
                if (newState) {
                    player.sendMessage("§a[우편] 알림이 활성화되었습니다.");
                } else {
                    player.sendMessage("§7[우편] 알림이 비활성화되었습니다.");
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, newState ? 1.2f : 0.8f);
                open(player);
            }
            case BLACKLIST_SLOT -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new BlacklistSelectGUI(plugin).open(player);
            }
            case BACK_SLOT -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new MailGUI(plugin).open(player);
            }
        }
    }
}
