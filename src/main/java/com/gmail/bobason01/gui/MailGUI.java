package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class MailGUI implements Listener, InventoryHolder {

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new WeakHashMap<>();
    private final Map<UUID, Boolean> selectModeMap = new WeakHashMap<>();
    private final Map<UUID, Set<Mail>> selectedMails = new WeakHashMap<>();

    private static final int PAGE_SIZE = 45;
    private static final int SEND_BTN_SLOT = 45;
    private static final int SETTING_BTN_SLOT = 8;
    private static final int PREV_BTN_SLOT = 48;
    private static final int MULTI_SELECT_SLOT = 47;
    private static final int MULTI_CLAIM_SLOT = 49;
    private static final int MULTI_DELETE_SLOT = 51;
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

        selectModeMap.putIfAbsent(uuid, false);
        selectedMails.putIfAbsent(uuid, new HashSet<>());

        String title = LangManager.get(uuid, "gui.mail.title");
        Inventory inv = Bukkit.createInventory(this, 54, title);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mails.size());

        for (int i = start; i < end; i++) {
            Mail mail = mails.get(i);
            ItemStack item = mail.toItemStack(player);
            if (item == null) continue;

            if (selectedMails.get(uuid).contains(mail)) {
                item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    item.setItemMeta(meta);
                }
            }

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

        if (page > 0) {
            inv.setItem(PREV_BTN_SLOT, createButton(
                    ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON).clone(),
                    LangManager.get(uuid, "gui.previous")));
        }
        if (end < mails.size()) {
            inv.setItem(NEXT_BTN_SLOT, createButton(
                    ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON).clone(),
                    LangManager.get(uuid, "gui.next")));
        }

        if (player.hasPermission("mail.send")) {
            inv.setItem(SEND_BTN_SLOT, createButton(
                    ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SEND_BUTTON).clone(),
                    LangManager.get(uuid, "gui.send.title")));
        }

        inv.setItem(SETTING_BTN_SLOT, createButton(
                ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SETTING_BUTTON).clone(),
                LangManager.get(uuid, "gui.setting.title")));

        inv.setItem(MULTI_SELECT_SLOT, createButton(
                ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SELECT_BUTTON).clone(),
                LangManager.get(uuid, selectModeMap.get(uuid) ? "gui.mail.select_mode_on" : "gui.mail.select_mode_off")));

        inv.setItem(MULTI_CLAIM_SLOT, createButton(
                ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_CLAIM_BUTTON).clone(),
                LangManager.get(uuid, "gui.mail.claim_selected")));

        inv.setItem(MULTI_DELETE_SLOT, createButton(
                ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_DELETE_BUTTON).clone(),
                LangManager.get(uuid, "gui.mail.delete_selected")));

        player.openInventory(inv);
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
        boolean selectMode = selectModeMap.getOrDefault(uuid, false);
        MailManager manager = MailManager.getInstance();

        switch (slot) {
            case SEND_BTN_SLOT:
                if (!player.hasPermission("mail.send")) {
                    player.sendMessage(LangManager.get(uuid, "mail.send.no_permission"));
                    return;
                }
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailSendGUI.open(player);
                break;
            case SETTING_BTN_SLOT:
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailSettingGUI.open(player);
                break;
            case PREV_BTN_SLOT:
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage - 1);
                break;
            case NEXT_BTN_SLOT:
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage + 1);
                break;
            case MULTI_SELECT_SLOT:
                selectModeMap.put(uuid, !selectMode);
                selectedMails.get(uuid).clear();
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                player.sendMessage(LangManager.get(uuid, selectModeMap.get(uuid) ? "gui.mail.select_mode_on" : "gui.mail.select_mode_off"));
                open(player, currentPage);
                break;
            case MULTI_CLAIM_SLOT:
                if (selectMode && !selectedMails.get(uuid).isEmpty()) {
                    List<Mail> mailsToClaim = new ArrayList<>(selectedMails.get(uuid));
                    boolean allClaimed = true;
                    for (Mail mail : mailsToClaim) {
                        List<ItemStack> remain = claimItems(player, mail.getItems());
                        if (remain.isEmpty()) {
                            MailDataManager.getInstance().removeMail(mail);
                        } else {
                            mail.getItems().clear();
                            mail.getItems().addAll(remain);
                            MailDataManager.getInstance().updateMail(mail);
                            allClaimed = false;
                        }
                    }
                    if (allClaimed) {
                        player.sendMessage(LangManager.get(uuid, "mail.claim_success"));
                        ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
                    } else {
                        player.sendMessage(LangManager.get(uuid, "mail.inventory_full"));
                        ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_FAIL);
                    }
                    selectedMails.get(uuid).clear();
                    open(player, currentPage);
                } else {
                    player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                }
                break;
            case MULTI_DELETE_SLOT:
                if (selectMode && !selectedMails.get(uuid).isEmpty()) {
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                    MailDeleteConfirmGUI confirm = new MailDeleteConfirmGUI(player, plugin, new ArrayList<>(selectedMails.get(uuid)));
                    confirm.open(player);
                } else {
                    player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                }
                break;
            default:
                if (slot < PAGE_SIZE) {
                    List<Mail> mails = MailDataManager.getInstance().getMails(uuid).stream()
                            .filter(mail -> !mail.isExpired())
                            .collect(Collectors.toCollection(ArrayList::new));
                    Collections.reverse(mails);

                    int mailIndex = currentPage * PAGE_SIZE + slot;
                    if (mailIndex >= mails.size()) return;
                    Mail mail = mails.get(mailIndex);

                    if (selectMode) {
                        if (selectedMails.get(uuid).contains(mail)) {
                            selectedMails.get(uuid).remove(mail);
                        } else {
                            selectedMails.get(uuid).add(mail);
                        }
                        ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                        open(player, currentPage);
                    } else {
                        if (e.getClick() == ClickType.RIGHT) {
                            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                            MailDeleteConfirmGUI confirm = new MailDeleteConfirmGUI(player, plugin, Collections.singletonList(mail));
                            confirm.open(player);
                        } else {
                            manager.mailViewGUI.open(player, mail);
                        }
                    }
                }
                break;
        }
    }

    private List<ItemStack> claimItems(Player player, List<ItemStack> items) {
        List<ItemStack> remainAll = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> remain = player.getInventory().addItem(item.clone());
            remainAll.addAll(remain.values());
        }
        return remainAll;
    }
}
