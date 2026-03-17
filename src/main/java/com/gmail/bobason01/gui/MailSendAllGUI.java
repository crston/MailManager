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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MailSendAllGUI implements Listener, InventoryHolder {

    private static final int SLOT_TIME = 10;
    private static final int SLOT_EXCLUDE = 12;
    private static final int SLOT_ATTACH = 14;
    private static final int SLOT_CONFIRM = 16;
    private static final int SLOT_BACK = 26;
    private static final long CLICK_COOLDOWN_MS = 500L;

    private final Plugin plugin;
    private final Map<UUID, AtomicLong> lastClickMap = new ConcurrentHashMap<>();
    private final Set<UUID> processingSet = ConcurrentHashMap.newKeySet();
    private final Set<UUID> navigatingSet = ConcurrentHashMap.newKeySet();

    public MailSendAllGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 27, "Send All");
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(lang, "gui.sendall.title");
        Inventory inv = Bukkit.createInventory(this, 27, title);

        updateContent(player, inv, lang);
        player.openInventory(inv);
    }

    private void updateContent(Player player, Inventory inv, String lang) {
        UUID uuid = player.getUniqueId();
        boolean isProcessing = processingSet.contains(uuid);

        // 1. 만료 시간 설정
        Map<String, Integer> timeData = MailService.getTimeData(uuid);
        String formatted = TimeUtil.format(timeData, lang);
        long expireAt = MailService.getExpireTime(uuid);

        List<String> timeLore = new ArrayList<>();
        timeLore.add(LangManager.get(lang, "gui.send.time.duration").replace("%time%", formatted));
        timeLore.add(LangManager.get(lang, (expireAt > 0 && expireAt < Long.MAX_VALUE) ? "gui.send.time.expires" : "gui.send.time.no_expire")
                .replace("%date%", TimeUtil.formatDateTime(expireAt, lang)));

        inv.setItem(SLOT_TIME, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_GUI_TIME))
                .name(LangManager.get(lang, "gui.send.time.name")).lore(timeLore).build());

        // 2. 제외 대상 설정
        int excludedCount = MailDataManager.getInstance().getExclude(uuid).size();
        List<String> excludeLore = new ArrayList<>();
        for (String line : LangManager.getList(lang, "gui.sendall.exclude.lore")) {
            excludeLore.add(line.replace("%count%", String.valueOf(excludedCount)));
        }

        inv.setItem(SLOT_EXCLUDE, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_ALL_GUI_EXCLUDE))
                .name(LangManager.get(lang, "gui.sendall.exclude.name")).lore(excludeLore).build());

        // 3. 아이템 첨부 상태
        List<ItemStack> items = MailService.getAttachedItems(uuid);
        if (items.isEmpty()) {
            inv.setItem(SLOT_ATTACH, new ItemBuilder(Material.CHEST).name(LangManager.get(lang, "gui.send.item.empty")).build());
        } else {
            ItemStack first = items.get(0);
            List<String> attachLore = new ArrayList<>();
            attachLore.add(LangManager.get(lang, "gui.send.item.first"));
            if (items.size() > 1) {
                attachLore.add(LangManager.get(lang, "gui.send.item.more").replace("%count%", String.valueOf(items.size() - 1)));
            }
            inv.setItem(SLOT_ATTACH, new ItemBuilder(first.clone()).lore(attachLore).build());
        }

        // 4. 발송 버튼 (처리 중이면 배리어로 변경)
        if (isProcessing) {
            inv.setItem(SLOT_CONFIRM, new ItemBuilder(Material.BARRIER).name(LangManager.get(lang, "mail.sendall.processing")).build());
        } else {
            inv.setItem(SLOT_CONFIRM, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.SEND_ALL_GUI_CONFIRM))
                    .name(LangManager.get(lang, "gui.sendall.confirm.name")).lore(LangManager.getList(lang, "gui.sendall.confirm.lore")).build());
        }

        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON)).name("§c" + LangManager.get(lang, "gui.back.name")).build());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendAllGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();
        if (slot < 0) return;

        if (processingSet.contains(uuid)) return; // 발송 중 클릭 전면 차단

        long now = System.currentTimeMillis();
        AtomicLong lastClick = lastClickMap.computeIfAbsent(uuid, k -> new AtomicLong(0));
        if (now - lastClick.get() < CLICK_COOLDOWN_MS) return;
        lastClick.set(now);

        MailManager manager = MailManager.getInstance();

        switch (slot) {
            case SLOT_TIME -> {
                navigatingSet.add(uuid);
                manager.mailTimeSelectGUI.open(player, MailSendAllGUI.class);
            }
            case SLOT_EXCLUDE -> {
                navigatingSet.add(uuid);
                manager.sendAllExcludeGUI.open(player);
            }
            case SLOT_ATTACH -> {
                navigatingSet.add(uuid);
                manager.mailAttachGUI.open(player, MailSendAllGUI.class);
            }
            case SLOT_CONFIRM -> {
                if (MailService.getAttachedItems(uuid).isEmpty()) {
                    player.sendMessage(LangManager.get(uuid, "mail.sendall.no_item"));
                    return;
                }

                processingSet.add(uuid);
                navigatingSet.add(uuid); // 발송 성공 취급 (아이템 반환 방지)

                player.sendMessage(LangManager.get(uuid, "mail.sendall.start"));
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);

                MailService.sendAll(player, plugin);
                player.closeInventory();

                // 발송 프로세스 완료 처리는 MailService의 콜백이나 적절한 시점에 수행 (여기서는 임시 해제)
                Bukkit.getScheduler().runTaskLater(plugin, () -> processingSet.remove(uuid), 100L);
            }
            case SLOT_BACK -> {
                navigatingSet.add(uuid);
                manager.mailGUI.open(player);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendAllGUI)) return;
        Player player = (Player) e.getPlayer();
        UUID uuid = player.getUniqueId();

        // 1. 다른 메뉴로 이동 중이거나 발송 중이면 반환 안 함
        if (navigatingSet.remove(uuid) || processingSet.contains(uuid)) return;

        // 2. 비정상적으로 창을 닫았을 때 아이템 안전 반환 (증발 방지)
        List<ItemStack> attached = MailService.getAttachedItems(uuid);
        if (attached != null && !attached.isEmpty()) {
            for (ItemStack item : attached) {
                if (item == null || item.getType().isAir()) continue;
                Map<Integer, ItemStack> left = player.getInventory().addItem(item);
                left.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
            }
            MailService.clearAttached(uuid);
            player.sendMessage(LangManager.get(uuid, "mail.receive.failed_returned"));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MailSendAllGUI) e.setCancelled(true);
    }
}