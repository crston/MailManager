package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
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
    private final Map<UUID, UUID> senderFilterMap = new ConcurrentHashMap<>();

    public MailSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54);
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        selectedMails.putIfAbsent(uuid, new HashSet<>());
        open(player, 0);
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        UUID filterSender = senderFilterMap.get(uuid);

        MailDataManager manager = MailDataManager.getInstance();
        manager.flushNow();
        manager.forceReloadMails(uuid);

        List<Mail> mails = manager.getMails(uuid).stream()
                .filter(mail -> !mail.isExpired())
                .filter(mail -> filterSender == null || mail.getSender().equals(filterSender))
                .sorted(Comparator.comparingLong(Mail::getCreatedAt).reversed())
                .collect(Collectors.toList());

        int totalPages = Math.max(1, (int) Math.ceil((double) mails.size() / PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        pageMap.put(uuid, safePage);

        Set<UUID> currentSelected = selectedMails.getOrDefault(uuid, new HashSet<>());
        String lang = LangManager.getLanguage(uuid);
        String title = (filterSender != null) ?
                LangManager.get(lang, "gui.mail.select_title_filtered").replace("%sender%", manager.getGlobalName(filterSender)).replace("%page%", String.valueOf(safePage + 1)) :
                LangManager.get(lang, "gui.mail.select_title").replace("%page%", String.valueOf(safePage + 1)).replace("%maxpage%", String.valueOf(totalPages));

        if (title.length() > 32) title = title.substring(0, 32);
        Inventory inv = Bukkit.createInventory(this, 54, title);

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mails.size());

        for (int i = start; i < end; i++) {
            Mail mail = mails.get(i);
            ItemStack item = mail.toItemStack(player);
            if (item == null) continue;

            if (currentSelected.contains(mail.getMailId())) {
                item.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
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

        inv.setItem(SLOT_SEARCH, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH)).name(LangManager.get(lang, "gui.search.name")).lore(LangManager.getList(lang, "gui.mail.search_lore")).build());
        if (safePage > 0) inv.setItem(SLOT_PREV, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON)).name(LangManager.get(lang, "gui.previous")).build());
        if (end < mails.size()) inv.setItem(SLOT_NEXT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON)).name(LangManager.get(lang, "gui.next")).build());
        inv.setItem(SLOT_CLAIM_ALL, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_CLAIM_SELECTED_BUTTON)).name(LangManager.get(lang, "gui.mail.claim_selected")).lore(LangManager.getList(lang, "gui.mail.claim_selected_lore")).build());
        inv.setItem(SLOT_DELETE_ALL, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_DELETE_SELECTED_BUTTON)).name(LangManager.get(lang, "gui.mail.delete_selected")).lore(LangManager.getList(lang, "gui.mail.delete_selected_lore")).build());
        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON)).name(LangManager.get(lang, "gui.back.name")).build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSelectGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0) return;

        UUID uuid = player.getUniqueId();
        int currentPage = pageMap.getOrDefault(uuid, 0);
        MailDataManager manager = MailDataManager.getInstance();

        if (slot == SLOT_SEARCH) {
            waitingForSearch.add(uuid);
            player.closeInventory();
            player.sendMessage(LangManager.get(uuid, "gui.mail.search_prompt"));
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
        } else if (slot == SLOT_PREV) {
            open(player, currentPage - 1);
        } else if (slot == SLOT_NEXT) {
            open(player, currentPage + 1);
        } else if (slot == SLOT_CLAIM_ALL) {
            Set<UUID> selectedIds = selectedMails.getOrDefault(uuid, new HashSet<>());
            if (selectedIds.isEmpty()) {
                player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
            } else {
                int count = 0;
                for (UUID mailId : new HashSet<>(selectedIds)) {
                    Mail mail = manager.getMailById(mailId);
                    if (mail == null) continue;
                    List<ItemStack> remain = claimItems(player, mail.getItems());
                    if (remain.isEmpty()) { manager.removeMail(mail); }
                    else { mail.setItems(remain); manager.updateMail(mail); }
                    count++;
                }
                selectedMails.get(uuid).clear();
                player.sendMessage(LangManager.get(uuid, "gui.mail.claim_success_count").replace("%count%", String.valueOf(count)));
                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
                open(player, currentPage);
            }
        } else if (slot == SLOT_DELETE_ALL) {
            Set<UUID> selectedIds = selectedMails.getOrDefault(uuid, new HashSet<>());
            if (selectedIds.isEmpty()) {
                player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
            } else {
                List<Mail> mailObjs = selectedIds.stream().map(manager::getMailById).filter(Objects::nonNull).collect(Collectors.toList());
                MailManager.getInstance().mailDeleteConfirmGUI.open(player, mailObjs);
            }
        } else if (slot == SLOT_BACK) {
            MailManager.getInstance().mailGUI.open(player);
        } else if (slot < PAGE_SIZE) {
            UUID filterSender = senderFilterMap.get(uuid);
            List<Mail> mails = manager.getMails(uuid).stream().filter(m -> !m.isExpired() && (filterSender == null || m.getSender().equals(filterSender))).sorted(Comparator.comparingLong(Mail::getCreatedAt).reversed()).collect(Collectors.toList());
            int idx = currentPage * PAGE_SIZE + slot;
            if (idx < mails.size()) {
                Mail m = mails.get(idx);
                Set<UUID> userSelected = selectedMails.get(uuid);
                if (userSelected.contains(m.getMailId())) userSelected.remove(m.getMailId());
                else userSelected.add(m.getMailId());
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                open(player, currentPage);
            }
        }
    }

    private List<ItemStack> claimItems(Player player, List<ItemStack> items) {
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
            if (!waitingForSearch.contains(uuid)) {
                // 검색 모드가 아닐 때만 일부 데이터 정리 (선택 목록은 사용성에 따라 유지 여부 결정 가능)
                pageMap.remove(uuid);
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!waitingForSearch.remove(uuid)) return;

        e.setCancelled(true);
        String input = e.getMessage().trim();
        if (input.equalsIgnoreCase("reset") || input.equalsIgnoreCase("cancel")) {
            senderFilterMap.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> open(player, 0));
            return;
        }

        MailDataManager.getInstance().getGlobalUUID(input).thenAccept(target -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (target == null) {
                    player.sendMessage(LangManager.get(uuid, "cmd.player.notfound").replace("%name%", input));
                    open(player, 0);
                } else {
                    senderFilterMap.put(uuid, target);
                    selectedMails.get(uuid).clear();
                    open(player, 0);
                }
            });
        });
    }
}