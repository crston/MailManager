package com.gmail.bobason01.gui;

import com.gmail.bobason01.lang.LangManager;
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
import org.bukkit.inventory.meta.ItemMeta;
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
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        List<Mail> mails = new ArrayList<>(MailDataManager.getInstance().getMails(uuid));

        mails.removeIf(mail -> {
            if (mail.isExpired()) {
                MailDataManager.getInstance().removeMail(uuid, mail);
                return true;
            }
            return false;
        });

        int totalPages = Math.max(1, (int) Math.ceil((double) mails.size() / PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        pageMap.put(uuid, page);

        String title = LangManager.get(uuid, "gui.mail.title");
        Inventory inv = Bukkit.createInventory(player, 54, title);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mails.size());

        int slot = MAIL_START_SLOT;
        for (int i = start; i < end && slot <= MAIL_END_SLOT; i++, slot++) {
            ItemStack item = mails.get(i).toItemStack();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(LangManager.get(uuid, "gui.mail.lore.claim"));
                lore.add(LangManager.get(uuid, "gui.mail.lore.delete"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        }

        if (page > 0) inv.setItem(PREV_BTN_SLOT, createButton(Material.ARROW, LangManager.get(uuid, "gui.prev.name")));
        if (end < mails.size()) inv.setItem(NEXT_BTN_SLOT, createButton(Material.ARROW, LangManager.get(uuid, "gui.next.name")));

        inv.setItem(SEND_BTN_SLOT, createButton(Material.WRITABLE_BOOK, LangManager.get(uuid, "gui.send.title")));
        inv.setItem(SETTING_BTN_SLOT, createButton(Material.COMPARATOR, LangManager.get(uuid, "gui.setting.title")));

        player.openInventory(inv);
    }

    private ItemStack createButton(Material material, String name) {
        ItemStack item = new ItemStack(material);
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

        String expectedTitle = LangManager.get(player.getUniqueId(), "gui.mail.title");
        if (!e.getView().getTitle().equals(expectedTitle)) return;

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
                    List<Mail> mails = new ArrayList<>(MailDataManager.getInstance().getMails(uuid));

                    mails.removeIf(mail -> {
                        if (mail.isExpired()) {
                            MailDataManager.getInstance().removeMail(uuid, mail);
                            return true;
                        }
                        return false;
                    });

                    int mailIndex = currentPage * PAGE_SIZE + (slot - MAIL_START_SLOT);
                    if (mailIndex >= mails.size()) return;

                    Mail mail = mails.get(mailIndex);

                    if (e.getClick().isShiftClick() && e.getClick().isRightClick()) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                        new MailDeleteConfirmGUI(plugin, mail, this, currentPage).open(player);
                        return;
                    }

                    if (mail.getItem() != null && mail.getItem().getType() != Material.AIR) {
                        player.getInventory().addItem(mail.getItem());
                    }
                    MailDataManager.getInstance().removeMail(uuid, mail);
                    player.sendMessage(LangManager.get(uuid, "mail.claim_success"));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                    open(player, currentPage);
                }
            }
        }
    }
}
