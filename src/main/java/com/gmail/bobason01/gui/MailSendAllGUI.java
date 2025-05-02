package com.gmail.bobason01.gui;

import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ConfigLoader;
import com.gmail.bobason01.utils.LangUtil;
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

public class MailSendAllGUI implements Listener {

    private final Plugin plugin;

    public MailSendAllGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        MailService.setContext(player.getUniqueId(), "sendall");

        Inventory inv = Bukkit.createInventory(player, 27, LangUtil.get("gui.mail-sendall.title"));
        inv.setItem(10, ConfigLoader.getGuiItem("send-gui-time"));
        inv.setItem(12, ConfigLoader.getGuiItem("exclude")); // 제외 대상 버튼
        inv.setItem(14, null); // 아이템 입력 슬롯
        inv.setItem(16, ConfigLoader.getGuiItem("confirm"));
        inv.setItem(18, ConfigLoader.getGuiItem("back"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.mail-sendall.title"))) return;

        e.setCancelled(e.getRawSlot() < 27);

        switch (e.getRawSlot()) {
            case 10 -> {
                MailService.setContext(player.getUniqueId(), "sendall");
                new MailTimeSelectGUI(plugin).open(player);
            }
            case 12 -> new SendAllExcludeGUI(plugin).open(player);
            case 16 -> {
                ItemStack item = e.getInventory().getItem(14);
                if (item == null || item.getType() == Material.AIR) {
                    player.sendMessage(LangUtil.get("mail.invalid-args"));
                    return;
                }
                player.performCommand("mail sendall");
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
            }
            case 18 -> player.performCommand("mail");
        }
    }
}
