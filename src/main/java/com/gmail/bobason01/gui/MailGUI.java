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
import java.util.stream.Collectors;

public class MailGUI implements Listener, InventoryHolder {

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new WeakHashMap<>();
    private final Map<UUID, Mail> mailToDelete = new WeakHashMap<>();

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
        return null;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();

        List<Mail> mails = MailDataManager.getInstance().getMails(uuid).stream()
                .filter(mail -> !mail.isExpired())
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(mails);

        int totalPages = Math.max(1, (int) Math.ceil((double) mails.size() / PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        pageMap.put(uuid, page);

        String title = LangManager.get(uuid, "gui.mail.title");
        Inventory inv = Bukkit.createInventory(this, 54, title);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mails.size());

        for (int i = start; i < end; i++) {
            Mail mail = mails.get(i);
            ItemStack item = mail.toItemStack(player);
            if (item == null) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(" ");
                lore.add(LangManager.get(uuid, "gui.mail.lore.claim"));
                lore.add(LangManager.get(uuid, "gui.mail.lore.delete"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(i - start, item);
        }

        if (page > 0) inv.setItem(PREV_BTN_SLOT, createButton(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON), LangManager.get(uuid, "gui.previous")));
        if (end < mails.size()) inv.setItem(NEXT_BTN_SLOT, createButton(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON), LangManager.get(uuid, "gui.next")));

        inv.setItem(SEND_BTN_SLOT, createButton(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SEND_BUTTON), LangManager.get(uuid, "gui.send.title")));
        inv.setItem(SETTING_BTN_SLOT, createButton(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SETTING_BUTTON), LangManager.get(uuid, "gui.setting.title")));

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

        InventoryHolder holder = e.getInventory().getHolder();

        if (holder instanceof MailGUI) {
            handleMailGUIClick(e, player);
        } else if (holder instanceof MailDeleteConfirmGUI) {
            handleDeleteConfirmGUIClick(e, player);
        }
    }

    private void handleMailGUIClick(InventoryClickEvent e, Player player) {
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();
        int currentPage = pageMap.getOrDefault(uuid, 0);

        MailManager manager = MailManager.getInstance();

        switch (slot) {
            case SEND_BTN_SLOT:
                player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.GUI_CLICK), 1.0f, 1.0f);
                manager.mailSendGUI.open(player);
                break;
            case SETTING_BTN_SLOT:
                player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.GUI_CLICK), 1.0f, 1.0f);
                manager.mailSettingGUI.open(player);
                break;
            case PREV_BTN_SLOT:
                player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.GUI_PAGE_TURN), 1.0f, 1.0f);
                open(player, currentPage - 1);
                break;
            case NEXT_BTN_SLOT:
                player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.GUI_PAGE_TURN), 1.0f, 1.0f);
                open(player, currentPage + 1);
                break;
            default:
                if (slot < PAGE_SIZE) {
                    List<Mail> mails = new ArrayList<>(MailDataManager.getInstance().getMails(uuid));
                    Collections.reverse(mails);

                    int mailIndex = currentPage * PAGE_SIZE + slot;
                    if (mailIndex >= mails.size()) return;
                    Mail mail = mails.get(mailIndex);

                    if (e.getClick() == ClickType.RIGHT) {
                        player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.GUI_CLICK), 1.0f, 1.0f);
                        mailToDelete.put(uuid, mail);
                        new MailDeleteConfirmGUI(player).open(player);
                        return;
                    }

                    ItemStack item = mail.getItem();
                    if (item != null && item.getType() != Material.AIR) {
                        if (player.getInventory().firstEmpty() == -1) {
                            player.sendMessage(LangManager.get(uuid, "mail.receive.failed"));
                            player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.MAIL_CLAIM_FAIL), 1.0f, 1.0f);
                            return;
                        }
                        player.getInventory().addItem(item);
                    }

                    MailDataManager.getInstance().removeMail(uuid, mail);
                    player.sendMessage(LangManager.get(uuid, "mail.claim_success"));
                    player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.MAIL_CLAIM_SUCCESS), 1.0f, 1.2f);
                    open(player, currentPage);
                }
                break;
        }
    }

    private void handleDeleteConfirmGUIClick(InventoryClickEvent e, Player player) {
        e.setCancelled(true);
        int slot = e.getRawSlot();
        UUID uuid = player.getUniqueId();
        int currentPage = pageMap.getOrDefault(uuid, 0);
        Mail mail = mailToDelete.remove(uuid);

        if (mail == null) {
            open(player, currentPage);
            return;
        }

        if (slot == MailDeleteConfirmGUI.YES_SLOT) {
            MailDataManager.getInstance().removeMail(uuid, mail);
            player.sendMessage(LangManager.get(uuid, "mail.deleted"));
            player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.MAIL_DELETE_SUCCESS), 1.0f, 1.0f);
        } else if (slot == MailDeleteConfirmGUI.NO_SLOT) {
            player.sendMessage(LangManager.get(uuid, "mail.delete_cancel"));
            player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.GUI_CLICK), 1.0f, 1.0f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, currentPage), 2L);
    }
}