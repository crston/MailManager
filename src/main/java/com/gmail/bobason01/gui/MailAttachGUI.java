package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MailAttachGUI implements Listener {

    private static final int SIZE = 54;      // 6줄
    private static final int BACK_SLOT = 53; // 뒤로가기 버튼

    private final Plugin plugin;

    public MailAttachGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    // 상태를 저장하기 위한 커스텀 Holder 클래스
    private static class AttachHolder implements InventoryHolder {
        private final Inventory inv;
        private final UUID owner;
        private final Class<?> returnGuiClass;

        public AttachHolder(UUID owner, Class<?> returnGuiClass) {
            this.owner = owner;
            this.returnGuiClass = returnGuiClass;
            this.inv = Bukkit.createInventory(this, SIZE, LangManager.get(owner, "gui.attach.title"));
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }

        public UUID getOwner() {
            return owner;
        }

        public Class<?> getReturnGuiClass() {
            return returnGuiClass;
        }
    }

    public void open(Player player, Class<?> returnGuiClass) {
        UUID uuid = player.getUniqueId();

        // Holder 생성 시점에 주인과 복귀할 GUI 정보를 담습니다.
        AttachHolder holder = new AttachHolder(uuid, returnGuiClass);
        Inventory inv = holder.getInventory();

        // 기존 첨부 아이템 불러오기
        List<ItemStack> items = MailService.getAttachedItems(uuid);
        for (int i = 0; i < items.size() && i < SIZE - 1; i++) {
            ItemStack item = items.get(i);
            if (item != null && item.getType() != Material.AIR) {
                inv.setItem(i, item.clone());
            }
        }

        // 뒤로가기 버튼
        ItemStack backButton = ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON);
        if (backButton == null) backButton = new ItemStack(Material.BARRIER);

        inv.setItem(BACK_SLOT, new ItemBuilder(backButton.clone())
                .name(LangManager.get(uuid, "gui.back.name"))
                .lore(LangManager.getList(uuid, "gui.back.lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // 커스텀 Holder인지 확인하여 우리 플러그인의 인벤토리인지 식별
        if (!(e.getInventory().getHolder() instanceof AttachHolder holder)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        int rawSlot = e.getRawSlot();

        // 뒤로가기 버튼 클릭
        if (rawSlot == BACK_SLOT) {
            e.setCancelled(true);

            // 저장 후 이동
            saveAttachedItems(holder);
            returnToParent(player, holder.getReturnGuiClass());

            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof AttachHolder)) return;

        // 뒤로가기 버튼 위치에 아이템 드래그 방지
        if (e.getRawSlots().contains(BACK_SLOT)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof AttachHolder holder) {
            // 창을 닫을 때(ESC 등 포함) 아이템 저장
            saveAttachedItems(holder);
        }
    }

    private void saveAttachedItems(AttachHolder holder) {
        Inventory inv = holder.getInventory();
        List<ItemStack> items = new ArrayList<>();

        // 마지막 슬롯(뒤로가기 버튼) 제외하고 순회
        for (int i = 0; i < SIZE - 1; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }

        // 서비스에 저장 (메모리 상의 임시 저장소)
        MailService.setAttachedItems(holder.getOwner(), items);
    }

    private void returnToParent(Player player, Class<?> returnClass) {
        MailManager manager = MailManager.getInstance();

        // 저장된 데이터를 바탕으로 부모 GUI 열기
        if (returnClass == MailSendGUI.class) {
            manager.mailSendGUI.open(player);
        } else if (returnClass == MailSendAllGUI.class) {
            manager.mailSendAllGUI.open(player);
        }
    }
}