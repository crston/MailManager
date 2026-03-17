package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
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

    // 8번 슬롯(설정)을 제외한 메일 표시 가능 슬롯들 (총 44개)
    private static final List<Integer> MAIL_SLOTS = new ArrayList<>();
    static {
        for (int i = 0; i < 45; i++) {
            if (i != 8) MAIL_SLOTS.add(i);
        }
    }

    private static final int PAGE_SIZE = MAIL_SLOTS.size();
    private static final int SETTING_BTN_SLOT = 8;
    private static final int PREV_BTN_SLOT = 45;
    private static final int SEND_BTN_SLOT = 49;
    private static final int NEXT_BTN_SLOT = 53;

    public MailGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54, "Mail Box");
    }

    public void open(Player player) {
        open(player, pageMap.getOrDefault(player.getUniqueId(), 0));
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        MailDataManager manager = MailDataManager.getInstance();

        // [최적화] 강제 리로드 제거. 메모리에 이미 최신 데이터가 동기화되어 있다고 가정함.
        List<Mail> validMails = getValidMails(uuid);

        int totalPages = Math.max(1, (int) Math.ceil((double) validMails.size() / PAGE_SIZE));
        int safePage = Math.min(Math.max(page, 0), totalPages - 1);
        pageMap.put(uuid, safePage);

        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(lang, "gui.mail.title") + " (" + (safePage + 1) + "/" + totalPages + ")";
        Inventory inv = Bukkit.createInventory(this, 54, title);

        int start = safePage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int mailIndex = start + i;
            if (mailIndex >= validMails.size()) break;

            int slot = MAIL_SLOTS.get(i);
            Mail mail = validMails.get(mailIndex);
            ItemStack item = mail.toItemStack(player);
            if (item == null) continue;

            // 로어 추가 (수령/삭제 안내)
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(" ");
                lore.addAll(LangManager.getList(lang, "gui.mail.lore.claim"));
                lore.addAll(LangManager.getList(lang, "gui.mail.lore.delete"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        }

        // 하단 제어 버튼
        inv.setItem(SETTING_BTN_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SETTING_BUTTON))
                .name(LangManager.get(lang, "gui.setting.title")).build());

        if (safePage > 0) inv.setItem(PREV_BTN_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON))
                .name(LangManager.get(lang, "gui.previous")).build());

        if (safePage < totalPages - 1) inv.setItem(NEXT_BTN_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON))
                .name(LangManager.get(lang, "gui.next")).build());

        if (player.hasPermission("mail.send")) inv.setItem(SEND_BTN_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_SEND_BUTTON))
                .name(LangManager.get(lang, "gui.send.title")).build());

        player.openInventory(inv);
    }

    private List<Mail> getValidMails(UUID uuid) {
        List<Mail> mails = new ArrayList<>(MailDataManager.getInstance().getMails(uuid));
        mails.removeIf(m -> m == null || m.isExpired());
        mails.sort(Comparator.comparingLong(Mail::getCreatedAt).reversed());
        return mails;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0) return;

        UUID uuid = player.getUniqueId();
        int currentPage = pageMap.getOrDefault(uuid, 0);

        if (slot == SETTING_BTN_SLOT) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            MailManager.getInstance().mailSettingGUI.open(player);
        } else if (slot == SEND_BTN_SLOT) {
            if (player.hasPermission("mail.send")) {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                MailManager.getInstance().mailSendGUI.open(player);
            }
        } else if (slot == PREV_BTN_SLOT) {
            if (currentPage > 0) {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage - 1);
            }
        } else if (slot == NEXT_BTN_SLOT) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
            open(player, currentPage + 1);
        } else if (MAIL_SLOTS.contains(slot)) {
            // [개선] 슬롯 리스트를 이용해 정확한 인덱스 추출
            int listIndex = MAIL_SLOTS.indexOf(slot);
            List<Mail> validMails = getValidMails(uuid);
            int mailIndex = currentPage * PAGE_SIZE + listIndex;

            if (mailIndex >= 0 && mailIndex < validMails.size()) {
                Mail mail = validMails.get(mailIndex);
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);

                if (e.getClick() == ClickType.RIGHT) {
                    MailManager.getInstance().mailDeleteConfirmGUI.open(player, Collections.singletonList(mail));
                } else {
                    MailManager.getInstance().mailViewGUI.open(player, mail);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MailGUI) {
            // 페이지 정보를 삭제하면 뒤로가기 시 불편하므로, 실제 필요시에만 삭제하도록 유지하거나 맵을 관리함.
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MailGUI) e.setCancelled(true);
    }
}