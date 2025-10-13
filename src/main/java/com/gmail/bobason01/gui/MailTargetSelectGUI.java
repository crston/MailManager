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
    private static final int SLOT_PREV = 48;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_BACK = 53;
    private static final long PAGE_COOLDOWN_MS = 1000L;

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> cooldownMap = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ItemStack> cachedHeads = new WeakHashMap<>();

    public MailTargetSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54);
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        if (!loading.add(uuid)) return;
        pageMap.put(uuid, page);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<OfflinePlayer> players = PlayerCache.getCachedPlayers().stream()
                    .filter(p -> p.getName() != null && !p.getUniqueId().equals(uuid))
                    .sorted(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            int maxPage = Math.max(0, (players.size() - 1) / PAGE_SIZE);
            int safePage = Math.min(Math.max(page, 0), maxPage);
            int start = safePage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, players.size());
            List<OfflinePlayer> pagePlayers = players.subList(start, end);

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!player.isOnline()) return;

                    String lang = LangManager.getLanguage(uuid);
                    String title = LangManager.get(lang, "gui.target.title")
                            .replace("%page%", String.valueOf(safePage + 1))
                            .replace("%maxpage%", String.valueOf(maxPage + 1));

                    Inventory inv = Bukkit.createInventory(this, 54, title);

                    for (int i = 0; i < pagePlayers.size(); i++) {
                        inv.setItem(i, getCachedHead(pagePlayers.get(i)));
                    }

                    if (safePage > 0) {
                        inv.setItem(SLOT_PREV, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON))
                                .name("§a" + LangManager.get(lang, "gui.previous"))
                                .lore(LangManager.getList(lang, "gui.page.prev_lore"))
                                .build());
                    }
                    if (safePage < maxPage) {
                        inv.setItem(SLOT_NEXT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON))
                                .name("§a" + LangManager.get(lang, "gui.next"))
                                .lore(LangManager.getList(lang, "gui.page.next_lore"))
                                .build());
                    }
                    inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                            .name("§c" + LangManager.get(lang, "gui.back.name"))
                            .lore(LangManager.getList(lang, "gui.back.lore"))
                            .build());

                    player.openInventory(inv);
                } finally {
                    loading.remove(uuid);
                }
            });
        });
    }

    private ItemStack getCachedHead(OfflinePlayer target) {
        return cachedHeads.computeIfAbsent(target.getUniqueId(), id -> {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + target.getName());
                meta.setOwningPlayer(target);
                skull.setItemMeta(meta);
            }
            return skull;
        }).clone();
    }

    private boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis();
        AtomicLong last = cooldownMap.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        if (now - last.get() < PAGE_COOLDOWN_MS) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
            return false;
        }
        last.set(now);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailTargetSelectGUI) || !(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;

        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();
        int page = pageMap.getOrDefault(uuid, 0);

        if (!checkCooldown(player)) return;

        switch (slot) {
            case SLOT_PREV -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, page - 1);
            }
            case SLOT_NEXT -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
                open(player, page + 1);
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
            UUID uuid = e.getPlayer().getUniqueId();
            pageMap.remove(uuid);
            loading.remove(uuid);
        }
    }
}
