package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MailGUI implements Listener, InventoryHolder {

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new HashMap<>();

    private static final int PAGE_SIZE = 45;
    private static final int SEND_BTN_SLOT = 45;
    private static final int SETTING_BTN_SLOT = 8;
    private static final int PREV_BTN_SLOT = 48;
    private static final int NEXT_BTN_SLOT = 50;

    public MailGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54);
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        List<Mail> validMails = getValidMails(uuid);

        int totalPages = Math.max(1, (validMails.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));
        pageMap.put(uuid, clampedPage);

        String title = LangManager.get(uuid, "gui.mail.title");
        Inventory inv = Bukkit.createInventory(this, 54, title);

        int start = clampedPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, validMails.size());

        for (int slot = 0; slot < PAGE_SIZE && start + slot < end; slot++) {
            Mail mail = validMails.get(validMails.size() - 1 - (start + slot));
            ItemStack item = mail.toItemStack(player);
            if (item == null) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add(" ");
                lore.addAll(LangManager.getList(uuid, "gui.mail.lore.view"));
                lore.addAll(LangManager.getList(uuid, "gui.mail.lore.delete"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        }

        if (clampedPage > 0) {
            inv.setItem(PREV_BTN_SLOT, createButton(
                    ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON),
                    LangManager.get(uuid, "gui.previous")));
        }
        if (end < validMails.size()) {
            inv.setItem(NEXT_BTN_SLOT, createButton(
                    ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON),
                    LangManager.get(uuid, "gui.next")));
        }

        if (player.hasPermission("mail.send")) {
            inv.setItem(SEND_BTN_SLOT, createButton(
                    ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SEND_BUTTON),
                    LangManager.get(uuid, "gui.send.title")));
        }

        inv.setItem(SETTING_BTN_SLOT, createButton(
                ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SETTING_BUTTON),
                LangManager.get(uuid, "gui.setting.title")));

        player.openInventory(inv);
    }

    private List<Mail> getValidMails(UUID uuid) {
        List<Mail> mails = MailDataManager.getInstance().getMails(uuid);
        List<Mail> result = new ArrayList<>(mails.size());
        for (Mail m : mails) {
            if (!m.isExpired()) result.add(m);
        }
        return result;
    }

    private ItemStack createButton(ItemStack base, String name) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!(e.getInventory().getHolder() instanceof MailGUI)) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();
        int currentPage = pageMap.getOrDefault(uuid, 0);
        MailManager manager = MailManager.getInstance();

        switch (slot) {
            case SEND_BTN_SLOT -> {
                if (!player.hasPermission("mail.send")) {
                    player.sendMessage(LangManager.get(uuid, "mail.send.no_permission"));
                    return;
                }
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailSendGUI.open(player);
            }
            case SETTING_BTN_SLOT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailSettingGUI.open(player);
            }
            case PREV_BTN_SLOT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage - 1);
            }
            case NEXT_BTN_SLOT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage + 1);
            }
            default -> {
                if (slot < PAGE_SIZE) {
                    List<Mail> validMails = getValidMails(uuid);
                    int mailIndex = currentPage * PAGE_SIZE + slot;
                    if (mailIndex >= validMails.size()) return;

                    int reversedIndex = validMails.size() - 1 - mailIndex;
                    Mail mail = validMails.get(reversedIndex);

                    if (e.getClick() == ClickType.RIGHT) {
                        ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                        new MailDeleteConfirmGUI(player, plugin, Collections.singletonList(mail)).open(player);
                    } else if (e.getClick() == ClickType.LEFT) {
                        ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                        manager.mailViewGUI.open(player, mail);
                    }
                }
            }
        }
    }
}
