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

    @Override
    public @NotNull Inventory getInventory() {
        // 기본 인벤토리를 반환하되 실제 open 시에는 데이터가 채워진 인벤토리를 사용합니다.
        return Bukkit.createInventory(this, 27, "Mail Settings");
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(uuid, "gui.setting.title");
        Inventory inv = Bukkit.createInventory(this, 27, title);

        refreshItems(player, inv);

        // 하위 메뉴 버튼들은 변동이 없으므로 처음에만 배치
        inv.setItem(BLACKLIST_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_BLACKLIST))
                .name(LangManager.get(uuid, "gui.blacklist.title")).lore(LangManager.getList(uuid, "gui.blacklist.lore")).build());

        inv.setItem(LANGUAGE_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_LANGUAGE))
                .name(LangManager.get(uuid, "gui.language.name")).lore(LangManager.getList(uuid, "gui.language.lore")).build());

        inv.setItem(MULTI_SELECT_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SELECT_BUTTON))
                .name(LangManager.get(uuid, "gui.mail.select_name")).lore(LangManager.getList(uuid, "gui.mail.select_lore")).build());

        inv.setItem(BACK_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(uuid, "gui.back.name")).build());

        player.openInventory(inv);
    }

    // 상태가 변하는 아이템만 따로 갱신하는 메서드
    private void refreshItems(Player player, Inventory inv) {
        UUID uuid = player.getUniqueId();
        boolean notifyEnabled = MailDataManager.getInstance().isNotify(uuid);

        inv.setItem(NOTIFY_SLOT, new ItemBuilder(notifyEnabled ?
                ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_NOTIFY_ON) :
                ConfigManager.getItem(ConfigManager.ItemType.SETTING_GUI_NOTIFY_OFF))
                .name(LangManager.get(uuid, "gui.notify.name"))
                .lore(LangManager.getList(uuid, "gui.notify.lore"))
                .build());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSettingGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        UUID uuid = player.getUniqueId();
        MailManager manager = MailManager.getInstance();

        switch (slot) {
            case NOTIFY_SLOT -> {
                boolean newState = MailDataManager.getInstance().toggleNotification(uuid);
                player.sendMessage(LangManager.get(uuid, newState ? "gui.notify.enabled" : "gui.notify.disabled"));
                ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SETTING_CHANGE);

                // [개선] 창을 다시 여는 대신 해당 슬롯만 갱신하여 깜빡임 제거
                refreshItems(player, e.getInventory());
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
                // [개선] 매번 flush/reload 하지 않고 메모리상의 메인 메뉴를 바로 오픈
                manager.mailGUI.open(player);
            }
        }
    }
}