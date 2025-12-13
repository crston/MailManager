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
    // 페이지 정보
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();
    // 선택된 메일 ID 목록
    private final Map<UUID, Set<UUID>> selectedMails = new ConcurrentHashMap<>();
    // 검색 대기 중인 유저
    private final Set<UUID> waitingForSearch = ConcurrentHashMap.newKeySet();
    // 필터링 중인 발신자
    private final Map<UUID, UUID> senderFilterMap = new ConcurrentHashMap<>();

    public MailSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54);
    }

    // 처음 열 때 (초기화)
    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        // 처음 열 때는 선택 목록과 필터를 초기화
        selectedMails.put(uuid, new HashSet<>());
        senderFilterMap.remove(uuid);
        open(player, 0);
    }

    // 페이지 이동 또는 갱신 시 (선택 목록 유지)
    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        UUID filterSender = senderFilterMap.get(uuid);

        MailDataManager manager = MailDataManager.getInstance();
        manager.flushNow();
        manager.forceReloadMails(uuid);

        // 메일 필터링 및 정렬
        List<Mail> mails = manager.getMails(uuid).stream()
                .filter(mail -> !mail.isExpired())
                .filter(mail -> filterSender == null || mail.getSender().equals(filterSender))
                .sorted(Comparator.comparingLong(Mail::getCreatedAt).reversed())
                .collect(Collectors.toList());

        int totalPages = Math.max(1, (int) Math.ceil((double) mails.size() / PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        pageMap.put(uuid, safePage);

        // 선택 목록 가져오기 (없으면 생성)
        Set<UUID> currentSelected = selectedMails.computeIfAbsent(uuid, k -> new HashSet<>());

        String lang = LangManager.getLanguage(uuid);
        String title;

        if (filterSender != null) {
            String senderName = manager.getGlobalName(filterSender);
            title = LangManager.get(lang, "gui.mail.select_title_filtered")
                    .replace("%sender%", senderName)
                    .replace("%page%", String.valueOf(safePage + 1));
        } else {
            title = LangManager.get(lang, "gui.mail.select_title")
                    .replace("%page%", String.valueOf(safePage + 1))
                    .replace("%maxpage%", String.valueOf(totalPages));
        }

        if (title.length() > 32) title = title.substring(0, 32);

        Inventory inv = Bukkit.createInventory(this, 54, title);

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mails.size());

        for (int i = start; i < end; i++) {
            Mail mail = mails.get(i);
            ItemStack item = mail.toItemStack(player);
            if (item == null) continue;

            // 선택된 상태면 인챈트 효과 추가
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

        // --- 버튼 배치 ---
        ItemStack searchIcon = ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH);
        if (searchIcon.getType() == Material.AIR) searchIcon = new ItemStack(Material.COMPASS);

        inv.setItem(SLOT_SEARCH, new ItemBuilder(searchIcon.clone())
                .name(LangManager.get(lang, "gui.search.name"))
                .lore(LangManager.getList(lang, "gui.mail.search_lore"))
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
        // [중요] 싱글톤 인스턴스인지 확인 (MailManager.getInstance().mailSelectGUI)
        if (!(e.getInventory().getHolder() instanceof MailSelectGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

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
                String lang = LangManager.getLanguage(uuid);
                player.sendMessage(LangManager.get(lang, "gui.mail.search_prompt"));
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            }
            case SLOT_PREV -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage - 1);
            }
            case SLOT_NEXT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage + 1);
            }
            case SLOT_CLAIM_ALL -> {
                // 선택된 메일 확인
                Set<UUID> selectedIds = selectedMails.getOrDefault(uuid, Collections.emptySet());

                if (selectedIds.isEmpty()) {
                    player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                } else {
                    int successCount = 0;
                    // ConcurrentModificationException 방지를 위해 복사본 사용
                    for (UUID mailId : new HashSet<>(selectedIds)) {
                        Mail mail = manager.getMailById(mailId);
                        if (mail == null) continue;

                        List<ItemStack> remain = claimItems(player, mail.getItems());

                        if (remain.isEmpty()) {
                            manager.removeMail(mail);
                        } else {
                            mail.setItems(remain);
                            manager.updateMail(mail);
                        }
                        successCount++;
                    }
                    manager.flushNow();

                    // 수령 완료 후 선택 목록 초기화
                    selectedMails.get(uuid).clear();

                    player.sendMessage(LangManager.get(uuid, "gui.mail.claim_success_count").replace("%count%", String.valueOf(successCount)));
                    ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
                    open(player, currentPage); // 화면 갱신
                }
            }
            case SLOT_DELETE_ALL -> {
                Set<UUID> selectedIds = selectedMails.getOrDefault(uuid, Collections.emptySet());
                if (selectedIds.isEmpty()) {
                    player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                } else {
                    List<Mail> mailObjs = selectedIds.stream()
                            .map(manager::getMailById)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                    // 삭제 확인창 열기
                    new MailDeleteConfirmGUI(player, plugin, mailObjs).open(player);
                }
            }
            case SLOT_BACK -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                MailManager.getInstance().mailGUI.open(player);
            }
            default -> {
                // 메일 아이템 클릭 (선택 토글)
                if (slot < PAGE_SIZE) {
                    UUID filterSender = senderFilterMap.get(uuid);
                    List<Mail> mails = manager.getMails(uuid).stream()
                            .filter(mail -> !mail.isExpired())
                            .filter(mail -> filterSender == null || mail.getSender().equals(filterSender))
                            .sorted(Comparator.comparingLong(Mail::getCreatedAt).reversed())
                            .collect(Collectors.toList());

                    int mailIndex = currentPage * PAGE_SIZE + slot;
                    if (mailIndex >= mails.size()) return;

                    Mail mail = mails.get(mailIndex);
                    Set<UUID> userSelected = selectedMails.computeIfAbsent(uuid, k -> new HashSet<>());

                    if (userSelected.contains(mail.getMailId())) {
                        userSelected.remove(mail.getMailId());
                        ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK); // 해제 소리
                    } else {
                        userSelected.add(mail.getMailId());
                        ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SELECTION_COMPLETE); // 선택 소리
                    }

                    open(player, currentPage); // 화면 갱신
                }
            }
        }
    }

    private List<ItemStack> claimItems(Player player, List<ItemStack> items) {
        if (items.isEmpty()) return Collections.emptyList();
        List<ItemStack> remainAll = new ArrayList<>();

        // 인벤토리가 꽉 찼는지 확인하며 아이템 지급
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
            // 검색 중이 아니면 선택 정보는 유지하되, 메모리 누수 방지를 위해 너무 오래된 데이터는 정리하는 로직이 있으면 좋음
            // 현재는 검색/다른 GUI 이동 시 유지하고, 서버 나갈 때만 정리됨 (별도 로직 필요 시 추가)
            if (!waitingForSearch.contains(uuid)) {
                // pageMap.remove(uuid); // 페이지는 유지하는 것이 사용자 경험상 좋음
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
        String lang = LangManager.getLanguage(uuid);

        if (input.equalsIgnoreCase("reset") || input.equalsIgnoreCase("초기화") || input.equalsIgnoreCase("cancel")) {
            senderFilterMap.remove(uuid);
            player.sendMessage(LangManager.get(lang, "gui.mail.search_reset"));
            Bukkit.getScheduler().runTask(plugin, () -> open(player, 0));
            return;
        }

        player.sendMessage(LangManager.get(lang, "gui.search.searching").replace("%name%", input));

        // 글로벌 닉네임 검색
        MailDataManager.getInstance().getGlobalUUID(input).thenAccept(targetUUID -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (targetUUID == null) {
                    player.sendMessage(LangManager.get(lang, "cmd.player.notfound").replace("%name%", input));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                    open(player, 0);
                    return;
                }

                senderFilterMap.put(uuid, targetUUID);

                long count = MailDataManager.getInstance().getMails(uuid).stream()
                        .filter(m -> m.getSender().equals(targetUUID) && !m.isExpired())
                        .count();

                player.sendMessage(LangManager.get(lang, "gui.mail.search_result").replace("%count%", String.valueOf(count)));
                ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SELECTION_COMPLETE);

                // 검색 후 첫 페이지부터 표시 (선택 목록은 초기화할지 유지할지 결정 -> 여기선 유지)
                selectedMails.put(uuid, new HashSet<>()); // 검색 시 선택 초기화
                open(player, 0);
            });
        });
    }
}