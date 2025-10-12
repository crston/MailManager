package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
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
            lore.add(LangManager.get(uuid, "gui.send.item.more")
                    .replace("%count%", String.valueOf(items.size() - 1)));
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
        });
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

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        MailManager manager = MailManager.getInstance();

        if (slot == SLOT_BACK) {
            e.setCancelled(true);
            manager.mailGUI.open(player);
            return;
        }

        if (e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.DOUBLE_CLICK || e.getClick() == ClickType.MIDDLE) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        switch (slot) {
            case SLOT_TIME -> manager.mailTimeSelectGUI.open(player, MailSendGUI.class);
            case SLOT_TARGET -> manager.mailTargetSelectGUI.open(player);
            case SLOT_ITEM -> manager.mailAttachGUI.open(player, MailSendGUI.class);
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
                MailService.send(player, plugin);
                MailService.clearAttached(uuid);
                ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_SEND_SUCCESS);
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MailSendGUI && e.getRawSlots().contains(SLOT_BACK)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailSendGUI) || !(e.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        if (sentSet.contains(uuid)) return;

        ItemStack item = e.getInventory().getItem(SLOT_ITEM);
        if (item != null && !item.getType().isAir()) {
            player.getInventory().addItem(item);
            MailService.clearAttached(uuid);
        }
    }
}
