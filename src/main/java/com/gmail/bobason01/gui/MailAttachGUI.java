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
import java.util.concurrent.ConcurrentHashMap;

public class MailAttachGUI implements Listener {

    private static final int SIZE = 54;
    private static final int BACK_SLOT = 53;
    private final Plugin plugin;

    // 저장 프로세스 중복 방지 태그
    private final List<UUID> savingProcess = new ArrayList<>();

    public MailAttachGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    private static class AttachHolder implements InventoryHolder {
        private final Inventory inv;
        private final UUID owner;
        private final Class<?> returnGuiClass;

        public AttachHolder(UUID owner, Class<?> returnGuiClass, String title) {
            this.owner = owner;
            this.returnGuiClass = returnGuiClass;
            this.inv = Bukkit.createInventory(this, SIZE, title);
        }

        @Override
        public @NotNull Inventory getInventory() { return inv; }
        public UUID getOwner() { return owner; }
        public Class<?> getReturnGuiClass() { return returnGuiClass; }
    }

    public void open(Player player, Class<?> returnGuiClass) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        AttachHolder holder = new AttachHolder(uuid, returnGuiClass, LangManager.get(lang, "gui.attach.title"));
        Inventory inv = holder.getInventory();

        // 기존 첨부 아이템 로드
        List<ItemStack> items = MailService.getAttachedItems(uuid);
        for (int i = 0; i < Math.min(items.size(), SIZE - 1); i++) {
            ItemStack item = items.get(i);
            if (item != null && item.getType() != Material.AIR) {
                inv.setItem(i, item.clone());
            }
        }

        // 뒤로가기 버튼
        inv.setItem(BACK_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(lang, "gui.back.name"))
                .lore(LangManager.getList(lang, "gui.back.lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AttachHolder holder)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        int slot = e.getRawSlot();

        // 뒤로가기 버튼 클릭 시
        if (slot == BACK_SLOT) {
            e.setCancelled(true);
            saveAttachedItems(holder);
            returnToParent(player, holder.getReturnGuiClass());
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            return;
        }

        // 상단 인벤토리 영역(첨부 영역)과 하단 인벤토리 사이의 아이템 이동은 허용하되,
        // BACK_SLOT에 아이템을 덮어씌우는 행위는 차단
        if (slot < SIZE && slot != BACK_SLOT) {
            // 여기에 추가적인 아이템 필터링(금지 아이템 등) 로직을 넣을 수 있습니다.
        } else if (slot == BACK_SLOT || (e.isShiftClick() && slot >= SIZE)) {
            // 쉬프트 클릭으로 BACK_SLOT에 아이템이 들어가는 것을 방지하기 위해
            // 인벤토리 전체 로직에서 체크가 필요할 수 있으나, 여기서는 기본적인 슬롯 보호만 수행
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof AttachHolder) {
            if (e.getRawSlots().contains(BACK_SLOT)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof AttachHolder holder) {
            saveAttachedItems(holder);
        }
    }

    private synchronized void saveAttachedItems(AttachHolder holder) {
        UUID uuid = holder.getOwner();

        // 이미 저장 프로세스가 진행 중이면 중복 실행 방지
        if (savingProcess.contains(uuid)) return;
        savingProcess.add(uuid);

        try {
            Inventory inv = holder.getInventory();
            List<ItemStack> items = new ArrayList<>();
            // BACK_SLOT을 제외한 모든 아이템 저장
            for (int i = 0; i < SIZE - 1; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    items.add(item.clone());
                }
            }
            MailService.setAttachedItems(uuid, items);
        } finally {
            // 저장 완료 후 플래그 제거 (잠시 후 제거하여 안전성 확보)
            Bukkit.getScheduler().runTaskLater(plugin, () -> savingProcess.remove(uuid), 1L);
        }
    }

    private void returnToParent(Player player, Class<?> returnClass) {
        // MailManager를 통해 안전하게 상위 GUI로 복귀
        if (returnClass == MailSendGUI.class) {
            MailManager.getInstance().mailSendGUI.open(player);
        } else if (returnClass == MailSendAllGUI.class) {
            MailManager.getInstance().mailSendAllGUI.open(player);
        } else {
            MailManager.getInstance().mailGUI.open(player);
        }
    }
}