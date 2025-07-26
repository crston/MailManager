package com.gmail.bobason01.gui;

import com.gmail.bobason01.config.ConfigLoader;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
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

import java.util.*;

public class MailGUI implements Listener {

    private final Plugin plugin;

    private static final int MAIL_START_SLOT = 10;
    private static final int MAIL_END_SLOT = 44;
    private static final int PAGE_SIZE = MAIL_END_SLOT - MAIL_START_SLOT + 1;

    private static final int SEND_BTN_SLOT = 45;
    private static final int SETTING_BTN_SLOT = 8;
    private static final int PREV_BTN_SLOT = 48;
    private static final int NEXT_BTN_SLOT = 50;

    private final Map<UUID, Integer> pageMap = new WeakHashMap<>();

    public MailGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        List<Mail> mails = new ArrayList<>(MailDataManager.getInstance().getMails(uuid));

        int totalPages = (int) Math.ceil((double) mails.size() / PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(0, Math.min(page, totalPages - 1));
        pageMap.put(uuid, page);

        Inventory inv = Bukkit.createInventory(player, 54, "우편함");

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mails.size());

        int slot = MAIL_START_SLOT;
        for (int i = start; i < end && slot <= MAIL_END_SLOT; i++, slot++) {
            inv.setItem(slot, mails.get(i).toItemStack());
        }

        if (page > 0) inv.setItem(PREV_BTN_SLOT, ConfigLoader.getGuiItem("previous-page"));
        if (end < mails.size()) inv.setItem(NEXT_BTN_SLOT, ConfigLoader.getGuiItem("next-page"));

        inv.setItem(SEND_BTN_SLOT, ConfigLoader.getGuiItem("mail-send"));
        inv.setItem(SETTING_BTN_SLOT, ConfigLoader.getGuiItem("setting"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals("우편함")) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();

        switch (slot) {
            case SEND_BTN_SLOT -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new MailSendGUI(plugin).open(player);
            }
            case SETTING_BTN_SLOT -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                new MailSettingGUI(plugin).open(player);
            }
            case PREV_BTN_SLOT -> {
                int currentPage = pageMap.getOrDefault(uuid, 0);
                open(player, currentPage - 1);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case NEXT_BTN_SLOT -> {
                int currentPage = pageMap.getOrDefault(uuid, 0);
                open(player, currentPage + 1);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            default -> {
                if (slot >= MAIL_START_SLOT && slot <= MAIL_END_SLOT) {
                    int currentPage = pageMap.getOrDefault(uuid, 0);
                    int mailIndex = currentPage * PAGE_SIZE + (slot - MAIL_START_SLOT);
                    List<Mail> mails = new ArrayList<>(MailDataManager.getInstance().getMails(uuid));
                    if (mailIndex >= mails.size()) return;

                    Mail mail = mails.get(mailIndex);
                    if (mail.getItem() != null && mail.getItem().getType() != Material.AIR) {
                        player.getInventory().addItem(mail.getItem());
                        MailDataManager.getInstance().removeMail(uuid, mail);
                        player.sendMessage("§a[우편] 아이템을 수령했습니다.");
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                        open(player, currentPage);
                    }
                }
            }
        }
    }
}
