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

    private static final int NOTIFY_SLOT = 11;
    private static final int BLACKLIST_SLOT = 15;
    private static final int BACK_SLOT = 26;

    private static final String GUI_TITLE = LangUtil.get("gui.mail-setting.title");

    private final Plugin plugin;

    public MailSettingGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin); // 한 번만 로드해도 되긴 함
    }

    public void open(Player player) {
        player.openInventory(createInventory(player));
    }

    private Inventory createInventory(Player player) {
        UUID uuid = player.getUniqueId();
        boolean notifyEnabled = MailDataManager.getInstance().isNotifyEnabled(uuid);

        String nameKey = notifyEnabled ? "gui.mail-setting.notify-on" : "gui.mail-setting.notify-off";
        Material dye = notifyEnabled ? Material.LIME_DYE : Material.GRAY_DYE;

        ItemStack notifyItem = new ItemBuilder(dye)
                .name(LangUtil.get(nameKey))
                .lore(LangUtil.get("gui.mail-setting.notify-lore"))
                .build();

        Inventory inv = Bukkit.createInventory(player, 27, GUI_TITLE);
        inv.setItem(NOTIFY_SLOT, notifyItem);
        inv.setItem(BLACKLIST_SLOT, ConfigLoader.getGuiItem("blacklist"));
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
                boolean newState = MailDataManager.getInstance().toggleNotify(uuid);
                player.sendMessage(LangUtil.get(newState
                        ? "gui.mail-setting.notify-enabled"
                        : "gui.mail-setting.notify-disabled"));
                open(player); // Refresh GUI
            }
            case BLACKLIST_SLOT -> new BlacklistSelectGUI(plugin).open(player);
            case BACK_SLOT -> new MailGUI(plugin).open(player);
        }
    }
}
