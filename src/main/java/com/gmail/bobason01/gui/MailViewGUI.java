package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MailViewGUI implements Listener, InventoryHolder {

    private static final int SLOT_CLAIM_ALL = 49;
    private static final int SLOT_DELETE = 50;
    private static final int SLOT_BACK = 53;

    private final Plugin plugin;
    private final Map<UUID, UUID> viewingMail = new ConcurrentHashMap<>();
    private static final Set<UUID> processingMails = ConcurrentHashMap.newKeySet();

    public MailViewGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54);
    }

    public void open(Player player, Mail mail) {
        if (mail == null) return;
        viewingMail.put(player.getUniqueId(), mail.getMailId());

        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(lang, "gui.mail.view.title");
        Inventory inv = Bukkit.createInventory(this, 54, title);

        List<ItemStack> items = mail.getItems();
        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            ItemStack item = items.get(i);
            if (item != null && item.getType() != Material.AIR) {
                inv.setItem(i, item.clone());
            }
        }

        inv.setItem(SLOT_CLAIM_ALL, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_CLAIM_BUTTON))
                .name(LangManager.get(lang, "gui.mail.claim_all")).build());
        inv.setItem(SLOT_DELETE, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_DELETE_BUTTON))
                .name(LangManager.get(lang, "gui.mail.delete")).build());
        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(lang, "gui.back.name")).build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailViewGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0) return;

        UUID playerUuid = player.getUniqueId();
        UUID mailId = viewingMail.get(playerUuid);
        if (mailId == null) {
            MailManager.getInstance().mailGUI.open(player);
            return;
        }

        if (processingMails.contains(mailId)) return;

        MailDataManager manager = MailDataManager.getInstance();
        Mail mail = manager.getMailById(mailId);

        if (mail == null || mail.isExpired()) {
            player.sendMessage(LangManager.get(playerUuid, "mail.expired"));
            MailManager.getInstance().mailGUI.open(player);
            return;
        }

        try {
            processingMails.add(mailId);

            if (slot < 45) {
                handleSingleClaim(player, mail, slot);
            } else if (slot == SLOT_CLAIM_ALL) {
                handleAllClaim(player, mail);
            } else if (slot == SLOT_DELETE) {
                new MailDeleteConfirmGUI(plugin).open(player, Collections.singletonList(mail));
            } else if (slot == SLOT_BACK) {
                MailManager.getInstance().mailGUI.open(player);
            }
        } finally {
            processingMails.remove(mailId);
        }
    }

    private void handleSingleClaim(Player player, Mail mail, int slot) {
        List<ItemStack> items = new ArrayList<>(mail.getItems());
        if (slot >= items.size()) return;

        ItemStack clickedItem = items.get(slot);
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        ItemStack toAdd = clickedItem.clone();
        int originalAmount = toAdd.getAmount();

        Map<Integer, ItemStack> left = player.getInventory().addItem(toAdd);

        if (left.isEmpty()) {
            items.remove(slot);
        } else {
            int leftAmount = 0;
            for (ItemStack leftItem : left.values()) {
                leftAmount += leftItem.getAmount();
            }

            if (leftAmount == originalAmount) {
                player.sendMessage(LangManager.get(player.getUniqueId(), "mail.receive.failed"));
                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_FAIL);
                return;
            }
            clickedItem.setAmount(leftAmount);
        }

        updateMailStatus(player, mail, items);
    }

    private void handleAllClaim(Player player, Mail mail) {
        List<ItemStack> items = new ArrayList<>(mail.getItems());
        List<ItemStack> remaining = new ArrayList<>();
        boolean claimedAny = false;

        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;

            ItemStack toAdd = item.clone();
            int originalAmount = toAdd.getAmount();
            Map<Integer, ItemStack> left = player.getInventory().addItem(toAdd);

            if (left.isEmpty()) {
                claimedAny = true;
            } else {
                int leftAmount = 0;
                for (ItemStack leftItem : left.values()) {
                    leftAmount += leftItem.getAmount();
                    remaining.add(leftItem);
                }
                if (leftAmount < originalAmount) claimedAny = true;
            }
        }

        if (claimedAny) {
            if (remaining.isEmpty()) {
                MailDataManager.getInstance().removeMail(mail);
                player.sendMessage(LangManager.get(player.getUniqueId(), "mail.claim_success"));
                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
                MailManager.getInstance().mailGUI.open(player);
            } else {
                mail.setItems(remaining);
                MailDataManager.getInstance().updateMail(mail);
                player.sendMessage(LangManager.get(player.getUniqueId(), "mail.inventory_full"));
                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
                open(player, mail);
            }
        } else {
            player.sendMessage(LangManager.get(player.getUniqueId(), "mail.receive.failed"));
            ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_FAIL);
        }
    }

    private void updateMailStatus(Player player, Mail mail, List<ItemStack> items) {
        items.removeIf(item -> item == null || item.getType() == Material.AIR);

        MailDataManager manager = MailDataManager.getInstance();
        if (items.isEmpty()) {
            manager.removeMail(mail);
            player.sendMessage(LangManager.get(player.getUniqueId(), "mail.claim_success"));
            ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
            MailManager.getInstance().mailGUI.open(player);
        } else {
            mail.setItems(items);
            manager.updateMail(mail);
            ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
            open(player, mail);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MailViewGUI) {
            viewingMail.remove(e.getPlayer().getUniqueId());
        }
    }
}