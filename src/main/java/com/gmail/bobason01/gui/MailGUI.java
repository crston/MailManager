package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MailGUI implements Listener, InventoryHolder {

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();

    private static final int PAGE_SIZE = 44;
    private static final int SETTING_BTN_SLOT = 8;
    private static final int PREV_BTN_SLOT = 45;
    private static final int SEND_BTN_SLOT = 49;
    private static final int NEXT_BTN_SLOT = 53;

    public MailGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54);
    }

    public void open(Player player) {
        open(player, pageMap.getOrDefault(player.getUniqueId(), 0));
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        MailDataManager manager = MailDataManager.getInstance();
        manager.flushNow();
        manager.forceReloadMails(uuid);

        List<Mail> validMails = getValidMails(uuid);
        int totalPages = Math.max(1, (int) Math.ceil((double) validMails.size() / PAGE_SIZE));
        int safePage = Math.min(Math.max(page, 0), totalPages - 1);
        pageMap.put(uuid, safePage);

        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(lang, "gui.mail.title") + " (" + (safePage + 1) + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(this, 54, title);

        int start = safePage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int mailIndex = start + i;
            if (mailIndex >= validMails.size()) break;

            int slot = (i < 8) ? i : (i + 1);
            Mail mail = validMails.get(mailIndex);
            ItemStack item = mail.toItemStack(player);
            if (item == null) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(" ");
                lore.addAll(LangManager.getList(uuid, "gui.mail.lore.claim"));
                lore.addAll(LangManager.getList(uuid, "gui.mail.lore.delete"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        }

        inv.setItem(SETTING_BTN_SLOT, createButton(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SETTING_BUTTON), LangManager.get(uuid, "gui.setting.title")));
        if (safePage > 0) inv.setItem(PREV_BTN_SLOT, createButton(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON), LangManager.get(uuid, "gui.previous")));
        if (safePage < totalPages - 1) inv.setItem(NEXT_BTN_SLOT, createButton(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON), LangManager.get(uuid, "gui.next")));
        if (player.hasPermission("mail.send")) inv.setItem(SEND_BTN_SLOT, createButton(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SEND_BUTTON), LangManager.get(uuid, "gui.send.title")));

        player.openInventory(inv);
    }

    private List<Mail> getValidMails(UUID uuid) {
        MailDataManager manager = MailDataManager.getInstance();
        List<Mail> mails = new ArrayList<>(manager.getMails(uuid));
        mails.removeIf(m -> m == null || m.isExpired());
        mails.sort(Comparator.comparingLong(Mail::getCreatedAt).reversed());
        return mails;
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
        if (!(e.getInventory().getHolder() instanceof MailGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0) return;

        UUID uuid = player.getUniqueId();
        int currentPage = pageMap.getOrDefault(uuid, 0);

        if (slot == SETTING_BTN_SLOT) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            MailManager.getInstance().mailSettingGUI.open(player);
        } else if (slot == SEND_BTN_SLOT) {
            if (player.hasPermission("mail.send")) {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                MailManager.getInstance().mailSendGUI.open(player);
            }
        } else if (slot == PREV_BTN_SLOT) {
            if (currentPage > 0) {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage - 1);
            }
        } else if (slot == NEXT_BTN_SLOT) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
            open(player, currentPage + 1);
        } else if (slot < 45) {
            List<Mail> validMails = getValidMails(uuid);
            int listIndexOffset = (slot < 8) ? slot : (slot - 1);
            int mailIndex = currentPage * PAGE_SIZE + listIndexOffset;

            if (mailIndex >= 0 && mailIndex < validMails.size()) {
                Mail mail = validMails.get(mailIndex);
                if (e.getClick() == ClickType.RIGHT) {
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                    MailManager.getInstance().mailDeleteConfirmGUI.open(player, Collections.singletonList(mail));
                } else {
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                    MailManager.getInstance().mailViewGUI.open(player, mail);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MailGUI) {
            pageMap.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MailGUI) e.setCancelled(true);
    }
}