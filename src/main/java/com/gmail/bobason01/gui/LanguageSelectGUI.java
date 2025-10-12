package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LanguageSelectGUI implements Listener, InventoryHolder {

    private static final int GUI_SIZE = 27;
    private final Plugin plugin;

    public LanguageSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, GUI_SIZE);
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String currentLang = LangManager.getLanguage(uuid);
        List<String> sortedLangs = new ArrayList<>(LangManager.getAvailableLanguages());
        sortedLangs.sort(String::compareToIgnoreCase);

        String guiTitle = LangManager.get(uuid, "gui.language.title");
        Inventory inv = Bukkit.createInventory(this, GUI_SIZE, guiTitle);

        int slot = 0;
        for (String lang : sortedLangs) {
            if (slot >= GUI_SIZE) break;
            boolean selected = lang.equalsIgnoreCase(currentLang);

            String nameKey = selected ? "gui.language.selected" : "gui.language.unselected";
            String displayName = LangManager.get(lang, "language.name") + " [" + lang + "]";

            inv.setItem(slot++, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.LANGUAGE_GUI_ITEM))
                    .name(LangManager.get(uuid, nameKey).replace("%lang%", displayName))
                    .lore(LangManager.getList(uuid, "gui.language.lore"))
                    .build());
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof LanguageSelectGUI) || !(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = clicked.getItemMeta().getDisplayName();
        if (displayName == null || displayName.isEmpty()) return;

        String strippedName = ChatColor.stripColor(displayName);
        int start = strippedName.lastIndexOf('[');
        int end = strippedName.lastIndexOf(']');
        if (start == -1 || end == -1 || start >= end) return;

        String selectedLang = strippedName.substring(start + 1, end);
        if (!LangManager.getAvailableLanguages().contains(selectedLang)) return;

        LangManager.setLanguage(player.getUniqueId(), selectedLang);
        ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SETTING_CHANGE);
        MailManager.getInstance().mailSettingGUI.open(player);
    }
}
