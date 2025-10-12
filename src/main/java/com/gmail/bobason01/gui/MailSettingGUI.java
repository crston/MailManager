package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MailSettingGUI implements Listener, InventoryHolder {

    private static final int NOTIFY_SLOT = 10;
    private static final int LANGUAGE_SLOT = 12;
    private static final int BLACKLIST_SLOT = 14;
    private static final int MULTI_SELECT_SLOT = 16;
    private static final int BACK_SLOT = 26;

    private final Plugin plugin;

    public MailSettingGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 27);
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        boolean notifyEnabled = MailDataManager.getInstance().isNotify(uuid);

        String title = LangManager.get(uuid, "gui.setting.title");
        Inventory inv = Bukkit.createInventory(this, 27, title);

        // 알림 버튼
        inv.setItem(NOTIFY_SLOT, new ItemBuilder(
                notifyEnabled ? ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_NOTIFY_ON)
                        : ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_NOTIFY_OFF))
                .name(LangManager.get(uuid, "gui.notify.name"))
                .lore(LangManager.getList(uuid, "gui.notify.lore"))
                .build());

        // 블랙리스트 버튼
        inv.setItem(BLACKLIST_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_BLACKLIST))
                .name(LangManager.get(uuid, "gui.blacklist.title"))
                .lore(LangManager.getList(uuid, "gui.blacklist.lore"))
                .build());

        // 언어 버튼
        inv.setItem(LANGUAGE_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_LANGUAGE))
                .name(LangManager.get(uuid, "gui.language.name"))
                .lore(LangManager.getList(uuid, "gui.language.lore"))
                .build());

        // 멀티 선택 버튼
        inv.setItem(MULTI_SELECT_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SELECT_BUTTON))
                .name(LangManager.get(uuid, "gui.mail.select_name"))
                .lore(LangManager.getList(uuid, "gui.mail.select_lore"))
                .build());

        // 뒤로가기 버튼
        inv.setItem(BACK_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(uuid, "gui.back.name"))
                .lore(LangManager.getList(uuid, "gui.back.lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSettingGUI) || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        UUID uuid = player.getUniqueId();
        MailManager manager = MailManager.getInstance();

        switch (e.getRawSlot()) {
            case NOTIFY_SLOT -> {
                boolean newState = MailDataManager.getInstance().toggleNotification(uuid);
                String key = newState ? "gui.notify.enabled" : "gui.notify.disabled";
                player.sendMessage(LangManager.get(uuid, key));
                ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SETTING_CHANGE);
                open(player);
            }
            case BLACKLIST_SLOT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.blacklistSelectGUI.open(player);
            }
            case LANGUAGE_SLOT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.languageSelectGUI.open(player);
            }
            case MULTI_SELECT_SLOT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailSelectGUI.open(player);
            }
            case BACK_SLOT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailGUI.open(player);
            }
        }
    }
}
