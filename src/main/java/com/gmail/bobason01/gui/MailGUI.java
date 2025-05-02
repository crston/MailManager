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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class MailGUI implements Listener {

    private final MailManager plugin;
    private final Map<UUID, Integer> pageMap = new HashMap<>();
    private final Map<UUID, Inventory> inventoryMap = new HashMap<>();

    public MailGUI(MailManager plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        int page = pageMap.getOrDefault(uuid, 0);

        List<Mail> inbox = MailService.getInbox(uuid);
        int total = inbox.size();
        int start = page * 27;
        int end = Math.min(start + 27, total);

        Inventory gui = Bukkit.createInventory(player, 54, LangUtil.get(uuid, "gui.mail.title"));
        inventoryMap.put(uuid, gui);

        for (int i = start; i < end; i++) {
            Mail mail = inbox.get(i);
            gui.setItem(i - start, mail.getItem().clone());
        }

        gui.setItem(8, new ItemBuilder(Material.COMPARATOR).name(LangUtil.get(uuid, "gui.mail.settings")).build());
        gui.setItem(45, new ItemBuilder(Material.WRITABLE_BOOK).name(LangUtil.get(uuid, "gui.mail.send")).build());
        gui.setItem(48, new ItemBuilder(Material.ARROW).name(LangUtil.get(uuid, "gui.previous")).build());
        gui.setItem(50, new ItemBuilder(Material.ARROW).name(LangUtil.get(uuid, "gui.next")).build());

        player.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        Inventory gui = inventoryMap.get(uuid);
        if (gui == null) return;

        // 메뉴 GUI 확인
        if (!event.getView().getTopInventory().equals(gui)) return;

        int rawSlot = event.getRawSlot();
        Inventory clicked = event.getClickedInventory();

        // 보호: 메뉴 클릭만 차단 (player 인벤토리는 허용)
        if (clicked != null && clicked.equals(event.getView().getTopInventory())) {
            event.setCancelled(true);
        } else {
            return; // 플레이어 인벤토리는 차단 안 함
        }

        int page = pageMap.getOrDefault(uuid, 0);
        List<Mail> inbox = MailService.getInbox(uuid);

        if (rawSlot >= 0 && rawSlot < 27) {
            int index = page * 27 + rawSlot;
            if (index >= inbox.size()) return;

            Mail mail = inbox.get(index);
            if (event.isShiftClick() && event.isRightClick()) {
                inbox.remove(index);
                player.sendMessage(LangUtil.get(uuid, "gui.mail.deleted"));
            } else {
                player.getInventory().addItem(mail.getItem().clone());
                inbox.remove(index);
                player.sendMessage(LangUtil.get(uuid, "gui.mail.received"));
            }

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            open(player);
        } else if (rawSlot == 45) {
            new MailSendGUI(plugin).open(player);
        } else if (rawSlot == 48) {
            if (page > 0) {
                pageMap.put(uuid, page - 1);
                open(player);
            }
        } else if (rawSlot == 50) {
            if ((page + 1) * 27 < inbox.size()) {
                pageMap.put(uuid, page + 1);
                open(player);
            }
        } else if (rawSlot == 8) {
            new MailSettingGUI(plugin).open(player);
        }
    }
}
