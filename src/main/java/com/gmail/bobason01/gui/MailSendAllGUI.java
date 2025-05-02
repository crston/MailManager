package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class MailSendAllGUI implements Listener {

    private final MailManager plugin;
    private final Map<UUID, Inventory> inventoryMap = new HashMap<>();
    private final Map<UUID, Long> expireMap = new HashMap<>();

    public MailSendAllGUI(MailManager plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        Inventory gui = plugin.getServer().createInventory(player, 27, LangUtil.get(uuid, "gui.sendall.title"));
        inventoryMap.put(uuid, gui);

        gui.setItem(10, new ItemBuilder(Material.CLOCK).name(LangUtil.get(uuid, "gui.send.set-time")).build());
        gui.setItem(12, new ItemBuilder(Material.BOOK).name(LangUtil.get(uuid, "gui.send.exclude")).build());
        gui.setItem(14, new ItemBuilder(Material.CHEST).name(LangUtil.get(uuid, "gui.send.put-item")).build());
        gui.setItem(16, new ItemBuilder(Material.ENDER_EYE).name(LangUtil.get(uuid, "gui.sendall.confirm")).build());
        gui.setItem(18, new ItemBuilder(Material.BARRIER).name(LangUtil.get(uuid, "gui.back")).build());

        player.openInventory(gui);
    }

    public void setExpire(UUID sender, long millis) {
        expireMap.put(sender, millis);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Inventory gui = inventoryMap.get(uuid);
        if (gui == null || !event.getInventory().equals(gui)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == 10) {
            new MailTimeSelectGUI(plugin, null).open(player); // 일반 ALL이므로 caller 없음
        } else if (slot == 12) {
            new SendAllExcludeGUI(plugin).open(player);
        } else if (slot == 16) {
            ItemStack mailItem = gui.getItem(14);
            if (mailItem == null || mailItem.getType() == Material.AIR) {
                player.sendMessage(LangUtil.get(uuid, "gui.send.no-item"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                return;
            }

            long expire = expireMap.getOrDefault(uuid,
                    System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000); // 기본 7일

            Mail mail = new Mail(uuid, mailItem.clone(), expire);
            int count = 0;

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.getUniqueId().equals(uuid)) continue;
                if (SendAllExcludeGUI.isExcluded(uuid, target.getUniqueId())) continue;

                MailService.addMail(target.getUniqueId(), mail);
                count++;
            }

            player.sendMessage(LangUtil.get(uuid, "gui.sendall.success").replace("%count%", String.valueOf(count)));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            player.closeInventory();
        } else if (slot == 18) {
            new MailGUI(plugin).open(player);
        } else if (slot == 14 && event.getClick() == ClickType.SHIFT_LEFT) {
            player.getInventory().addItem(gui.getItem(14));
            gui.setItem(14, null);
        } else if (slot == 14) {
            event.setCancelled(false); // 아이템 넣고 빼기 허용
        }
    }
}
