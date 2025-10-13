package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MailSelectGUI implements Listener, InventoryHolder {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_SEARCH = 45;
    private static final int SLOT_PREV = 47;
    private static final int SLOT_CLAIM_ALL = 48;
    private static final int SLOT_DELETE_ALL = 50;
    private static final int SLOT_NEXT = 51;
    private static final int SLOT_BACK = 53;

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> selectedMails = new ConcurrentHashMap<>();
    private final Set<UUID> waitingForSearch = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> searchTarget = new ConcurrentHashMap<>();

    public MailSelectGUI(Plugin plugin) {
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
        open(player, page, searchTarget.get(player.getUniqueId()));
    }

    public void open(Player player, int page, UUID filterTarget) {
        UUID uuid = player.getUniqueId();

        MailDataManager manager = MailDataManager.getInstance();
        manager.flushNow();
        manager.forceReloadMails(filterTarget != null ? filterTarget : uuid);

        List<Mail> mails = manager.getMails(filterTarget != null ? filterTarget : uuid)
                .stream()
                .filter(mail -> !mail.isExpired())
                .sorted(Comparator.comparingLong(Mail::getCreatedAt).reversed())
                .collect(Collectors.toList());

        int totalPages = Math.max(1, (int) Math.ceil((double) mails.size() / PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        pageMap.put(uuid, safePage);

        selectedMails.putIfAbsent(uuid, new HashSet<>());

        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(lang, "gui.mail.select_title")
                .replace("%page%", String.valueOf(safePage + 1))
                .replace("%maxpage%", String.valueOf(totalPages));
        Inventory inv = Bukkit.createInventory(this, 54, title);

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mails.size());

        for (int i = start; i < end; i++) {
            Mail mail = mails.get(i);
            ItemStack item = mail.toItemStack(player);
            if (item == null) continue;

            if (selectedMails.get(uuid).contains(mail.getMailId())) {
                item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add(LangManager.get(lang, "gui.mail.selected"));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }
            inv.setItem(i - start, item);
        }

        inv.setItem(SLOT_SEARCH, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH).clone())
                .name("§b" + LangManager.get(lang, "gui.search.name"))
                .lore(LangManager.get(lang, "gui.exclude.search.prompt"))
                .build());

        if (safePage > 0) {
            inv.setItem(SLOT_PREV, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON).clone())
                    .name(LangManager.get(lang, "gui.previous"))
                    .build());
        }
        if (end < mails.size()) {
            inv.setItem(SLOT_NEXT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON).clone())
                    .name(LangManager.get(lang, "gui.next"))
                    .build());
        }

        inv.setItem(SLOT_CLAIM_ALL, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_CLAIM_SELECTED_BUTTON).clone())
                .name(LangManager.get(lang, "gui.mail.claim_selected"))
                .lore(LangManager.getList(lang, "gui.mail.claim_selected_lore"))
                .build());

        inv.setItem(SLOT_DELETE_ALL, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_DELETE_SELECTED_BUTTON).clone())
                .name(LangManager.get(lang, "gui.mail.delete_selected"))
                .lore(LangManager.getList(lang, "gui.mail.delete_selected_lore"))
                .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON).clone())
                .name(LangManager.get(lang, "gui.back.name"))
                .lore(LangManager.getList(lang, "gui.back.lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSelectGUI) || !(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();
        int currentPage = pageMap.getOrDefault(uuid, 0);

        MailDataManager manager = MailDataManager.getInstance();

        switch (slot) {
            case SLOT_SEARCH -> {
                player.closeInventory();
                waitingForSearch.add(uuid);
                player.sendMessage(LangManager.get(uuid, "gui.exclude.search.prompt"));
            }
            case SLOT_PREV -> open(player, currentPage - 1, searchTarget.get(uuid));
            case SLOT_NEXT -> open(player, currentPage + 1, searchTarget.get(uuid));
            case SLOT_CLAIM_ALL -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                Set<UUID> mails = selectedMails.getOrDefault(uuid, Collections.emptySet());
                if (mails.isEmpty()) {
                    player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
                } else {
                    for (UUID mailId : new HashSet<>(mails)) {
                        Mail mail = manager.getMailById(mailId);
                        if (mail == null) continue;
                        List<ItemStack> remain = claimItems(player, mail.getItems());
                        if (remain.isEmpty()) {
                            manager.removeMail(mail);
                        } else {
                            mail.setItems(remain);
                            manager.updateMail(mail);
                        }
                    }
                    manager.flushNow();
                    selectedMails.get(uuid).clear();
                    player.sendMessage(LangManager.get(uuid, "gui.mail.claim_success"));
                    open(player, currentPage, searchTarget.get(uuid));
                }
            }
            case SLOT_DELETE_ALL -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                Set<UUID> mails = selectedMails.getOrDefault(uuid, Collections.emptySet());
                if (mails.isEmpty()) {
                    player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
                } else {
                    List<Mail> mailObjs = mails.stream()
                            .map(manager::getMailById)
                            .filter(Objects::nonNull)
                            .toList();
                    new MailDeleteConfirmGUI(player, plugin, mailObjs).open(player);
                }
            }
            case SLOT_BACK -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                MailManager.getInstance().mailSettingGUI.open(player);
            }
            default -> {
                if (slot < PAGE_SIZE) {
                    List<Mail> mails = manager.getMails(searchTarget.getOrDefault(uuid, uuid))
                            .stream().filter(mail -> !mail.isExpired())
                            .sorted(Comparator.comparingLong(Mail::getCreatedAt).reversed())
                            .toList();

                    int mailIndex = currentPage * PAGE_SIZE + slot;
                    if (mailIndex >= mails.size()) return;
                    Mail mail = mails.get(mailIndex);

                    if (selectedMails.get(uuid).contains(mail.getMailId())) {
                        selectedMails.get(uuid).remove(mail.getMailId());
                    } else {
                        selectedMails.get(uuid).add(mail.getMailId());
                    }
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                    open(player, currentPage, searchTarget.get(uuid));
                }
            }
        }
    }

    private List<ItemStack> claimItems(Player player, List<ItemStack> items) {
        if (items.isEmpty()) return Collections.emptyList();
        List<ItemStack> remainAll = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> remain = player.getInventory().addItem(item.clone());
            if (!remain.isEmpty()) remainAll.addAll(remain.values());
        }
        return remainAll;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MailSelectGUI) {
            UUID uuid = e.getPlayer().getUniqueId();
            pageMap.remove(uuid);
            waitingForSearch.remove(uuid);
            searchTarget.remove(uuid);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!waitingForSearch.remove(uuid)) return;

        e.setCancelled(true);
        String input = e.getMessage();
        OfflinePlayer target = PlayerCache.getByName(input);

        Bukkit.getScheduler().runTask(plugin, () -> {
            int currentPage = pageMap.getOrDefault(uuid, 0);
            if (target == null || target.getName() == null) {
                player.sendMessage(LangManager.get(uuid, "gui.exclude.notfound").replace("%input%", input));
                open(player, currentPage, null);
                return;
            }
            searchTarget.put(uuid, target.getUniqueId());
            open(player, 0, target.getUniqueId());
        });
    }
}
