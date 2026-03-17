package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MailSendGUI implements Listener, InventoryHolder {

    private static final int SLOT_TIME = 10;
    private static final int SLOT_TARGET = 12;
    private static final int SLOT_ITEM = 14;
    private static final int SLOT_CONFIRM = 16;
    private static final int SLOT_BACK = 26;

    private final Plugin plugin;
    // 발송 처리 중임을 나타내는 세트 (복사 방지)
    private final Set<UUID> processingSet = ConcurrentHashMap.newKeySet();
    private final Set<UUID> navigatingSet = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ItemStack> cachedHeads = new ConcurrentHashMap<>();

    public MailSendGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 27, "Mail Send GUI");
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.hasPermission("mail.send")) {
            player.sendMessage(LangManager.get(uuid, "mail.send.no_permission"));
            return;
        }

        String lang = LangManager.getLanguage(uuid);
        Inventory inv = Bukkit.createInventory(this, 27, LangManager.get(uuid, "gui.send.title"));

        // 시간 정보 설정
        Map<String, Integer> timeData = MailService.getTimeData(uuid);
        String formatted = TimeUtil.format(timeData, lang);
        long expireAt = MailService.getExpireTime(uuid);

        List<String> timeLore = new ArrayList<>();
        timeLore.add(LangManager.get(uuid, "gui.send.time.duration").replace("%time%", formatted));
        if (expireAt > 0 && expireAt < Long.MAX_VALUE) {
            timeLore.add(LangManager.get(uuid, "gui.send.time.expires").replace("%date%", TimeUtil.formatDateTime(expireAt, lang)));
        } else {
            timeLore.add(LangManager.get(uuid, "gui.send.time.no_expire"));
        }

        inv.setItem(SLOT_TIME, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_GUI_TIME))
                .name(LangManager.get(uuid, "gui.send.time.name")).lore(timeLore).build());

        // 대상 설정
        OfflinePlayer target = MailService.getTargetPlayer(uuid);
        if (target != null && target.getName() != null) {
            inv.setItem(SLOT_TARGET, getCachedHead(target));
        } else {
            inv.setItem(SLOT_TARGET, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_GUI_TARGET))
                    .name(LangManager.get(uuid, "gui.send.target.name")).lore(LangManager.getList(uuid, "gui.send.target.lore")).build());
        }

        // 아이템 아이콘 갱신
        updateItemSlot(player, inv);

        inv.setItem(SLOT_CONFIRM, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_GUI_CONFIRM))
                .name(LangManager.get(uuid, "gui.send.confirm.name")).lore(LangManager.getList(uuid, "gui.send.confirm.lore")).build());

        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(uuid, "gui.back.name")).build());

        player.openInventory(inv);
    }

    private void updateItemSlot(Player player, Inventory inv) {
        UUID uuid = player.getUniqueId();
        List<ItemStack> items = MailService.getAttachedItems(uuid);
        if (items.isEmpty()) {
            inv.setItem(SLOT_ITEM, new ItemBuilder(Material.BARRIER).name(LangManager.get(uuid, "gui.send.no_item")).build());
        } else {
            ItemStack first = items.get(0);
            List<String> lore = new ArrayList<>();
            lore.add(LangManager.get(uuid, "gui.send.item.first"));
            if (items.size() > 1) {
                lore.add(LangManager.get(uuid, "gui.send.item.more").replace("%count%", String.valueOf(items.size() - 1)));
            }
            inv.setItem(SLOT_ITEM, new ItemBuilder(first.clone()).lore(lore).build());
        }
    }

    private ItemStack getCachedHead(OfflinePlayer target) {
        return cachedHeads.computeIfAbsent(target.getUniqueId(), id -> {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§f" + target.getName());
                meta.setOwningPlayer(target);
                skull.setItemMeta(meta);
            }
            return skull;
        }).clone();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();

        if (slot == SLOT_BACK) {
            navigatingSet.add(uuid);
            MailManager.getInstance().mailGUI.open(player);
            return;
        }

        switch (slot) {
            case SLOT_TIME -> {
                navigatingSet.add(uuid);
                MailManager.getInstance().mailTimeSelectGUI.open(player, MailSendGUI.class);
            }
            case SLOT_TARGET -> {
                navigatingSet.add(uuid);
                MailManager.getInstance().mailTargetSelectGUI.open(player);
            }
            case SLOT_ITEM -> {
                navigatingSet.add(uuid);
                MailManager.getInstance().mailAttachGUI.open(player, MailSendGUI.class);
            }
            case SLOT_CONFIRM -> {
                handleConfirm(player, e.getInventory());
            }
        }
    }

    private void handleConfirm(Player player, Inventory inv) {
        UUID uuid = player.getUniqueId();

        // 중복 클릭 방지
        if (processingSet.contains(uuid)) return;

        List<ItemStack> items = MailService.getAttachedItems(uuid);
        if (items.isEmpty() || MailService.getTargetPlayer(uuid) == null) {
            player.sendMessage(LangManager.get(uuid, "mail.send.invalid"));
            return;
        }

        // 전송 처리 시작
        processingSet.add(uuid);

        // 시각적으로 슬롯 비우기 (복사 심리 방지)
        inv.setItem(SLOT_ITEM, null);

        // 서비스 레이어 호출
        MailService.send(player, plugin);

        // 전송 완료 후 데이터 클리어
        MailService.clearAttached(uuid);
        ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_SEND_SUCCESS);

        // 전송 성공 시 navigatingSet에 넣어 onClose에서의 아이템 반환을 막음
        navigatingSet.add(uuid);
        player.closeInventory();

        // 전송 프로세스 종료
        processingSet.remove(uuid);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MailSendGUI) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendGUI) || !(e.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        // 이동 중이거나 발송 성공 상태라면 아이템을 돌려주지 않음
        if (navigatingSet.remove(uuid)) return;

        // 발송 처리 중(비동기 대기 등)인 경우에도 돌려주지 않음 (MailService가 처리할 영역)
        if (processingSet.contains(uuid)) return;

        // 실제 아이템 반환 (증발 방지 로직)
        List<ItemStack> attached = MailService.getAttachedItems(uuid);
        if (attached != null && !attached.isEmpty()) {
            for (ItemStack item : attached) {
                if (item == null || item.getType().isAir()) continue;

                // 인벤토리에 넣고 남은 아이템은 바닥에 드랍 (증발 차단)
                Map<Integer, ItemStack> left = player.getInventory().addItem(item);
                if (!left.isEmpty()) {
                    for (ItemStack leftItem : left.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftItem);
                    }
                }
            }
            MailService.clearAttached(uuid);
            player.sendMessage(LangManager.get(uuid, "mail.receive.failed_returned")); // 언어 파일에 반환 메시지 추가 권장
        }
    }
}