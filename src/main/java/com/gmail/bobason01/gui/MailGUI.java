package com.gmail.bobason01.gui;

import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ConfigLoader;
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

    // 메모리 누수 방지: 플레이어가 사라지면 자동 제거
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
        List<Mail> mails = MailDataManager.getInstance().getMails(uuid);

        int totalPages = (int) Math.ceil((double) mails.size() / PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(0, Math.min(page, totalPages - 1));
        pageMap.put(uuid, page);

        Inventory inv = Bukkit.createInventory(player, 54, LangUtil.get("gui.mail.title"));

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mails.size());

        int slot = MAIL_START_SLOT;
        for (int i = start; i < end && slot <= MAIL_END_SLOT; i++, slot++) {
            inv.setItem(slot, mails.get(i).toItemStack());
        }

        // 하단 컨트롤 버튼
        if (page > 0) inv.setItem(PREV_BTN_SLOT, ConfigLoader.getGuiItem("previous-page"));
        if (end < mails.size()) inv.setItem(NEXT_BTN_SLOT, ConfigLoader.getGuiItem("next-page"));

        inv.setItem(SEND_BTN_SLOT, ConfigLoader.getGuiItem("mail-send"));
        inv.setItem(SETTING_BTN_SLOT, ConfigLoader.getGuiItem("setting"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LangUtil.get("gui.mail.title"))) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();
        int page = pageMap.getOrDefault(uuid, 0);
        List<Mail> mails = MailDataManager.getInstance().getMails(uuid);

        // 메일 수신 또는 삭제
        if (slot >= MAIL_START_SLOT && slot <= MAIL_END_SLOT) {
            int mailIndex = page * PAGE_SIZE + (slot - MAIL_START_SLOT);
            if (mailIndex < mails.size()) {
                Mail mail = mails.get(mailIndex);

                if (e.getClick() == ClickType.SHIFT_RIGHT) {
                    MailDataManager.getInstance().removeMail(uuid, mail);
                    player.sendMessage(LangUtil.get("gui.mail.deleted"));
                } else {
                    mail.give(player);
                    MailDataManager.getInstance().removeMail(uuid, mail);
                    player.sendMessage(LangUtil.get("gui.mail.received"));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1, 1);
                }

                open(player, page);
            }
            return;
        }

        // 하단 버튼
        switch (slot) {
            case SEND_BTN_SLOT -> new MailSendGUI(plugin).open(player);
            case PREV_BTN_SLOT -> open(player, page - 1);
            case NEXT_BTN_SLOT -> open(player, page + 1);
            case SETTING_BTN_SLOT -> new MailSettingGUI(plugin).open(player);
        }
    }
}
