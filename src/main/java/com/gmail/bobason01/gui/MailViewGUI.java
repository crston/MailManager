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
import org.bukkit.event.inventory.InventoryCloseEvent;
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
    // 시청 중인 메일 정보 동시성 문제 방지용
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

    // 인벤토리를 닫을 때 맵에서 제거하여 메모리 누수 방지
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MailViewGUI) {
            viewingMail.remove(e.getPlayer().getUniqueId());
        }
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
            // 정보가 없으면 목록으로 돌아감
            MailManager.getInstance().mailGUI.open(player);
            return;
        }

        MailDataManager manager = MailDataManager.getInstance();
        Mail mail = manager.getMailById(mailId);

        if (mail == null || mail.isExpired()) {
            player.sendMessage(LangManager.get(player.getUniqueId(), "mail.expired"));
            MailManager.getInstance().mailGUI.open(player);
            return;
        }

        UUID uuid = player.getUniqueId();

        // 첫번째 아이템 개별 수령
        if (slot < 45) {
            List<ItemStack> currentItems = new ArrayList<>(mail.getItems());

            if (slot >= currentItems.size()) return;

            ItemStack clickedItem = currentItems.get(slot);
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            int originalAmount = clickedItem.getAmount();
            Map<Integer, ItemStack> left = player.getInventory().addItem(clickedItem.clone());

            if (left.isEmpty()) {
                // 전부 수령 성공
                currentItems.remove(slot);
            } else {
                ItemStack remainingItem = left.get(0);
                if (remainingItem.getAmount() == originalAmount) {
                    // 인벤토리가 꽉 차서 하나도 받지 못함
                    player.sendMessage(LangManager.get(uuid, "mail.receive.failed"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_FAIL);
                    return;
                } else {
                    // 공간이 부족하여 일부만 수령함 남은 개수 업데이트
                    clickedItem.setAmount(remainingItem.getAmount());
                }
            }

            mail.setItems(currentItems);
            manager.updateMail(mail);
            manager.flushNow();

            player.updateInventory();

            if (currentItems.isEmpty()) {
                manager.removeMail(mail);
                player.sendMessage(LangManager.get(uuid, "mail.claim_success"));
                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
                MailManager.getInstance().mailGUI.open(player);
            } else {
                // 아이템을 성공적으로 받았거나 일부만 받았을 때 성공 소리 재생 후 새로고침
                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_CLAIM_SUCCESS);
                open(player, mail);
            }
            return;
        }

        // 두번째 전체 수령
        if (slot == SLOT_CLAIM_ALL) {
            List<ItemStack> items = mail.getItems();
            List<ItemStack> remaining = new ArrayList<>();
            boolean claimedAny = false;

            for (ItemStack item : items) {
                if (item == null || item.getType() == Material.AIR) continue;

                int originalAmount = item.getAmount();
                Map<Integer, ItemStack> left = player.getInventory().addItem(item.clone());

                if (left.isEmpty()) {
                    claimedAny = true;
                } else {
                    ItemStack leftItem = left.get(0);
                    if (leftItem.getAmount() < originalAmount) {
                        // 일부라도 들어갔다면 수령한 것으로 판정
                        claimedAny = true;
                    }
                    remaining.add(leftItem);
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
        // 세번째 삭제
        else if (slot == SLOT_DELETE) {
            new MailDeleteConfirmGUI(plugin).open(player, Collections.singletonList(mail));
        }
        // 네번째 뒤로가기
        else if (slot == SLOT_BACK) {
            MailManager.getInstance().mailGUI.open(player);
        }
    }
}