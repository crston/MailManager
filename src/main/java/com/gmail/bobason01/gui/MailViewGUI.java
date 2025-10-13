package com.gmail.bobason01.gui;

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

public class MailViewGUI implements Listener, InventoryHolder {

    private static final int SIZE = 54;
    private static final int MAX_ITEMS = 36;
    private static final int SLOT_CLAIM_ALL = 48;
    private static final int SLOT_DELETE = 50;
    private static final int SLOT_BACK = 53;

    private final Plugin plugin;
    private Inventory inv;
    private Mail mail;
    private UUID owner;

    public MailViewGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void open(Player player, Mail mail) {
        this.mail = mail;
        this.owner = player.getUniqueId();
        this.inv = Bukkit.createInventory(this, SIZE, LangManager.get(owner, "gui.mail.view.title"));
        refreshInventory();
        player.openInventory(inv);
    }

    private void refreshInventory() {
        for (int i = 0; i < MAX_ITEMS; i++) {
            inv.setItem(i, null);
        }

        List<ItemStack> items = mail.getItems();
        int max = Math.min(MAX_ITEMS, items.size());
        for (int j = 0; j < max; j++) {
            ItemStack item = items.get(j);
            if (item != null && item.getType() != Material.AIR) {
                inv.setItem(j, item.clone());
            }
        }

        String lang = LangManager.getLanguage(owner);

        inv.setItem(SLOT_CLAIM_ALL, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_CLAIM_BUTTON))
                .name(LangManager.get(lang, "gui.mail.claim_all"))
                .lore(LangManager.getList(lang, "gui.mail.claim_selected_lore"))
                .build());

        inv.setItem(SLOT_DELETE, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_DELETE_BUTTON))
                .name(LangManager.get(lang, "gui.mail.delete"))
                .lore(LangManager.getList(lang, "gui.mail.delete_selected_lore"))
                .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(lang, "gui.back.name"))
                .lore(LangManager.getList(lang, "gui.back.lore"))
                .build());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailViewGUI gui)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        e.setCancelled(true);

        if (slot == SLOT_BACK) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            new MailGUI(plugin).open(player);
            return;
        }

        if (slot == SLOT_DELETE) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            new MailDeleteConfirmGUI(player, plugin, Collections.singletonList(gui.mail)).open(player);
            return;
        }

        if (slot == SLOT_CLAIM_ALL) {
            gui.handleClaimAll(player);
            return;
        }

        if (slot < MAX_ITEMS) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                gui.handleClaimSingle(player, clicked, slot);
            }
        }
    }

    private void handleClaimAll(Player player) {
        UUID uuid = player.getUniqueId();
        MailDataManager manager = MailDataManager.getInstance();

        List<ItemStack> remain = claimItems(player, mail.getItems());
        if (remain.isEmpty()) {
            // 모든 슬롯 null 처리
            for (int i = 0; i < mail.getItems().size(); i++) {
                mail.getItems().set(i, null);
            }
            manager.removeMail(mail);
        } else {
            mail.setItems(remain);
            manager.updateMail(mail);
        }
        manager.flushNow();
        manager.forceReloadMails(uuid);

        if (remain.isEmpty()) {
            player.sendMessage(LangManager.get(uuid, "mail.claim_success"));
            ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
            new MailGUI(plugin).open(player);
        } else {
            player.sendMessage(LangManager.get(uuid, "mail.inventory_full"));
            ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_FAIL);
            refreshInventory();
        }
    }

    private void handleClaimSingle(Player player, ItemStack clicked, int slot) {
        UUID uuid = player.getUniqueId();
        MailDataManager manager = MailDataManager.getInstance();

        List<ItemStack> remain = claimItems(player, Collections.singletonList(clicked));
        if (remain.isEmpty()) {
            // 클릭한 슬롯만 null 처리
            if (slot < mail.getItems().size()) {
                mail.getItems().set(slot, null);
            }

            if (mail.getItems().stream().allMatch(i -> i == null || i.getType() == Material.AIR)) {
                manager.removeMail(mail);
            } else {
                manager.updateMail(mail);
            }
        }
        manager.flushNow();
        manager.forceReloadMails(uuid);

        if (remain.isEmpty()) {
            ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
            if (mail.getItems().stream().allMatch(i -> i == null || i.getType() == Material.AIR)) {
                new MailGUI(plugin).open(player);
            } else {
                inv.setItem(slot, null);
            }
        } else {
            player.sendMessage(LangManager.get(uuid, "mail.inventory_full"));
            ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_FAIL);
        }
    }

    private List<ItemStack> claimItems(Player player, List<ItemStack> items) {
        if (items.isEmpty()) return Collections.emptyList();
        List<ItemStack> remainAll = new ArrayList<>();

        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> remain = player.getInventory().addItem(item.clone());
            if (!remain.isEmpty()) {
                remainAll.addAll(remain.values());
            }
        }
        return remainAll;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailViewGUI gui)) return;
        MailDataManager manager = MailDataManager.getInstance();

        if (gui.mail != null) {
            if (gui.mail.getItems().stream().allMatch(i -> i == null || i.getType() == Material.AIR)) {
                manager.removeMail(gui.mail);
            } else {
                manager.updateMail(gui.mail);
            }
            manager.flushNow();
            manager.forceReloadMails(gui.owner);
        }
    }
}
