package com.gmail.bobason01.gui;

import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class MailDeleteConfirmGUI implements InventoryHolder {

    public static final int YES_SLOT = 11;
    public static final int NO_SLOT = 15;

    private final Inventory inventory;

    public MailDeleteConfirmGUI(Player player) {
        String title = LangManager.get(player.getUniqueId(), "gui.delete.title");
        this.inventory = Bukkit.createInventory(this, 27, title);
        setupItems(player);
    }

    private void setupItems(Player player) {
        inventory.setItem(YES_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.DELETE_GUI_CONFIRM_BUTTON))
                .name(ChatColor.translateAlternateColorCodes('&', "&cYES")) // 이렇게 수정해주세요.
                .lore(LangManager.get(player.getUniqueId(), "gui.delete.yes_lore"))
                .build());

        inventory.setItem(NO_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.DELETE_GUI_CANCEL_BUTTON))
                .name(ChatColor.translateAlternateColorCodes('&', "&aNO")) // 여기도 수정해주세요.
                .lore(LangManager.get(player.getUniqueId(), "gui.delete.no_lore"))
                .build());
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}