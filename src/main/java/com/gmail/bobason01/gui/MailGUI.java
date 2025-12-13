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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MailGUI implements Listener, InventoryHolder {

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();

    // 8번 슬롯(설정)을 제외한 페이지 당 메일 개수 (총 44개)
    private static final int PAGE_SIZE = 44;

    // 버튼 슬롯 위치 설정
    private static final int SETTING_BTN_SLOT = 8;  // [요청하신 위치] 우측 상단

    private static final int PREV_BTN_SLOT = 45;    // 좌측 하단
    private static final int SEND_BTN_SLOT = 49;    // 중앙 하단 (메일 보내기)
    private static final int NEXT_BTN_SLOT = 53;    // 우측 하단

    public MailGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54);
    }

    public void open(Player player) {
        int lastPage = pageMap.getOrDefault(player.getUniqueId(), 0);
        open(player, lastPage);
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();

        // 최신 데이터 로드
        MailDataManager manager = MailDataManager.getInstance();
        manager.flushNow();
        manager.forceReloadMails(uuid);

        List<Mail> validMails = getValidMails(uuid);
        int size = validMails.size();
        int totalPages = size == 0 ? 1 : ((size - 1) / PAGE_SIZE + 1);
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

        pageMap.put(uuid, clampedPage);

        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(lang, "gui.mail.title") + " (" + (clampedPage + 1) + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(this, 54, title);

        int start = clampedPage * PAGE_SIZE;

        // 메일 아이템 배치 (0~44개를 배치하되, 8번 슬롯은 건너뜀)
        for (int i = 0; i < PAGE_SIZE; i++) {
            int mailIndex = start + i;
            if (mailIndex >= size) break;

            // [중요] 8번 슬롯을 피하기 위한 인덱스 조정
            // i가 0~7이면 그대로 slot 0~7
            // i가 8 이상이면 slot 9부터 시작 (8번 건너뜀)
            int slot = (i < 8) ? i : (i + 1);

            Mail mail = validMails.get(mailIndex);
            ItemStack item = mail.toItemStack(player);
            if (item == null) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(" ");
                lore.addAll(LangManager.getList(uuid, "gui.mail.lore.claim"));
                lore.addAll(LangManager.getList(uuid, "gui.mail.lore.delete"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        }

        // --- 버튼 배치 ---

        // 1. 설정 버튼 (8번 슬롯)
        inv.setItem(SETTING_BTN_SLOT, createButton(
                ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SETTING_BUTTON),
                LangManager.get(uuid, "gui.setting.title")));

        // 2. 이전 페이지
        if (clampedPage > 0) {
            inv.setItem(PREV_BTN_SLOT, createButton(
                    ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON),
                    LangManager.get(uuid, "gui.previous")));
        }

        // 3. 다음 페이지
        if (clampedPage < totalPages - 1) {
            inv.setItem(NEXT_BTN_SLOT, createButton(
                    ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON),
                    LangManager.get(uuid, "gui.next")));
        }

        // 4. 메일 보내기 (권한 있을 때만)
        if (player.hasPermission("mail.send")) {
            inv.setItem(SEND_BTN_SLOT, createButton(
                    ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SEND_BUTTON),
                    LangManager.get(uuid, "gui.send.title")));
        }

        player.openInventory(inv);
    }

    private List<Mail> getValidMails(UUID uuid) {
        MailDataManager manager = MailDataManager.getInstance();
        List<Mail> mails = manager.getMails(uuid);
        if (mails.isEmpty()) return Collections.emptyList();

        List<Mail> result = new ArrayList<>();
        // 정렬: 최신순 (내림차순)으로 미리 정렬하여 인덱스 계산을 단순화
        List<Mail> sortedMails = new ArrayList<>(mails);
        sortedMails.sort(Comparator.comparingLong(Mail::getCreatedAt).reversed());

        for (Mail m : sortedMails) {
            if (m != null && !m.isExpired()) {
                result.add(m);
            } else if (m != null) {
                manager.removeMail(m); // 만료된 메일 삭제
            }
        }
        return result;
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
        if (!(e.getInventory().getHolder() instanceof MailGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();
        int currentPage = pageMap.getOrDefault(uuid, 0);
        MailManager manager = MailManager.getInstance();

        switch (slot) {
            case SETTING_BTN_SLOT -> { // 8번 슬롯 (설정)
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailSettingGUI.open(player);
            }
            case SEND_BTN_SLOT -> { // 49번 슬롯 (보내기)
                if (!player.hasPermission("mail.send")) {
                    player.sendMessage(LangManager.get(uuid, "mail.send.no_permission"));
                    return;
                }
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                manager.mailSendGUI.open(player);
            }
            case PREV_BTN_SLOT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage - 1);
            }
            case NEXT_BTN_SLOT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage + 1);
            }
            default -> {
                // 메일 아이템 클릭 처리 (하단 메뉴바 슬롯 제외)
                if (slot < 45 && slot != SETTING_BTN_SLOT) {
                    List<Mail> validMails = getValidMails(uuid);

                    // 슬롯 번호를 메일 리스트 인덱스로 변환 (8번 슬롯 건너뛰기 고려)
                    int listIndexOffset = (slot < 8) ? slot : (slot - 1);
                    int mailIndex = currentPage * PAGE_SIZE + listIndexOffset;

                    if (mailIndex >= 0 && mailIndex < validMails.size()) {
                        Mail mail = validMails.get(mailIndex);

                        if (e.getClick() == ClickType.RIGHT) {
                            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                            new MailDeleteConfirmGUI(player, plugin, Collections.singletonList(mail)).open(player);
                        } else {
                            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                            // 싱글톤 인스턴스 사용
                            MailManager.getInstance().mailViewGUI.open(player, mail);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MailGUI) {
            pageMap.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MailGUI) {
            e.setCancelled(true);
        }
    }
}