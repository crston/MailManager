package com.gmail.bobason01.gui;

import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class MailSendGUI implements Listener {

    private static final int SLOT_TIME = 10;
    private static final int SLOT_TARGET = 12;
    private static final int SLOT_ITEM = 14;
    private static final int SLOT_CONFIRM = 16;
    private static final int SLOT_BACK = 26;

    private final Plugin plugin;
    private final Set<UUID> sentSet = new HashSet<>();
    private final Map<UUID, ItemStack> cachedHeads = new WeakHashMap<>();

    public MailSendGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(uuid, "gui.send.title");
        Inventory inv = Bukkit.createInventory(player, 27, title);

        Map<String, Integer> timeData = MailService.getTimeData(uuid);
        String formatted = TimeUtil.format(timeData, lang);
        long expireAt = MailService.getExpireTime(uuid);
        String formattedExpire = TimeUtil.formatDateTime(expireAt);

        List<String> timeLore = new ArrayList<>();
        timeLore.add(LangManager.get(uuid, "gui.send.time.duration").replace("%time%", formatted));
        timeLore.add(expireAt > 0
                ? LangManager.get(uuid, "gui.send.time.expires").replace("%date%", formattedExpire)
                : LangManager.get(uuid, "gui.send.time.no_expire"));

        inv.setItem(SLOT_TIME, new ItemBuilder(Material.CLOCK)
                .name(LangManager.get(uuid, "gui.send.time.name"))
                .lore(timeLore)
                .build());

        OfflinePlayer target = MailService.getTargetPlayer(uuid);
        ItemStack targetItem;

        if (target != null && target.getName() != null && target.getName().length() <= 16) {
            targetItem = getCachedHead(target);
        } else {
            targetItem = new ItemBuilder(Material.PLAYER_HEAD)
                    .name(LangManager.get(uuid, "gui.send.target.name"))
                    .lore(LangManager.get(uuid, "gui.send.target.lore"))
                    .build();
        }

        inv.setItem(SLOT_TARGET, targetItem);

        ItemStack item = MailService.getAttachedItem(uuid);
        if (item != null) {
            inv.setItem(SLOT_ITEM, item);
        }

        inv.setItem(SLOT_CONFIRM, new ItemBuilder(Material.GREEN_WOOL)
                .name(LangManager.get(uuid, "gui.send.confirm.name"))
                .lore(LangManager.get(uuid, "gui.send.confirm.lore"))
                .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name("§c" + LangManager.get(uuid, "gui.back.name"))
                .lore(LangManager.get(uuid, "gui.back.lore"))
                .build());

        player.openInventory(inv);
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
        if (!(e.getWhoClicked() instanceof Player player)) return;
        String expectedTitle = LangManager.get(player.getUniqueId(), "gui.send.title");
        if (!e.getView().getTitle().equals(expectedTitle)) return;

        int slot = e.getRawSlot();
        ClickType click = e.getClick();
        UUID uuid = player.getUniqueId();
        Inventory inv = e.getInventory();

        if (slot == SLOT_ITEM) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack newItem = inv.getItem(SLOT_ITEM);
                if (newItem != null && !newItem.getType().isAir()) {
                    MailService.setAttachedItem(uuid, newItem.clone());
                } else {
                    MailService.setAttachedItem(uuid, null);
                }
            }, 1L);
            return;
        }

        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.DOUBLE_CLICK || click.isShiftClick()) {
            e.setCancelled(true);
            return;
        }

        if (slot >= e.getInventory().getSize()) return;

        e.setCancelled(true);

        switch (slot) {
            case SLOT_TIME -> new MailTimeSelectGUI(plugin).open(player);
            case SLOT_TARGET -> new MailTargetSelectGUI(plugin).open(player);
            case SLOT_CONFIRM -> {
                if (sentSet.contains(uuid)) {
                    player.sendMessage(LangManager.get(uuid, "mail.send.cooldown"));
                    return;
                }
                MailService.MailSession session = MailService.getSession(uuid);
                if (session == null || session.item == null || session.item.getType() == Material.AIR || session.target == null) {
                    player.sendMessage(LangManager.get(uuid, "mail.send.invalid"));
                    return;
                }
                MailService.send(player, plugin);
                MailService.setAttachedItem(uuid, null);
                sentSet.add(uuid);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                player.closeInventory();
            }
            case SLOT_BACK -> new MailGUI(plugin).open(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        String expectedTitle = LangManager.get(player.getUniqueId(), "gui.send.title");
        if (!e.getView().getTitle().equals(expectedTitle)) return;

        UUID uuid = player.getUniqueId();

        if (sentSet.contains(uuid)) return;

        ItemStack item = e.getInventory().getItem(SLOT_ITEM);
        if (item != null && !item.getType().isAir()) {
            player.getInventory().addItem(item);
            MailService.setAttachedItem(uuid, null);
        }
    }
}
