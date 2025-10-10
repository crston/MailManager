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
    private static final int MAX_ITEMS = 36;      // 아이템 슬롯 0~35
    private static final int SLOT_CLAIM_ALL = 48; // 모든 아이템 수령
    private static final int SLOT_DELETE = 50;    // 우편 삭제
    private static final int SLOT_BACK = 53;      // 뒤로가기

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

        inv = Bukkit.createInventory(this, SIZE, LangManager.get(owner, "gui.mail.view.title"));

        refreshInventory();
        player.openInventory(inv);
    }

    private void refreshInventory() {
        inv.clear();

        // 아이템 다시 채우기
        List<ItemStack> items = mail.getItems();
        for (int i = 0; i < Math.min(MAX_ITEMS, items.size()); i++) {
            ItemStack item = items.get(i);
            if (item != null && item.getType() != Material.AIR) {
                inv.setItem(i, item.clone());
            }
        }

        String lang = LangManager.getLanguage(owner);

        // 모든 아이템 수령 버튼
        inv.setItem(SLOT_CLAIM_ALL, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_CLAIM_BUTTON).clone())
                .name(LangManager.get(lang, "gui.mail.claim_all"))
                .build());

        // 우편 삭제 버튼
        inv.setItem(SLOT_DELETE, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_DELETE_BUTTON).clone())
                .name(LangManager.get(lang, "gui.mail.delete"))
                .build());

        // 뒤로가기 버튼
        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON).clone())
                .name("§c" + LangManager.get(lang, "gui.back.name"))
                .build());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailViewGUI) || !(e.getWhoClicked() instanceof Player player)) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        UUID uuid = player.getUniqueId();
        e.setCancelled(true);

        if (slot == SLOT_BACK) {
            new MailGUI(plugin).open(player);
            return;
        }

        if (slot == SLOT_DELETE) {
            MailDataManager.getInstance().removeMail(mail);
            player.sendMessage(LangManager.get(uuid, "mail.deleted"));
            new MailGUI(plugin).open(player);
            return;
        }

        if (slot == SLOT_CLAIM_ALL) {
            claimItems(player, new ArrayList<>(mail.getItems()));
            mail.getItems().clear();
            MailDataManager.getInstance().removeMail(mail);
            player.sendMessage(LangManager.get(uuid, "mail.claim_success"));
            new MailGUI(plugin).open(player);
            return;
        }

        // 개별 아이템 수령
        if (slot < MAX_ITEMS) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                claimItems(player, Collections.singletonList(clicked.clone()));

                List<ItemStack> items = mail.getItems();
                if (slot < items.size()) {
                    items.remove(slot);
                }

                if (mail.getItems().isEmpty()) {
                    MailDataManager.getInstance().removeMail(mail);
                    new MailGUI(plugin).open(player);
                } else {
                    MailDataManager.getInstance().updateMail(mail);
                    refreshInventory();
                }
            }
        }
    }

    private void claimItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> remain = player.getInventory().addItem(item.clone());
            for (ItemStack leftover : remain.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailViewGUI) || !(e.getPlayer() instanceof Player)) return;

        if (mail.getItems().isEmpty()) {
            MailDataManager.getInstance().removeMail(mail);
        } else {
            MailDataManager.getInstance().updateMail(mail);
        }
    }
}
