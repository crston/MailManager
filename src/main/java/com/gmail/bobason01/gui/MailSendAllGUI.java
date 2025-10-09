package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MailSendAllGUI implements Listener, InventoryHolder {

    private static final int SLOT_TIME = 10;
    private static final int SLOT_EXCLUDE = 12;
    private static final int SLOT_ATTACH = 14;
    private static final int SLOT_CONFIRM = 16;
    private static final int SLOT_BACK = 26;

    private final Plugin plugin;
    private final Set<UUID> sentSet = new HashSet<>();
    private Inventory inv;

    public MailSendAllGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(uuid, "gui.sendall.title");
        inv = Bukkit.createInventory(this, 27, title);

        Map<String, Integer> timeData = MailService.getTimeData(uuid);
        String formatted = TimeUtil.format(timeData, lang);
        long expireAt = MailService.getExpireTime(uuid);
        String formattedExpire = TimeUtil.formatDateTime(expireAt, lang);

        List<String> timeLore = new ArrayList<>();
        timeLore.add(LangManager.get(uuid, "gui.send.time.duration").replace("%time%", formatted));
        if (expireAt > 0 && expireAt < Long.MAX_VALUE) {
            timeLore.add(LangManager.get(uuid, "gui.send.time.expires").replace("%date%", formattedExpire));
        } else {
            timeLore.add(LangManager.get(uuid, "gui.send.time.no_expire"));
        }

        inv.setItem(SLOT_TIME, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_GUI_TIME).clone())
                .name(LangManager.get(uuid, "gui.send.time.name"))
                .lore(timeLore)
                .build());

        inv.setItem(SLOT_EXCLUDE, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_ALL_GUI_EXCLUDE).clone())
                .name(LangManager.get(uuid, "gui.sendall.exclude.name"))
                .lore(LangManager.get(uuid, "gui.sendall.exclude.lore"))
                .build());

        refresh(player);

        inv.setItem(SLOT_CONFIRM, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_ALL_GUI_CONFIRM).clone())
                .name(LangManager.get(uuid, "gui.sendall.confirm.name"))
                .lore(LangManager.get(uuid, "gui.sendall.confirm.lore"))
                .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON).clone())
                .name("§c" + LangManager.get(uuid, "gui.back.name"))
                .lore(LangManager.get(uuid, "gui.back.lore"))
                .build());

        player.openInventory(inv);
    }

    // 첨부 아이템 슬롯 새로고침
    public void refresh(Player player) {
        UUID uuid = player.getUniqueId();
        List<ItemStack> items = MailService.getAttachedItems(uuid);

        if (inv == null) return;

        if (items.isEmpty()) {
            inv.setItem(SLOT_ATTACH, null);
            player.updateInventory();
            return;
        }

        ItemStack first = items.get(0).clone();
        List<String> lore = new ArrayList<>();
        lore.add(LangManager.get(uuid, "gui.send.item.first"));
        if (items.size() > 1) {
            lore.add(LangManager.get(uuid, "gui.send.item.more")
                    .replace("%count%", String.valueOf(items.size() - 1)));
        }

        inv.setItem(SLOT_ATTACH, new ItemBuilder(first).lore(lore).build());
        player.updateInventory();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendAllGUI) || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        e.setCancelled(true);
        UUID uuid = player.getUniqueId();
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        MailManager manager = MailManager.getInstance();

        switch (rawSlot) {
            case SLOT_TIME -> manager.mailTimeSelectGUI.open(player, MailSendAllGUI.class);
            case SLOT_EXCLUDE -> manager.sendAllExcludeGUI.open(player);
            case SLOT_ATTACH -> manager.mailAttachGUI.open(player, MailSendAllGUI.class);
            case SLOT_CONFIRM -> {
                if (sentSet.contains(uuid)) {
                    player.sendMessage(LangManager.get(uuid, "mail.sendall.cooldown"));
                    return;
                }
                if (MailService.getAttachedItems(uuid).isEmpty()) {
                    player.sendMessage(LangManager.get(uuid, "mail.sendall.no_item"));
                    return;
                }
                MailService.sendAll(player, plugin);
                MailService.clearAttached(uuid);
                sentSet.add(uuid);
                player.sendMessage(LangManager.get(uuid, "mail.sendall.success"));
                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_SEND_SUCCESS);
                player.closeInventory();
            }
            case SLOT_BACK -> manager.mailGUI.open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendAllGUI)) return;
        if (e.getRawSlots().contains(SLOT_BACK)) {
            e.setCancelled(true);
        }
    }
}
