package com.gmail.bobason01.gui;

import com.gmail.bobason01.config.ConfigLoader;
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
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, "Send Mail");
        UUID uuid = player.getUniqueId();

        Map<String, Integer> timeData = MailService.getTimeData(uuid);
        String formattedTime = TimeUtil.format(timeData);
        long expireAt = MailService.getExpireTime(uuid);
        String formattedExpire = TimeUtil.formatDateTime(expireAt);

        List<String> timeLore = new ArrayList<>();
        timeLore.add("§7Duration: " + formattedTime);
        timeLore.add(expireAt > 0
                ? "§8Expires at: §f" + formattedExpire
                : "§8No expiration set");

        inv.setItem(SLOT_TIME, new ItemBuilder(Material.CLOCK)
                .name("§eSet Expiration Time")
                .lore(timeLore)
                .build());

        OfflinePlayer target = MailService.getTargetPlayer(uuid);
        ItemStack targetItem;

        if (target != null && target.getName() != null && target.getName().length() <= 16) {
            targetItem = getCachedHead(target);
        } else {
            targetItem = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("§fSelect Recipient")
                    .lore("§7Click to choose a player to send mail to.")
                    .build();
        }

        inv.setItem(SLOT_TARGET, targetItem);

        ItemStack item = MailService.getAttachedItem(uuid);
        if (item != null) {
            inv.setItem(SLOT_ITEM, item);
        }

        inv.setItem(SLOT_CONFIRM, new ItemBuilder(Material.GREEN_WOOL)
                .name("§aSend")
                .lore("§7Click to send the mail.")
                .build());

        inv.setItem(SLOT_BACK, ConfigLoader.getGuiItem("back"));

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
        if (!e.getView().getTitle().equals("Send Mail")) return;

        ClickType click = e.getClick();
        if (click.isShiftClick() || click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
            return;
        }

        int slot = e.getRawSlot();
        UUID uuid = player.getUniqueId();

        if (slot >= e.getInventory().getSize()) return;
        e.setCancelled(true);

        switch (slot) {
            case SLOT_TIME -> new MailTimeSelectGUI(plugin).open(player);
            case SLOT_TARGET -> new MailTargetSelectGUI(plugin).open(player);
            case SLOT_ITEM -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack newItem = e.getInventory().getItem(SLOT_ITEM);
                if (newItem != null && !newItem.getType().isAir()) {
                    MailService.setAttachedItem(uuid, newItem);
                }
            }, 1L);
            case SLOT_CONFIRM -> {
                if (sentSet.contains(uuid)) {
                    player.sendMessage("§c[Mail] You already sent this mail. Please wait.");
                    return;
                }
                MailService.MailSession session = MailService.getSession(uuid);
                if (session == null || session.item == null || session.item.getType() == Material.AIR || session.target == null) {
                    player.sendMessage("§c[Mail] Missing recipient or item. Cannot send.");
                    return;
                }
                MailService.send(player, plugin);
                sentSet.add(uuid);
                player.closeInventory();
                player.sendMessage("§a[Mail] Mail has been sent.");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            case SLOT_BACK -> new MailGUI(plugin).open(player);
        }
    }
}
