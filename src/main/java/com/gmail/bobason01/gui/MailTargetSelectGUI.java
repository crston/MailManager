package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ChatSearchRegistry;
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
import org.bukkit.event.player.PlayerQuitEvent;
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
    private static final int SLOT_SEARCH = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_BACK = 53;
    private static final long PAGE_COOLDOWN_MS = 300L;

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> cooldownMap = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> headCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> searching = new ConcurrentHashMap<>();

    public MailTargetSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54);
    }

    public void open(Player player) {
        open(player, pageMap.getOrDefault(player.getUniqueId(), 0));
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        if (Boolean.TRUE.equals(searching.get(uuid))) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<OfflinePlayer> players = new ArrayList<>(PlayerCache.getCachedPlayers());
            players.removeIf(p -> p == null || p.getName() == null || p.getUniqueId().equals(uuid));
            players.sort(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER));

            int maxPage = Math.max(0, (players.size() - 1) / PAGE_SIZE);
            int safePage = Math.min(Math.max(page, 0), maxPage);
            pageMap.put(uuid, safePage); // 보정된 페이지 저장

            int start = safePage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, players.size());
            List<OfflinePlayer> pagePlayers = players.subList(start, end);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                String lang = LangManager.getLanguage(uuid);
                String title = LangManager.get(lang, "gui.target.title")
                        .replace("%page%", String.valueOf(safePage + 1))
                        .replace("%maxpage%", String.valueOf(maxPage + 1));

                Inventory inv = Bukkit.createInventory(this, 54, title);

                for (int i = 0; i < pagePlayers.size(); i++) {
                    inv.setItem(i, getCachedHead(pagePlayers.get(i)));
                }

                if (safePage > 0) inv.setItem(SLOT_PREV, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON)).name(LangManager.get(lang, "gui.previous")).build());
                if (safePage < maxPage) inv.setItem(SLOT_NEXT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON)).name(LangManager.get(lang, "gui.next")).build());
                inv.setItem(SLOT_SEARCH, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH)).name(LangManager.get(lang, "gui.search.name")).build());
                inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON)).name(LangManager.get(lang, "gui.back.name")).build());

                player.openInventory(inv);
            });
        });
    }

    private ItemStack getCachedHead(OfflinePlayer target) {
        return headCache.computeIfAbsent(target.getUniqueId(), k -> {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + (target.getName() != null ? target.getName() : "Unknown"));
                meta.setOwningPlayer(target);
                skull.setItemMeta(meta);
            }
            return skull;
        }).clone();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailTargetSelectGUI) || !(e.getWhoClicked() instanceof Player player)) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        if (slot < 0) return;

        // 쿨다운 체크
        long now = System.currentTimeMillis();
        AtomicLong last = cooldownMap.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        if (now - last.get() < PAGE_COOLDOWN_MS) return;
        last.set(now);

        UUID uuid = player.getUniqueId();
        int page = pageMap.getOrDefault(uuid, 0);

        if (slot == SLOT_PREV) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
            open(player, page - 1);
        } else if (slot == SLOT_NEXT) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_PAGE_TURN);
            open(player, page + 1);
        } else if (slot == SLOT_BACK) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            MailManager.getInstance().mailSendGUI.open(player);
        } else if (slot == SLOT_SEARCH) {
            searching.put(uuid, true);
            player.closeInventory();
            player.sendMessage(LangManager.get(uuid, "gui.search.start"));
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);

            ChatSearchRegistry.register(player, message -> {
                searching.remove(uuid);
                if (message == null || message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("취소")) {
                    Bukkit.getScheduler().runTask(plugin, () -> open(player, page));
                    return;
                }
                MailDataManager.getInstance().getGlobalUUID(message.trim()).thenAccept(targetUUID -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (targetUUID == null || targetUUID.equals(uuid)) {
                            player.sendMessage(LangManager.get(uuid, targetUUID == null ? "cmd.player.notfound" : "gui.target.self"));
                            open(player, page);
                        } else {
                            selectTarget(player, Bukkit.getOfflinePlayer(targetUUID));
                        }
                    });
                });
            });
        } else if (slot < PAGE_SIZE) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            OfflinePlayer target = PlayerCache.getByName(name);
            if (target != null && !target.getUniqueId().equals(uuid)) selectTarget(player, target);
        }
    }

    private void selectTarget(Player player, OfflinePlayer target) {
        MailService.setTarget(player.getUniqueId(), target);
        player.sendMessage(LangManager.get(player.getUniqueId(), "gui.target.selected").replace("%target%", target.getName()));
        ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SETTING_CHANGE);
        MailManager.getInstance().mailSendGUI.open(player);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MailTargetSelectGUI) {
            UUID uuid = e.getPlayer().getUniqueId();
            if (!Boolean.TRUE.equals(searching.get(uuid))) {
                pageMap.remove(uuid);
                cooldownMap.remove(uuid);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        searching.remove(uuid);
        pageMap.remove(uuid);
        cooldownMap.remove(uuid);
    }
}