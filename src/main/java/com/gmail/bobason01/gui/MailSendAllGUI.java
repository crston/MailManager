package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.TimeUtil; // TimeUtil import 추가
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*; // List, Map import 추가

public class MailSendAllGUI implements Listener, InventoryHolder {

    private static final int SLOT_TIME = 10;
    private static final int SLOT_EXCLUDE = 12;
    private static final int SLOT_ITEM = 14;
    private static final int SLOT_CONFIRM = 16;
    private static final int SLOT_BACK = 26;

    private final Plugin plugin;
    private final Set<UUID> sentSet = new HashSet<>();

    public MailSendAllGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid); // lang 변수 추가
        String title = LangManager.get(uuid, "gui.sendall.title");
        Inventory inv = Bukkit.createInventory(this, 27, title);

        // --- 시간 표시 로직 시작 ---
        Map<String, Integer> timeData = MailService.getTimeData(uuid);
        String formatted = TimeUtil.format(timeData, lang);
        long expireAt = MailService.getExpireTime(uuid);
        String formattedExpire = TimeUtil.formatDateTime(expireAt, lang);

        List<String> timeLore = new ArrayList<>();
        timeLore.add(LangManager.get(uuid, "gui.sendall.time.duration").replace("%time%", formatted));
        if (expireAt > 0 && expireAt < Long.MAX_VALUE) {
            timeLore.add(LangManager.get(uuid, "gui.sendall.time.expires").replace("%date%", formattedExpire));
        } else {
            timeLore.add(LangManager.get(uuid, "gui.sendall.time.no_expire"));
        }
        // --- 시간 표시 로직 끝 ---

        inv.setItem(SLOT_TIME, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_GUI_TIME))
                .name(LangManager.get(uuid, "gui.sendall.expire.name"))
                .lore(timeLore) // 수정된 lore 적용
                .build());

        inv.setItem(SLOT_EXCLUDE, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_ALL_GUI_EXCLUDE))
                .name(LangManager.get(uuid, "gui.sendall.exclude.name"))
                .lore(LangManager.get(uuid, "gui.sendall.exclude.lore"))
                .build());

        ItemStack attached = MailService.getAttachedItem(uuid);
        if (attached != null) {
            inv.setItem(SLOT_ITEM, attached);
        }

        inv.setItem(SLOT_CONFIRM, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_ALL_GUI_CONFIRM))
                .name(LangManager.get(uuid, "gui.sendall.confirm.name"))
                .lore(LangManager.get(uuid, "gui.sendall.confirm.lore"))
                .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(uuid, "gui.back.name"))
                .lore(Collections.singletonList("§7" + LangManager.get(uuid, "gui.back.lore")))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendAllGUI) || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = e.getRawSlot();
        UUID uuid = player.getUniqueId();
        Inventory inv = e.getInventory();

        if (slot == SLOT_ITEM) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack newItem = inv.getItem(SLOT_ITEM);
                MailService.setAttachedItem(uuid, newItem != null ? newItem.clone() : null);
            }, 1L);
            return;
        }

        e.setCancelled(true);

        MailManager manager = MailManager.getInstance();

        switch (slot) {
            case SLOT_TIME -> manager.mailTimeSelectGUI.open(player, MailSendAllGUI.class);
            case SLOT_EXCLUDE -> manager.sendAllExcludeGUI.open(player);
            case SLOT_CONFIRM -> {
                if (sentSet.contains(uuid)) {
                    player.sendMessage(LangManager.get(uuid, "mail.sendall.cooldown"));
                    return;
                }

                ItemStack item = MailService.getAttachedItem(uuid);
                if (item == null || item.getType().isAir()) {
                    player.sendMessage(LangManager.get(uuid, "mail.sendall.no_item"));
                    return;
                }

                MailService.sendAll(player, plugin);
                MailService.setAttachedItem(uuid, null);
                sentSet.add(uuid);
                player.sendMessage(LangManager.get(uuid, "mail.sendall.success"));
                player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.MAIL_SEND_SUCCESS), 1.0f, 1.0f);
                player.closeInventory();
            }
            case SLOT_BACK -> manager.mailGUI.open(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendAllGUI) || !(e.getPlayer() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (sentSet.contains(uuid)) return;

        ItemStack item = e.getInventory().getItem(SLOT_ITEM);
        if (item != null && !item.getType().isAir()) {
            player.getInventory().addItem(item);
            MailService.setAttachedItem(uuid, null);
        }
    }
}