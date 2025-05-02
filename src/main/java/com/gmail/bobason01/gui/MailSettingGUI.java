package com.gmail.bobason01.gui;

import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ConfigLoader;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.LangUtil;
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

    private static final String GUI_TITLE = LangUtil.get("gui.mail-setting.title");

    public MailSettingGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        player.openInventory(createInventory(player));
    }

    private Inventory createInventory(Player player) {
        UUID uuid = player.getUniqueId();
        boolean notify = MailDataManager.getInstance().isNotifyEnabled(uuid);

        Inventory inv = Bukkit.createInventory(player, 27, GUI_TITLE);

        ItemStack notifyItem = new ItemBuilder(notify ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(LangUtil.get(notify ? "gui.mail-setting.notify-on" : "gui.mail-setting.notify-off"))
                .lore(LangUtil.get("gui.mail-setting.notify-lore"))
                .build();

        inv.setItem(11, notifyItem);
        inv.setItem(15, ConfigLoader.getGuiItem("blacklist"));
        inv.setItem(26, ConfigLoader.getGuiItem("back"));

        return inv;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;

        UUID uuid = player.getUniqueId();

        switch (e.getRawSlot()) {
            case 11 -> {
                boolean current = MailDataManager.getInstance().isNotifyEnabled(uuid);
                boolean newState = !current;
                MailDataManager.getInstance().setNotify(uuid, newState);
                player.sendMessage(LangUtil.get(newState
                        ? "gui.mail-setting.notify-enabled"
                        : "gui.mail-setting.notify-disabled"));
                open(player);
            }
            case 15 -> new BlacklistSelectGUI(plugin).open(player);
            case 26 -> new MailGUI(plugin).open(player);
        }
    }
}
