package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MailSendAllGUI implements Listener, InventoryHolder {

    private static final int SLOT_TIME = 10;
    private static final int SLOT_EXCLUDE = 12;
    private static final int SLOT_ATTACH = 14;
    private static final int SLOT_CONFIRM = 16;
    private static final int SLOT_BACK = 26;
    private static final long CLICK_COOLDOWN_MS = 500L;

    private final Plugin plugin;
    private final Map<UUID, AtomicLong> lastClickMap = new ConcurrentHashMap<>();
    private final Set<UUID> processingSet = ConcurrentHashMap.newKeySet();

    public MailSendAllGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 27);
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        if (processingSet.contains(uuid)) return;

        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(lang, "gui.sendall.title");
        Inventory inv = Bukkit.createInventory(this, 27, title);

        updateContent(player, inv, lang);
        player.openInventory(inv);
    }

    public void refresh(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (inv.getHolder() instanceof MailSendAllGUI) {
            updateContent(player, inv, LangManager.getLanguage(player.getUniqueId()));
        }
    }

    private void updateContent(Player player, Inventory inv, String lang) {
        UUID uuid = player.getUniqueId();

        // Time Setting
        Map<String, Integer> timeData = MailService.getTimeData(uuid);
        String formatted = TimeUtil.format(timeData, lang);
        long expireAt = MailService.getExpireTime(uuid);
        String formattedExpire = TimeUtil.formatDateTime(expireAt, lang);

        List<String> timeLore = new ArrayList<>();
        timeLore.add(LangManager.get(lang, "gui.send.time.duration").replace("%time%", formatted));
        if (expireAt > 0 && expireAt < Long.MAX_VALUE) {
            timeLore.add(LangManager.get(lang, "gui.send.time.expires").replace("%date%", formattedExpire));
        } else {
            timeLore.add(LangManager.get(lang, "gui.send.time.no_expire"));
        }

        inv.setItem(SLOT_TIME, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_GUI_TIME))
                .name(LangManager.get(lang, "gui.send.time.name"))
                .lore(timeLore)
                .build());

        // Exclude Setting
        int excludedCount = MailDataManager.getInstance().getExclude(uuid).size();
        List<String> excludeLore = LangManager.getList(lang, "gui.sendall.exclude.lore");
        excludeLore.replaceAll(line -> line.replace("%count%", String.valueOf(excludedCount)));

        inv.setItem(SLOT_EXCLUDE, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_ALL_GUI_EXCLUDE))
                .name(LangManager.get(lang, "gui.sendall.exclude.name"))
                .lore(excludeLore)
                .build());

        // Attach Items
        List<ItemStack> items = MailService.getAttachedItems(uuid);
        if (items.isEmpty()) {
            inv.setItem(SLOT_ATTACH, new ItemBuilder(Material.CHEST)
                    .name(LangManager.get(lang, "gui.send.item.empty"))
                    .lore(LangManager.getList(lang, "gui.send.item.empty_lore"))
                    .build());
        } else {
            ItemStack first = items.get(0);
            List<String> attachLore = new ArrayList<>();
            attachLore.add(LangManager.get(lang, "gui.send.item.first"));
            if (items.size() > 1) {
                attachLore.add(LangManager.get(lang, "gui.send.item.more")
                        .replace("%count%", String.valueOf(items.size() - 1)));
            }
            inv.setItem(SLOT_ATTACH, new ItemBuilder(first.clone()).lore(attachLore).build());
        }

        // Confirm Button
        inv.setItem(SLOT_CONFIRM, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_ALL_GUI_CONFIRM))
                .name(LangManager.get(lang, "gui.sendall.confirm.name"))
                .lore(LangManager.getList(lang, "gui.sendall.confirm.lore"))
                .build());

        // Back Button
        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(lang, "gui.back.name"))
                .lore(LangManager.getList(lang, "gui.back.lore"))
                .build());
    }

    private boolean hasCooldownPassed(Player player) {
        long now = System.currentTimeMillis();
        AtomicLong lastClick = lastClickMap.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        if (now - lastClick.get() < CLICK_COOLDOWN_MS) {
            return false;
        }
        lastClick.set(now);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendAllGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0) return;

        if (!hasCooldownPassed(player)) return;

        UUID uuid = player.getUniqueId();
        MailManager manager = MailManager.getInstance();

        switch (slot) {
            case SLOT_TIME -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailTimeSelectGUI.open(player, MailSendAllGUI.class);
            }
            case SLOT_EXCLUDE -> {
                // Check if time is set (required before exclusion)
                Map<String, Integer> timeData = MailService.getTimeData(uuid);
                boolean isPermanent = timeData.getOrDefault("permanent", 0) == 1; // Assuming your logic or just empty map check
                boolean hasTime = !timeData.isEmpty();

                // If you use empty map for default 100 years, this check might need adjustment based on your specific logic for "Permanent"
                // Based on MailService.buildExpireTime, empty map means +100 years.
                // Assuming we want user to explicitly visit time GUI or confirm default.

                // For now, let's open it directly as requested, unless specific restriction is needed.
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.sendAllExcludeGUI.open(player);
            }
            case SLOT_ATTACH -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailAttachGUI.open(player, MailSendAllGUI.class);
            }
            case SLOT_CONFIRM -> {
                if (processingSet.contains(uuid)) {
                    player.sendMessage(LangManager.get(uuid, "mail.sendall.processing"));
                    return;
                }
                if (MailService.getAttachedItems(uuid).isEmpty()) {
                    player.sendMessage(LangManager.get(uuid, "mail.sendall.no_item"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                    return;
                }

                processingSet.add(uuid);
                player.closeInventory();
                player.sendMessage(LangManager.get(uuid, "mail.sendall.start"));
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);

                // Execute Send Logic
                MailService.sendAll(player, plugin);

                // Reset processing set after a short delay or assume sendAll handles cleanup via callback if you modified it.
                // Since MailService.sendAll runs async and has its own callback, we remove from set there or here after delay.
                // But MailService.sendAll provided uses sessions.remove(senderId) at the end.
                // We will remove from processingSet after a delay to prevent spam.
                Bukkit.getScheduler().runTaskLater(plugin, () -> processingSet.remove(uuid), 40L);
            }
            case SLOT_BACK -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailGUI.open(player);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MailSendAllGUI) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // processingSet is handled via logic flow
    }
}