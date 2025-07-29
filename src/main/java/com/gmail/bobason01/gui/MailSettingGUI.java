package com.gmail.bobason01.gui;

import com.gmail.bobason01.lang.LangManager;
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
    private static final int LANGUAGE_SLOT = 13;
    private static final int BACK_SLOT = 26;

    private final Plugin plugin;

    public MailSettingGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        player.openInventory(createInventory(player));
    }

    private Inventory createInventory(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        boolean notifyEnabled = MailDataManager.getInstance().isNotifyEnabled(uuid);

        // 알림 설정 버튼
        Material notifyMaterial = notifyEnabled ? Material.LIME_DYE : Material.GRAY_DYE;

        // 블랙리스트 버튼
        ItemStack blacklistItem = new ItemBuilder(Material.BARRIER)
                .name(LangManager.get(lang, "gui.blacklist.title"))
                .lore(LangManager.get(lang, "gui.blacklist.search_prompt"))
                .build();

        // 언어 선택 버튼
        String displayLangName = LangManager.get(lang, "gui.language.name");
        ItemStack langItem = new ItemBuilder(Material.BOOK)
                .name(displayLangName)
                .lore(LangManager.get(lang, "gui.language.lore"))
                .build();

        // 뒤로가기 버튼
        ItemStack backItem = new ItemBuilder(Material.ARROW)
                .name(LangManager.get(lang, "gui.back.name"))
                .lore(LangManager.get(lang, "gui.back.lore"))
                .build();

        String title = LangManager.get(lang, "gui.setting.title");
        Inventory inv = Bukkit.createInventory(player, 27, title);

        inv.setItem(NOTIFY_SLOT, new ItemBuilder(notifyMaterial)
                .name(LangManager.get(uuid, "gui.notify.name"))
                .lore(LangManager.get(uuid, "gui.notify.lore"))
                .build());
        inv.setItem(BLACKLIST_SLOT, blacklistItem);
        inv.setItem(LANGUAGE_SLOT, langItem);
        inv.setItem(BACK_SLOT, backItem);

        return inv;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(lang, "gui.setting.title");

        if (!e.getView().getTitle().equals(title)) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;

        switch (e.getRawSlot()) {
            case NOTIFY_SLOT -> {
                boolean newState = MailDataManager.getInstance().toggleNotification(uuid);
                String messageKey = newState ? "gui.notify.enabled" : "gui.notify.disabled";
                player.sendMessage(LangManager.get(lang, messageKey));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, newState ? 1.2f : 0.8f);
                open(player);
            }
            case BLACKLIST_SLOT -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new BlacklistSelectGUI(plugin).open(player);
            }
            case LANGUAGE_SLOT -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new LanguageSelectGUI(plugin).open(player);
            }
            case BACK_SLOT -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new MailGUI(plugin).open(player);
            }
        }
    }
}
