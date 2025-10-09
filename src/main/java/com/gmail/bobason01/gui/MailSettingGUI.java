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

    private static final int NOTIFY_SLOT = 11;
    private static final int LANGUAGE_SLOT = 13;
    private static final int BLACKLIST_SLOT = 15;
    private static final int BACK_SLOT = 26;

    private final Plugin plugin;

    public MailSettingGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        boolean notifyEnabled = MailDataManager.getInstance().isNotify(uuid);

        // 알림 상태에 따라 다른 아이템 불러오기
        ItemStack notifyItem = (notifyEnabled
                ? ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_NOTIFY_ON)
                : ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_NOTIFY_OFF)).clone();

        String title = LangManager.get(lang, "gui.setting.title");
        Inventory inv = Bukkit.createInventory(this, 27, title);

        // 알림 버튼
        inv.setItem(NOTIFY_SLOT, new ItemBuilder(notifyItem)
                .name(LangManager.get(uuid, "gui.notify.name"))
                .lore(LangManager.get(uuid, "gui.notify.lore"))
                .build());

        // 블랙리스트 버튼
        inv.setItem(BLACKLIST_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_BLACKLIST).clone())
                .name(LangManager.get(lang, "gui.blacklist.title"))
                .lore(LangManager.get(lang, "gui.blacklist.search_prompt"))
                .build());

        // 언어 선택 버튼
        inv.setItem(LANGUAGE_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_LANGUAGE).clone())
                .name(LangManager.get(lang, "gui.language.name"))
                .lore(LangManager.get(lang, "gui.language.lore"))
                .build());

        // 뒤로가기 버튼
        inv.setItem(BACK_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON).clone())
                .name(LangManager.get(lang, "gui.back.name"))
                .lore(LangManager.get(lang, "gui.back.lore"))
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

        MailManager manager = MailManager.getInstance();

        switch (e.getRawSlot()) {
            case NOTIFY_SLOT -> {
                boolean newState = MailDataManager.getInstance().toggleNotification(player.getUniqueId());
                String messageKey = newState ? "gui.notify.enabled" : "gui.notify.disabled";
                player.sendMessage(LangManager.get(player.getUniqueId(), messageKey));

                ConfigManager.playSound(
                        player,
                        ConfigManager.SoundType.ACTION_SETTING_CHANGE
                );

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
            case BACK_SLOT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailGUI.open(player);
            }
        }
    }
}
