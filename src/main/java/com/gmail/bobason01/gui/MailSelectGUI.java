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
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> selectedMails = new ConcurrentHashMap<>();

    // 채팅 검색을 대기 중인 플레이어 목록
    private final Set<UUID> waitingForSearch = ConcurrentHashMap.newKeySet();

    // 현재 필터링 중인 발신자 UUID (null이면 전체 보기)
    private final Map<UUID, UUID> senderFilterMap = new ConcurrentHashMap<>();

    public MailSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54);
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        UUID filterSender = senderFilterMap.get(uuid); // 현재 필터링 중인 발신자

        MailDataManager manager = MailDataManager.getInstance();

        // 데이터 최신화 (멀티 서버 싱크)
        manager.flushNow();
        manager.forceReloadMails(uuid);

        // 내 메일 목록 가져오기 및 필터링
        List<Mail> mails = manager.getMails(uuid)
                .stream()
                .filter(mail -> !mail.isExpired()) // 만료된 메일 제외
                .filter(mail -> filterSender == null || mail.getSender().equals(filterSender)) // 발신자 필터 적용
                .sorted(Comparator.comparingLong(Mail::getCreatedAt).reversed()) // 최신순 정렬
                .collect(Collectors.toList());

        int totalPages = Math.max(1, (int) Math.ceil((double) mails.size() / PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        pageMap.put(uuid, safePage);

        selectedMails.putIfAbsent(uuid, new HashSet<>());

        String lang = LangManager.getLanguage(uuid);
        String title;

        // 타이틀 설정 (필터링 여부에 따라 다르게 표시)
        if (filterSender != null) {
            String senderName = manager.getGlobalName(filterSender);
            title = LangManager.get(lang, "gui.mail.select_title_filtered") // lang 파일에 추가 필요 (예: "&8선택 모드 (보낸이: %sender%)")
                    .replace("%sender%", senderName)
                    .replace("%page%", String.valueOf(safePage + 1));
        } else {
            title = LangManager.get(lang, "gui.mail.select_title")
                    .replace("%page%", String.valueOf(safePage + 1))
                    .replace("%maxpage%", String.valueOf(totalPages));
        }

        // 타이틀 길이 제한 안전 장치
        if (title.length() > 32) title = title.substring(0, 32);

        Inventory inv = Bukkit.createInventory(this, 54, title);

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mails.size());

        for (int i = start; i < end; i++) {
            Mail mail = mails.get(i);
            ItemStack item = mail.toItemStack(player);
            if (item == null) continue;

            // 선택된 메일이면 인챈트 효과(반짝임) 추가
            if (selectedMails.get(uuid).contains(mail.getMailId())) {
                item.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add(LangManager.get(lang, "gui.mail.selected")); // (예: "&a✔ 선택됨")
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }
            inv.setItem(i - start, item);
        }

        // 검색 버튼
        ItemStack searchIcon = ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH);
        if (searchIcon.getType() == Material.AIR) searchIcon = new ItemStack(Material.COMPASS);

        inv.setItem(SLOT_SEARCH, new ItemBuilder(searchIcon.clone())
                .name(LangManager.get(lang, "gui.search.name")) // (예: "&e보낸 사람 검색")
                .lore(LangManager.getList(lang, "gui.mail.search_lore")) // (예: "&7특정 플레이어가 보낸 메일만 봅니다.")
                .build());

        // 페이지 이동 버튼
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

        // 기능 버튼들
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
                String lang = LangManager.getLanguage(uuid);
                player.sendMessage(LangManager.get(lang, "gui.mail.search_prompt")); // (예: "&a검색할 발신자 이름을 입력하세요. (초기화하려면 'reset')")
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
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                Set<UUID> mails = selectedMails.getOrDefault(uuid, Collections.emptySet());
                if (mails.isEmpty()) {
                    player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                } else {
                    // 선택된 메일 수령 처리
                    int successCount = 0;
                    for (UUID mailId : new HashSet<>(mails)) {
                        Mail mail = manager.getMailById(mailId);
                        if (mail == null) continue;

                        // 인벤토리 공간 확인 및 지급
                        List<ItemStack> remain = claimItems(player, mail.getItems());

                        if (remain.isEmpty()) {
                            // 모두 받음 -> 메일 삭제
                            manager.removeMail(mail);
                        } else {
                            // 일부만 받음 -> 남은 아이템 업데이트
                            mail.setItems(remain);
                            manager.updateMail(mail);
                        }
                        successCount++;
                    }
                    manager.flushNow();
                    selectedMails.get(uuid).clear();
                    player.sendMessage(LangManager.get(uuid, "gui.mail.claim_success_count").replace("%count%", String.valueOf(successCount)));
                    ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
                    open(player, currentPage);
                }
            }
            case SLOT_DELETE_ALL -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                Set<UUID> mails = selectedMails.getOrDefault(uuid, Collections.emptySet());
                if (mails.isEmpty()) {
                    player.sendMessage(LangManager.get(uuid, "gui.mail.no_selected"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                } else {
                    List<Mail> mailObjs = mails.stream()
                            .map(manager::getMailById)
                            .filter(Objects::nonNull)
                            .toList();
                    // 삭제 확인 GUI 열기
                    new MailDeleteConfirmGUI(player, plugin, mailObjs).open(player);
                }
            }
            case SLOT_BACK -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                // 뒤로가기 시 필터 초기화 여부는 기획에 따라 결정 (여기서는 유지)
                MailManager.getInstance().mailGUI.open(player);
            }
            default -> {
                // 메일 아이템 클릭 (선택/해제)
                if (slot < PAGE_SIZE) {
                    UUID filterSender = senderFilterMap.get(uuid);
                    List<Mail> mails = manager.getMails(uuid)
                            .stream()
                            .filter(mail -> !mail.isExpired())
                            .filter(mail -> filterSender == null || mail.getSender().equals(filterSender))
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
                    open(player, currentPage); // 화면 갱신
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
            // 검색 중일 때는 상태 유지, 그 외에는 정리
            if (!waitingForSearch.contains(uuid)) {
                pageMap.remove(uuid);
                selectedMails.remove(uuid);
                senderFilterMap.remove(uuid);
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

        // 검색 초기화 명령
        if (input.equalsIgnoreCase("reset") || input.equalsIgnoreCase("초기화") || input.equalsIgnoreCase("cancel")) {
            senderFilterMap.remove(uuid);
            player.sendMessage(LangManager.get(lang, "gui.mail.search_reset"));
            Bukkit.getScheduler().runTask(plugin, () -> open(player, 0));
            return;
        }

        player.sendMessage(LangManager.get(lang, "gui.search.searching").replace("%name%", input));

        // 글로벌 닉네임 검색 (비동기)
        MailDataManager.getInstance().getGlobalUUID(input).thenAccept(targetUUID -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (targetUUID == null) {
                    player.sendMessage(LangManager.get(lang, "cmd.player.notfound").replace("%name%", input));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                    open(player, 0); // 실패 시 원래 화면 복귀
                    return;
                }

                // 필터 적용
                senderFilterMap.put(uuid, targetUUID);

                // 검색 결과가 있는지 미리 확인 (선택 사항)
                long count = MailDataManager.getInstance().getMails(uuid).stream()
                        .filter(m -> m.getSender().equals(targetUUID) && !m.isExpired())
                        .count();

                player.sendMessage(LangManager.get(lang, "gui.mail.search_result").replace("%count%", String.valueOf(count)));
                ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SELECTION_COMPLETE);

                open(player, 0);
            });
        });
    }
}