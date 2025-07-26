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
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, "우편 보내기");
        UUID uuid = player.getUniqueId();

        Map<String, Integer> timeData = MailService.getTimeData(uuid);
        String formattedTime = TimeUtil.format(timeData);
        long expireAt = MailService.getExpireTime(uuid);
        String formattedExpire = TimeUtil.formatDateTime(expireAt);

        List<String> timeLore = new ArrayList<>();
        timeLore.add("§7지속 시간: " + formattedTime);
        timeLore.add(expireAt > 0
                ? "§8만료 시각: §f" + formattedExpire
                : "§8만료 시간이 설정되지 않음");

        inv.setItem(SLOT_TIME, new ItemBuilder(Material.CLOCK)
                .name("§e만료 시간 설정")
                .lore(timeLore)
                .build());

        OfflinePlayer target = MailService.getTargetPlayer(uuid);
        ItemStack targetItem;

        if (target != null && target.getName() != null && target.getName().length() <= 16) {
            targetItem = getCachedHead(target);
        } else {
            targetItem = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("§f받는 사람 선택")
                    .lore("§7클릭하여 우편을 보낼 대상을 선택하세요.")
                    .build();
        }

        inv.setItem(SLOT_TARGET, targetItem);

        ItemStack item = MailService.getAttachedItem(uuid);
        if (item != null) {
            inv.setItem(SLOT_ITEM, item);
        }

        inv.setItem(SLOT_CONFIRM, new ItemBuilder(Material.GREEN_WOOL)
                .name("§a보내기")
                .lore("§7클릭하면 우편을 전송합니다.")
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
        if (!e.getView().getTitle().equals("우편 보내기")) return;

        int slot = e.getRawSlot();
        ClickType click = e.getClick();
        UUID uuid = player.getUniqueId();

        if (click.isShiftClick() || click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
            return;
        }

        if (slot >= e.getInventory().getSize()) return;

        e.setCancelled(slot != SLOT_ITEM);

        switch (slot) {
            case SLOT_TIME -> new MailTimeSelectGUI(plugin).open(player);
            case SLOT_TARGET -> new MailTargetSelectGUI(plugin).open(player);
            case SLOT_CONFIRM -> {
                if (sentSet.contains(uuid)) {
                    player.sendMessage("§c[우편] 이미 이 우편을 보냈습니다. 잠시 후 다시 시도하세요.");
                    return;
                }
                MailService.MailSession session = MailService.getSession(uuid);
                if (session == null || session.item == null || session.item.getType() == Material.AIR || session.target == null) {
                    player.sendMessage("§c[우편] 수신자 또는 아이템이 없습니다. 전송할 수 없습니다.");
                    return;
                }
                MailService.send(player, plugin);
                MailService.setAttachedItem(uuid, null); // ✅ 아이템 제거
                sentSet.add(uuid);
                player.closeInventory();
                player.sendMessage("§a[우편] 우편이 성공적으로 전송되었습니다.");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            case SLOT_BACK -> new MailGUI(plugin).open(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (!e.getView().getTitle().equals("우편 보내기")) return;

        UUID uuid = player.getUniqueId();
        if (sentSet.contains(uuid)) return; // ✅ 이미 보낸 경우 저장 안함

        ItemStack item = e.getInventory().getItem(SLOT_ITEM);

        if (item != null && !item.getType().isAir()) {
            MailService.setAttachedItem(uuid, item);
        } else {
            MailService.setAttachedItem(uuid, null);
        }
    }
}

