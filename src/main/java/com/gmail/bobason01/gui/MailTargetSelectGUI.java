package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MailTargetSelectGUI implements Listener, InventoryHolder {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_BACK = 53;
    private static final long PAGE_COOLDOWN_MS = 1000L;

    private final Plugin plugin;
    private final Map<UUID, Integer> playerPageMap = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> lastPageClickMap = new ConcurrentHashMap<>();
    private final Set<UUID> loadingSet = ConcurrentHashMap.newKeySet();

    public MailTargetSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();

        if (!loadingSet.add(uuid)) return;

        playerPageMap.put(uuid, page);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<OfflinePlayer> allPlayers = PlayerCache.getCachedPlayers().stream()
                    .filter(p -> p.getName() != null && !p.getUniqueId().equals(uuid))
                    .sorted(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            int maxPage = Math.max(0, (allPlayers.size() - 1) / PAGE_SIZE);
            int safePage = Math.min(Math.max(page, 0), maxPage);
            int start = safePage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, allPlayers.size());

            List<OfflinePlayer> pagePlayers = allPlayers.subList(start, end);

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!player.isOnline()) return;

                    String lang = LangManager.getLanguage(uuid);
                    String title = LangManager.get(lang, "gui.target.title")
                            .replace("%page%", String.valueOf(safePage + 1))
                            .replace("%maxpage%", String.valueOf(maxPage + 1));

                    Inventory inv = Bukkit.createInventory(this, 54, title);

                    // 플레이어 목록 채우기
                    for (int i = 0; i < pagePlayers.size(); i++) {
                        OfflinePlayer target = pagePlayers.get(i);
                        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta meta = (SkullMeta) skull.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName("§e" + target.getName());
                            meta.setOwningPlayer(target);
                            skull.setItemMeta(meta);
                            inv.setItem(i, skull);
                        }
                    }

                    // 다음/이전/뒤로가기 버튼
                    if (safePage < maxPage) {
                        inv.setItem(SLOT_NEXT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON).clone())
                                .name("§a" + LangManager.get(lang, "gui.next"))
                                .build());
                    }
                    if (safePage > 0) {
                        inv.setItem(SLOT_PREV, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON).clone())
                                .name("§a" + LangManager.get(lang, "gui.previous"))
                                .build());
                    }
                    inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON).clone())
                            .name("§c" + LangManager.get(lang, "gui.back.name"))
                            .lore(LangManager.get(lang, "gui.back.lore"))
                            .build());

                    player.openInventory(inv);
                } finally {
                    loadingSet.remove(uuid);
                }
            });
        });
    }

    private boolean hasCooldownPassed(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        AtomicLong lastClick = lastPageClickMap.computeIfAbsent(uuid, k -> new AtomicLong(0));
        if (now - lastClick.get() < PAGE_COOLDOWN_MS) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
            return false;
        }
        lastClick.set(now);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailTargetSelectGUI) || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();
        int currentPage = playerPageMap.getOrDefault(uuid, 0);

        if (!hasCooldownPassed(player)) return;

        switch (slot) {
            case SLOT_NEXT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage + 1);
            }
            case SLOT_PREV -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, currentPage - 1);
            }
            case SLOT_BACK -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                MailManager.getInstance().mailSendGUI.open(player);
            }
            default -> {
                if (clicked.getType() != Material.PLAYER_HEAD) return;
                String name = ChatColor.stripColor(Objects.requireNonNull(clicked.getItemMeta()).getDisplayName());

                OfflinePlayer target = PlayerCache.getByName(name);
                if (target == null || target.getUniqueId().equals(uuid)) {
                    player.sendMessage(LangManager.get(uuid, "gui.target.invalid"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                    return;
                }

                MailService.setTarget(uuid, target);
                player.sendMessage(LangManager.get(uuid, "gui.target.selected").replace("%target%", name));
                ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SETTING_CHANGE);
                MailManager.getInstance().mailSendGUI.open(player);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MailTargetSelectGUI) {
            playerPageMap.remove(e.getPlayer().getUniqueId());
            loadingSet.remove(e.getPlayer().getUniqueId());
        }
    }
}
