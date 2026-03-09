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
    private final Set<UUID> sentSet = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> navigatingSet = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ItemStack> cachedHeads = new ConcurrentHashMap<>();
    private Inventory inv;

    public MailSendGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.hasPermission("mail.send")) {
            player.sendMessage(LangManager.get(uuid, "mail.send.no_permission"));
            return;
        }
        String lang = LangManager.getLanguage(uuid);
        inv = Bukkit.createInventory(this, 27, LangManager.get(uuid, "gui.send.title"));

        Map<String, Integer> timeData = MailService.getTimeData(uuid);
        String formatted = TimeUtil.format(timeData, lang);
        long expireAt = MailService.getExpireTime(uuid);
        String formattedExpire = TimeUtil.formatDateTime(expireAt, lang);

        List<String> timeLore = new ArrayList<>();
        timeLore.add(LangManager.get(uuid, "gui.send.time.duration").replace("%time%", formatted));
        if (expireAt > 0 && expireAt < Long.MAX_VALUE) {
            timeLore.add(LangManager.get(uuid, "gui.send.time.expires").replace("%date%", formattedExpire));
        } else {
            timeLore.add(LangManager.get(uuid, "gui.send.time.no_expire"));
        }

        inv.setItem(SLOT_TIME, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_GUI_TIME))
                .name(LangManager.get(uuid, "gui.send.time.name"))
                .lore(timeLore)
                .build());

        OfflinePlayer target = MailService.getTargetPlayer(uuid);
        if (target != null && target.getName() != null) {
            inv.setItem(SLOT_TARGET, getCachedHead(target));
        } else {
            inv.setItem(SLOT_TARGET, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_GUI_TARGET))
                    .name(LangManager.get(uuid, "gui.send.target.name"))
                    .lore(LangManager.getList(uuid, "gui.send.target.lore"))
                    .build());
        }

        refresh(player);

        inv.setItem(SLOT_CONFIRM, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_GUI_CONFIRM))
                .name(LangManager.get(uuid, "gui.send.confirm.name"))
                .lore(LangManager.getList(uuid, "gui.send.confirm.lore"))
                .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(uuid, "gui.back.name"))
                .lore(LangManager.getList(uuid, "gui.back.lore"))
                .build());

        player.openInventory(inv);
    }

    public void refresh(Player player) {
        if (inv == null) return;
        UUID uuid = player.getUniqueId();
        List<ItemStack> items = MailService.getAttachedItems(uuid);
        if (items.isEmpty()) {
            inv.setItem(SLOT_ITEM, null);
            return;
        }
        ItemStack first = items.get(0);
        List<String> lore = new ArrayList<>();
        lore.add(LangManager.get(uuid, "gui.send.item.first"));
        if (items.size() > 1) {
            lore.add(LangManager.get(uuid, "gui.send.item.more").replace("%count%", String.valueOf(items.size() - 1)));
        }
        inv.setItem(SLOT_ITEM, new ItemBuilder(first).lore(lore).build());
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
        if (!(e.getInventory().getHolder() instanceof MailSendGUI gui)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        if (!player.hasPermission("mail.send")) {
            e.setCancelled(true);
            player.sendMessage(LangManager.get(uuid, "mail.send.no_permission"));
            player.closeInventory();
            return;
        }

        // 클릭한 곳이 플레이어의 하단 인벤토리일 경우 쉬프트 클릭 차단
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getBottomInventory())) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
            }
            return;
        }

        // 상단 인벤토리를 클릭한 경우 무조건 취소하여 아이템 이동 방지
        e.setCancelled(true);

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        MailManager manager = MailManager.getInstance();

        if (slot == SLOT_BACK) {
            MailDataManager dataManager = MailDataManager.getInstance();
            dataManager.flushNow();
            dataManager.forceReloadMails(uuid);

            manager.mailGUI.open(player);
            return;
        }

        if (e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.DOUBLE_CLICK || e.getClick() == ClickType.MIDDLE) {
            return;
        }

        switch (slot) {
            case SLOT_TIME -> {
                gui.navigatingSet.add(uuid);
                manager.mailTimeSelectGUI.open(player, MailSendGUI.class);
            }
            case SLOT_TARGET -> {
                gui.navigatingSet.add(uuid);
                manager.mailTargetSelectGUI.open(player);
            }
            case SLOT_ITEM -> {
                gui.navigatingSet.add(uuid);
                manager.mailAttachGUI.open(player, MailSendGUI.class);
            }
            case SLOT_CONFIRM -> {
                if (!gui.sentSet.add(uuid)) {
                    player.sendMessage(LangManager.get(uuid, "mail.send.cooldown"));
                    return;
                }
                List<ItemStack> items = MailService.getAttachedItems(uuid);
                if (items.isEmpty() || items.get(0).getType() == Material.AIR || MailService.getTargetPlayer(uuid) == null) {
                    player.sendMessage(LangManager.get(uuid, "mail.send.invalid"));
                    gui.sentSet.remove(uuid);
                    return;
                }

                // 메일 전송 로직 수행
                MailService.send(player, plugin);
                MailService.clearAttached(uuid);

                // 상단 슬롯을 비워서 인벤토리 닫기 이벤트가 아이템을 감지하지 못하게 함
                e.getInventory().setItem(SLOT_ITEM, null);

                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_SEND_SUCCESS);

                // 변수 제거를 인벤토리 닫기 뒤로 미룸
                // 이렇게 해야 닫을 때 변수 검사가 참이 되어 아이템 반환을 건너뜀
                player.closeInventory();

                gui.sentSet.remove(uuid);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MailSendGUI) {
            for (int slot : e.getRawSlots()) {
                if (slot < e.getInventory().getSize()) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendGUI gui) || !(e.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        // 클릭에서 제거를 나중에 하므로 전송 성공 시 여기서 반환됨
        if (gui.sentSet.contains(uuid)) return;

        // 다른 메뉴로 이동 중이면 아이템 반환 생략
        if (gui.navigatingSet.remove(uuid)) return;

        // 실제 첨부되어 있던 아이템들을 플레이어에게 안전하게 반환
        List<ItemStack> attached = MailService.getAttachedItems(uuid);
        if (attached != null && !attached.isEmpty()) {
            for (ItemStack item : attached) {
                if (item != null && !item.getType().isAir()) {
                    player.getInventory().addItem(item);
                }
            }
            MailService.clearAttached(uuid);
        }
    }
}