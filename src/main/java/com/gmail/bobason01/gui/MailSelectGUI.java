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
import org.bukkit.event.EventPriority;
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
        return Bukkit.createInventory(this, 54, "Select Mail");
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        selectedMails.putIfAbsent(uuid, new HashSet<>());

        MailDataManager manager = MailDataManager.getInstance();
        UUID filterSender = senderFilterMap.get(uuid);

        // [최적화] 매번 flush/forceReload 하지 않고 메모리 내 데이터를 스트림으로 필터링
        List<Mail> mails = manager.getMails(uuid).stream()
                .filter(mail -> !mail.isExpired())
                .filter(mail -> filterSender == null || mail.getSender().equals(filterSender))
                .sorted(Comparator.comparingLong(Mail::getCreatedAt).reversed())
                .collect(Collectors.toList());

        int totalPages = Math.max(1, (int) Math.ceil((double) mails.size() / PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        pageMap.put(uuid, safePage);

        String lang = LangManager.getLanguage(uuid);
        String titleKey = (filterSender != null) ? "gui.mail.select_title_filtered" : "gui.mail.select_title";
        String title = LangManager.get(lang, titleKey)
                .replace("%page%", String.valueOf(safePage + 1))
                .replace("%maxpage%", String.valueOf(totalPages));

        if (filterSender != null) {
            title = title.replace("%sender%", manager.getGlobalName(filterSender));
        }

        // 타이틀 길이 방어 코드
        if (title.length() > 32) title = title.substring(0, 29) + "...";
        Inventory inv = Bukkit.createInventory(this, 54, title);

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mails.size());
        Set<UUID> currentSelected = selectedMails.get(uuid);

        for (int i = start; i < end; i++) {
            Mail mail = mails.get(i);
            ItemStack item = mail.toItemStack(player);
            if (item == null) continue;

            // 선택된 아이템 시각 효과 (인챈트 광채)
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

        // 하단 버튼 배치
        setupButtons(inv, lang, safePage, end < mails.size());
        player.openInventory(inv);
    }

    private void setupButtons(Inventory inv, String lang, int page, boolean hasNext) {
        inv.setItem(SLOT_SEARCH, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH)).name(LangManager.get(lang, "gui.search.name")).lore(LangManager.getList(lang, "gui.mail.search_lore")).build());
        if (page > 0) inv.setItem(SLOT_PREV, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON)).name(LangManager.get(lang, "gui.previous")).build());
        if (hasNext) inv.setItem(SLOT_NEXT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON)).name(LangManager.get(lang, "gui.next")).build());
        inv.setItem(SLOT_CLAIM_ALL, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_CLAIM_SELECTED_BUTTON)).name(LangManager.get(lang, "gui.mail.claim_selected")).lore(LangManager.getList(lang, "gui.mail.claim_selected_lore")).build());
        inv.setItem(SLOT_DELETE_ALL, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_DELETE_SELECTED_BUTTON)).name(LangManager.get(lang, "gui.mail.delete_selected")).lore(LangManager.getList(lang, "gui.mail.delete_selected_lore")).build());
        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON)).name(LangManager.get(lang, "gui.back.name")).build());
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
            handleBulkClaim(player, currentPage);
        } else if (slot == SLOT_DELETE_ALL) {
            handleBulkDelete(player);
        } else if (slot == SLOT_BACK) {
            MailManager.getInstance().mailGUI.open(player);
        } else if (slot < PAGE_SIZE) {
            handleMailSelection(player, slot, currentPage);
        }
    }

    private void handleMailSelection(Player player, int slot, int page) {
        UUID uuid = player.getUniqueId();
        UUID filter = senderFilterMap.get(uuid);
        MailDataManager manager = MailDataManager.getInstance();

        List<Mail> mails = manager.getMails(uuid).stream()
                .filter(m -> !m.isExpired() && (filter == null || m.getSender().equals(filter)))
                .sorted(Comparator.comparingLong(Mail::getCreatedAt).reversed())
                .collect(Collectors.toList());

        int idx = page * PAGE_SIZE + slot;
        if (idx < mails.size()) {
            UUID mailId = mails.get(idx).getMailId();
            Set<UUID> selected = selectedMails.get(uuid);
            if (selected.contains(mailId)) selected.remove(mailId);
            else selected.add(mailId);
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            open(player, page);
        }
    }

    private void handleBulkClaim(Player player, int page) {
        UUID uuid = player.getUniqueId();
        Set<UUID> selectedIds = selectedMails.getOrDefault(uuid, new HashSet<>());
        if (selectedIds.isEmpty()) {
            player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
            return;
        }

        MailDataManager manager = MailDataManager.getInstance();
        int count = 0;
        for (UUID mailId : new HashSet<>(selectedIds)) {
            Mail mail = manager.getMailById(mailId);
            if (mail == null) continue;

            List<ItemStack> items = new ArrayList<>(mail.getItems());
            List<ItemStack> remain = new ArrayList<>();

            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) continue;
                Map<Integer, ItemStack> left = player.getInventory().addItem(item.clone());
                if (!left.isEmpty()) remain.addAll(left.values());
            }

            if (remain.isEmpty()) manager.removeMail(mail);
            else {
                mail.setItems(remain);
                manager.updateMail(mail);
            }
            count++;
        }
        selectedMails.get(uuid).clear();
        player.sendMessage(LangManager.get(uuid, "gui.mail.claim_success_count").replace("%count%", String.valueOf(count)));
        ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
        open(player, page);
    }

    private void handleBulkDelete(Player player) {
        UUID uuid = player.getUniqueId();
        Set<UUID> selectedIds = selectedMails.get(uuid);
        if (selectedIds == null || selectedIds.isEmpty()) {
            player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
            return;
        }

        List<Mail> mailObjs = selectedIds.stream()
                .map(MailDataManager.getInstance()::getMailById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        MailManager.getInstance().mailDeleteConfirmGUI.open(player, mailObjs);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!waitingForSearch.contains(uuid)) return;

        e.setCancelled(true);
        waitingForSearch.remove(uuid); // 즉시 제거하여 중복 방지

        String input = e.getMessage().trim();
        if (input.equalsIgnoreCase("reset") || input.equalsIgnoreCase("cancel")) {
            senderFilterMap.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> open(player, 0));
            return;
        }

        // 비동기 스레드에서 UUID 검색 후 메인 스레드에서 GUI 오픈
        MailDataManager.getInstance().getGlobalUUID(input).thenAccept(target -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (target == null) {
                    player.sendMessage(LangManager.get(uuid, "cmd.player.notfound").replace("%name%", input));
                } else {
                    senderFilterMap.put(uuid, target);
                    selectedMails.get(uuid).clear();
                }
                open(player, 0);
            });
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MailSelectGUI) {
            UUID uuid = e.getPlayer().getUniqueId();
            if (!waitingForSearch.contains(uuid)) {
                pageMap.remove(uuid);
                // 선택 목록은 유저 편의를 위해 명시적으로 지우지 않음 (필요 시 여기서 clear)
            }
        }
    }
}