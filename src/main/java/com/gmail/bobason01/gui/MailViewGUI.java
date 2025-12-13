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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MailViewGUI implements Listener, InventoryHolder {

    private static final int SLOT_CLAIM_ALL = 49;
    private static final int SLOT_DELETE = 50;
    private static final int SLOT_BACK = 53;

    private final Plugin plugin;
    // 시청 중인 메일 정보 (동시성 문제 방지용 ConcurrentHashMap)
    private final Map<UUID, UUID> viewingMail = new ConcurrentHashMap<>();

    public MailViewGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54);
    }

    public void open(Player player, Mail mail) {
        if (mail == null) return;
        viewingMail.put(player.getUniqueId(), mail.getMailId());

        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(lang, "gui.mail.view.title");
        Inventory inv = Bukkit.createInventory(this, 54, title);

        List<ItemStack> items = mail.getItems();

        // 아이템 배치
        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            ItemStack item = items.get(i);
            if (item != null && item.getType() != Material.AIR) {
                inv.setItem(i, item.clone());
            }
        }

        inv.setItem(SLOT_CLAIM_ALL, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_CLAIM_BUTTON))
                .name(LangManager.get(lang, "gui.mail.claim_all"))
                .build());

        inv.setItem(SLOT_DELETE, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.MAIL_GUI_DELETE_BUTTON))
                .name(LangManager.get(lang, "gui.mail.delete"))
                .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(lang, "gui.back.name"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailViewGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0) return;

        // 보고 있는 메일 정보 확인
        UUID mailId = viewingMail.get(player.getUniqueId());
        if (mailId == null) {
            // 정보가 없으면 목록으로 돌아감 (오류 방지)
            MailManager.getInstance().mailGUI.open(player);
            return;
        }

        MailDataManager manager = MailDataManager.getInstance();
        Mail mail = manager.getMailById(mailId);

        if (mail == null || mail.isExpired()) {
            player.sendMessage(LangManager.get(player.getUniqueId(), "mail.expired")); // 없는 키면 기본값 출력됨
            MailManager.getInstance().mailGUI.open(player);
            return;
        }

        UUID uuid = player.getUniqueId();

        // 1. 아이템 개별 수령
        if (slot < 45) {
            // [중요] 원본 리스트 복사본 생성 (ConcurrentModificationException 방지)
            List<ItemStack> currentItems = new ArrayList<>(mail.getItems());

            if (slot >= currentItems.size()) return;

            ItemStack clickedItem = currentItems.get(slot);
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            Map<Integer, ItemStack> left = player.getInventory().addItem(clickedItem.clone());

            if (left.isEmpty()) {
                // 성공적으로 수령 -> 리스트에서 제거
                currentItems.remove(slot);
                mail.setItems(currentItems);
                manager.updateMail(mail);
                manager.flushNow();

                player.updateInventory(); // 인벤토리 동기화
                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);

                if (currentItems.isEmpty()) {
                    manager.removeMail(mail);
                    player.sendMessage(LangManager.get(uuid, "mail.claim_success"));
                    MailManager.getInstance().mailGUI.open(player);
                } else {
                    // GUI 새로고침 (당겨진 아이템 반영)
                    open(player, mail);
                }
            } else {
                player.sendMessage(LangManager.get(uuid, "mail.receive.failed"));
                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_FAIL);
            }
            return;
        }

        // 2. 전체 수령
        if (slot == SLOT_CLAIM_ALL) {
            List<ItemStack> items = new ArrayList<>(mail.getItems());
            List<ItemStack> remaining = new ArrayList<>();
            boolean claimedAny = false;

            for (ItemStack item : items) {
                if (item == null) continue;
                Map<Integer, ItemStack> left = player.getInventory().addItem(item.clone());
                if (left.isEmpty()) {
                    claimedAny = true;
                } else {
                    remaining.addAll(left.values());
                }
            }

            player.updateInventory();

            if (remaining.isEmpty()) {
                manager.removeMail(mail);
                manager.flushNow();
                player.sendMessage(LangManager.get(uuid, "mail.claim_success"));
                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
                MailManager.getInstance().mailGUI.open(player);
            } else {
                mail.setItems(remaining);
                manager.updateMail(mail);
                manager.flushNow();

                if (claimedAny) {
                    player.sendMessage(LangManager.get(uuid, "mail.inventory_full"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
                } else {
                    player.sendMessage(LangManager.get(uuid, "mail.receive.failed"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_FAIL);
                }
                open(player, mail);
            }
        }
        // 3. 삭제
        else if (slot == SLOT_DELETE) {
            new MailDeleteConfirmGUI(player, plugin, Collections.singletonList(mail)).open(player);
        }
        // 4. 뒤로가기
        else if (slot == SLOT_BACK) {
            MailManager.getInstance().mailGUI.open(player);
        }
    }
}