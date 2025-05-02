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
    private final Map<UUID, Integer> pageMap = new HashMap<>();
    private static final int PAGE_SIZE = 27;

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

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, mails.get(i).toItemStack());
        }

        // 컨트롤 버튼
        if (page > 0) inv.setItem(48, ConfigLoader.getGuiItem("previous-page"));
        if (end < mails.size()) inv.setItem(50, ConfigLoader.getGuiItem("next-page"));
        inv.setItem(45, ConfigLoader.getGuiItem("mail-send"));
        inv.setItem(8, ConfigLoader.getGuiItem("setting"));

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
        int rawSlot = e.getRawSlot();
        int page = pageMap.getOrDefault(uuid, 0);
        List<Mail> mails = MailDataManager.getInstance().getMails(uuid);
        int index = page * PAGE_SIZE + rawSlot;

        // 메일 클릭 처리
        if (rawSlot < PAGE_SIZE && index < mails.size()) {
            Mail mail = mails.get(index);

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
            return;
        }

        // 하단 버튼 처리
        switch (rawSlot) {
            case 45 -> new MailSendGUI(plugin).open(player);
            case 48 -> open(player, page - 1);
            case 50 -> open(player, page + 1);
            case 8 -> new MailSettingGUI(plugin).open(player);
        }
    }
}
