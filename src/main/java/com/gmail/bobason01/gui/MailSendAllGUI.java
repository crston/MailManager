package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.TimeUtil;
import org.bukkit.Bukkit;
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
    private final Set<UUID> sentSet = Collections.synchronizedSet(new HashSet<>());
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
        String title = LangManager.get(uuid, "gui.sendall.title");
        inv = Bukkit.createInventory(this, 27, title);

        Map<String, Integer> timeData = MailService.getTimeData(uuid);
        String formatted = TimeUtil.format(timeData, LangManager.getLanguage(uuid));
        long expireAt = MailService.getExpireTime(uuid);
        String formattedExpire = TimeUtil.formatDateTime(expireAt, LangManager.getLanguage(uuid));

        List<String> timeLore = new ArrayList<>();
        timeLore.add(LangManager.get(uuid, "gui.send.time.duration").replace("%time%", formatted));
        if (expireAt > 0 && expireAt < Long.MAX_VALUE) {
            timeLore.add(LangManager.get(uuid, "gui.send.time.expires").replace("%date%", formattedExpire));
        } else {
            timeLore.add(LangManager.get(uuid, "gui.send.time.no_expire"));
        }

        inv.setItem(SLOT_TIME, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_GUI_TIME))
                .name(LangManager.get(uuid, "gui.send.time.name"))
                .lore(timeLore)
                .build());

        inv.setItem(SLOT_EXCLUDE, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_ALL_GUI_EXCLUDE))
                .name(LangManager.get(uuid, "gui.sendall.exclude.name"))
                .lore(LangManager.getList(uuid, "gui.sendall.exclude.lore"))
                .build());

        refresh(player);

        inv.setItem(SLOT_CONFIRM, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_ALL_GUI_CONFIRM))
                .name(LangManager.get(uuid, "gui.sendall.confirm.name"))
                .lore(LangManager.getList(uuid, "gui.sendall.confirm.lore"))
                .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(uuid, "gui.back.name"))
                .lore(LangManager.getList(uuid, "gui.back.lore"))
                .build());

        player.openInventory(inv);
    }

    // 첨부 아이템 슬롯 새로고침
    public void refresh(Player player) {
        if (inv == null) return;

        UUID uuid = player.getUniqueId();
        List<ItemStack> items = MailService.getAttachedItems(uuid);

        if (items.isEmpty()) {
            inv.setItem(SLOT_ATTACH, null);
            return;
        }

        ItemStack first = items.get(0);
        List<String> lore = new ArrayList<>();
        lore.add(LangManager.get(uuid, "gui.send.item.first"));
        if (items.size() > 1) {
            lore.add(LangManager.get(uuid, "gui.send.item.more")
                    .replace("%count%", String.valueOf(items.size() - 1)));
        }

        inv.setItem(SLOT_ATTACH, new ItemBuilder(first).lore(lore).build());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendAllGUI gui)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();

        MailManager manager = MailManager.getInstance();

        if (slot == SLOT_TIME) {
            manager.mailTimeSelectGUI.open(player, MailSendAllGUI.class);
        } else if (slot == SLOT_EXCLUDE) {
            manager.sendAllExcludeGUI.open(player);
        } else if (slot == SLOT_ATTACH) {
            manager.mailAttachGUI.open(player, MailSendAllGUI.class);
        } else if (slot == SLOT_CONFIRM) {
            if (!gui.sentSet.add(uuid)) {
                player.sendMessage(LangManager.get(uuid, "mail.sendall.cooldown"));
                return;
            }
            if (MailService.getAttachedItems(uuid).isEmpty()) {
                player.sendMessage(LangManager.get(uuid, "mail.sendall.no_item"));
                gui.sentSet.remove(uuid);
                return;
            }
            MailService.sendAll(player, plugin);
            MailService.clearAttached(uuid);
            player.sendMessage(LangManager.get(uuid, "mail.sendall.success"));
            ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_SEND_SUCCESS);
            player.closeInventory();
        } else if (slot == SLOT_BACK) {
            manager.mailGUI.open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MailSendAllGUI && e.getRawSlots().contains(SLOT_BACK)) {
            e.setCancelled(true);
        }
    }
}
