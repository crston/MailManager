package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.mail.BlacklistManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MailSendGUI implements Listener {

    private final MailManager plugin;
    private final Map<UUID, Inventory> inventoryMap = new HashMap<>();
    private final Map<UUID, String> targetMap = new HashMap<>();
    private final Map<UUID, Long> expireMap = new HashMap<>();

    public MailSendGUI(MailManager plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        Inventory gui = plugin.getServer().createInventory(player, 27, LangUtil.get(uuid, "gui.send.title"));
        inventoryMap.put(uuid, gui);

        gui.setItem(10, new ItemBuilder(Material.CLOCK).name(LangUtil.get(uuid, "gui.send.set-time")).build());
        gui.setItem(12, new ItemBuilder(Material.NAME_TAG).name(LangUtil.get(uuid, "gui.send.set-target")).build());
        gui.setItem(14, new ItemBuilder(Material.CHEST).name(LangUtil.get(uuid, "gui.send.put-item")).build());
        gui.setItem(16, new ItemBuilder(Material.ENDER_PEARL).name(LangUtil.get(uuid, "gui.send.confirm")).build());
        gui.setItem(18, new ItemBuilder(Material.BARRIER).name(LangUtil.get(uuid, "gui.back")).build());

        player.openInventory(gui);
    }

    public void setTarget(UUID sender, String name) {
        targetMap.put(sender, name);
    }

    public void setExpire(UUID sender, long millis) {
        expireMap.put(sender, millis);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Inventory gui = inventoryMap.get(uuid);
        if (gui == null || !event.getInventory().equals(gui)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == 10) {
            new MailTimeSelectGUI(plugin, this).open(player);
        } else if (slot == 12) {
            new MailTargetSelectGUI(plugin, this).open(player);
        } else if (slot == 16) {
            ItemStack mailItem = gui.getItem(14);
            if (mailItem == null || mailItem.getType() == Material.AIR) {
                player.sendMessage(LangUtil.get(uuid, "gui.send.no-item"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                return;
            }

            String targetName = targetMap.get(uuid);
            if (targetName == null) {
                player.sendMessage(LangUtil.get(uuid, "gui.send.no-target"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                return;
            }

            Player target = plugin.getServer().getPlayerExact(targetName);
            if (target == null) {
                player.sendMessage("§c해당 플레이어는 오프라인입니다.");
                return;
            }

            UUID targetId = target.getUniqueId();
            if (BlacklistManager.isBlocked(targetId, uuid)) {
                player.sendMessage("§c해당 플레이어에게는 메일을 보낼 수 없습니다. (차단됨)");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                return;
            }

            long expire = expireMap.getOrDefault(uuid,
                    System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000); // 기본 7일

            Mail mail = new Mail(uuid, mailItem.clone(), expire);
            MailService.addMail(targetId, mail);

            player.sendMessage(LangUtil.get(uuid, "gui.send.success")
                    .replace("%target%", target.getName()));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            player.closeInventory();
        } else if (slot == 18) {
            new MailGUI(plugin).open(player);
        } else if (slot == 14) {
            // 14번 슬롯에 아이템을 자유롭게 넣고 빼게 허용
            event.setCancelled(false);
        }
    }
}
