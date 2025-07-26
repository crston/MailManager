package com.gmail.bobason01.gui;

import com.gmail.bobason01.config.ConfigLoader;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class MailSendAllGUI implements Listener {

    private static final int SLOT_TIME = 10;
    private static final int SLOT_EXCLUDE = 12;
    private static final int SLOT_ITEM = 14;
    private static final int SLOT_CONFIRM = 16;
    private static final int SLOT_BACK = 26;

    private final Plugin plugin;
    private final Set<UUID> sentSet = new HashSet<>();

    public MailSendAllGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, "전체 우편 발송");
        UUID uuid = player.getUniqueId();

        inv.setItem(SLOT_TIME, new ItemBuilder(Material.CLOCK)
                .name("§e만료 시간 설정")
                .lore("§7클릭하여 우편 만료 시간을 설정하세요.")
                .build());

        inv.setItem(SLOT_EXCLUDE, new ItemBuilder(Material.BARRIER)
                .name("§c제외할 플레이어")
                .lore("§7클릭하여 제외할 플레이어를 선택하세요.")
                .build());

        ItemStack attached = MailService.getAttachedItem(uuid);
        if (attached != null) {
            inv.setItem(SLOT_ITEM, attached);
        }

        inv.setItem(SLOT_CONFIRM, new ItemBuilder(Material.GREEN_WOOL)
                .name("§a모두에게 발송")
                .lore("§7클릭하면 모든 플레이어에게 우편을 보냅니다.\n§7아이템이 첨부되어 있어야 합니다.")
                .build());

        inv.setItem(SLOT_BACK, ConfigLoader.getGuiItem("back"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals("전체 우편 발송")) return;

        int slot = e.getRawSlot();
        ClickType click = e.getClick();
        UUID uuid = player.getUniqueId();
        Inventory inv = e.getInventory();

        // SLOT_ITEM: 드래그 앤 드롭 허용 → 다음 틱에 상태 저장
        if (slot == SLOT_ITEM) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack newItem = inv.getItem(SLOT_ITEM);
                if (newItem != null && !newItem.getType().isAir()) {
                    MailService.setAttachedItem(uuid, newItem.clone());
                } else {
                    MailService.setAttachedItem(uuid, null);
                }
            }, 1L);
            return; // 클릭 허용
        }

        // 쉬프트, 드롭, 더블클릭 등은 무효화
        if (click.isShiftClick() || click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
            return;
        }

        // 슬롯 범위 외 클릭은 무시
        if (slot >= inv.getSize()) return;

        // 나머지 슬롯 클릭 방지
        e.setCancelled(true);

        switch (slot) {
            case SLOT_TIME -> new MailTimeSelectGUI(plugin).open(player);
            case SLOT_EXCLUDE -> new SendAllExcludeGUI(plugin).open(player);
            case SLOT_CONFIRM -> {
                if (sentSet.contains(uuid)) {
                    player.sendMessage("§c[우편] 이미 우편을 보냈습니다. 잠시 후 다시 시도하세요.");
                    return;
                }

                ItemStack item = MailService.getAttachedItem(uuid);
                if (item == null || item.getType().isAir()) {
                    player.sendMessage("§c[우편] 아이템을 첨부한 후 발송할 수 있습니다.");
                    return;
                }

                MailService.sendAll(player, plugin);
                MailService.setAttachedItem(uuid, null);
                sentSet.add(uuid);
                player.sendMessage("§a[우편] 모든 플레이어에게 우편을 성공적으로 발송했습니다.");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.closeInventory();
            }
            case SLOT_BACK -> new MailGUI(plugin).open(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (!e.getView().getTitle().equals("전체 우편 발송")) return;

        UUID uuid = player.getUniqueId();

        if (sentSet.contains(uuid)) return;

        ItemStack item = e.getInventory().getItem(SLOT_ITEM);
        if (item != null && !item.getType().isAir()) {
            player.getInventory().addItem(item);
            MailService.setAttachedItem(uuid, null);
        }
    }
}
