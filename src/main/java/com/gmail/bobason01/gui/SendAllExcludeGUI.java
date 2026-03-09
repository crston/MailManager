package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
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

public class SendAllExcludeGUI implements Listener, InventoryHolder {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_SEARCH = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_BACK = 53;
    private static final long PAGE_COOLDOWN_MS = 300L; // 조금 더 부드러운 조작을 위해 0.3초로 조정

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> lastClickMap = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> headCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> searching = new ConcurrentHashMap<>();

    public SendAllExcludeGUI(Plugin plugin) {
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

        pageMap.put(uuid, page);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<OfflinePlayer> players = new ArrayList<>(PlayerCache.getCachedPlayers());
            players.removeIf(p -> p == null || p.getName() == null || p.getUniqueId().equals(uuid));
            players.sort(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER));

            int maxPage = Math.max(0, (players.size() - 1) / PAGE_SIZE);
            int safePage = Math.min(Math.max(page, 0), maxPage);
            pageMap.put(uuid, safePage); // 보정된 페이지 저장

            int start = safePage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, players.size());
            List<OfflinePlayer> subList = players.subList(start, end);

            Set<UUID> excludedSet = MailDataManager.getInstance().getExclude(uuid);
            String lang = LangManager.getLanguage(uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                String title = LangManager.get(lang, "gui.exclude.title")
                        .replace("%page%", String.valueOf(safePage + 1))
                        .replace("%maxpage%", String.valueOf(maxPage + 1));

                Inventory inv = Bukkit.createInventory(this, 54, title);

                for (int i = 0; i < subList.size(); i++) {
                    inv.setItem(i, createPlayerItem(subList.get(i), excludedSet.contains(subList.get(i).getUniqueId()), lang));
                }

                if (safePage > 0) {
                    inv.setItem(SLOT_PREV, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON))
                            .name(LangManager.get(lang, "gui.previous")).build());
                }
                if (safePage < maxPage) {
                    inv.setItem(SLOT_NEXT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON))
                            .name(LangManager.get(lang, "gui.next")).build());
                }

                inv.setItem(SLOT_SEARCH, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH))
                        .name(LangManager.get(lang, "gui.exclude.search.name")).build());

                inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                        .name(LangManager.get(lang, "gui.back.name")).build());

                player.openInventory(inv);
            });
        });
    }

    private ItemStack createPlayerItem(OfflinePlayer target, boolean isExcluded, String lang) {
        UUID id = target.getUniqueId();
        ItemStack base = headCache.computeIfAbsent(id, k -> {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null) { meta.setOwningPlayer(target); item.setItemMeta(meta); }
            return item;
        });

        ItemStack head = base.clone();
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            String name = target.getName() != null ? target.getName() : "Unknown";
            meta.setDisplayName(isExcluded ? "§c" + name : "§a" + name);
            meta.setLore(LangManager.getList(lang, isExcluded ? "gui.exclude.status.excluded" : "gui.exclude.status.included"));
            head.setItemMeta(meta);
        }
        return head;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof SendAllExcludeGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0) return;

        // 쿨다운 체크
        long now = System.currentTimeMillis();
        AtomicLong lastClick = lastClickMap.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        if (now - lastClick.get() < PAGE_COOLDOWN_MS) return;
        lastClick.set(now);

        UUID uuid = player.getUniqueId();
        int page = pageMap.getOrDefault(uuid, 0);

        if (slot == SLOT_PREV) {
            open(player, page - 1);
        } else if (slot == SLOT_NEXT) {
            open(player, page + 1);
        } else if (slot == SLOT_BACK) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            MailManager.getInstance().mailSendAllGUI.open(player);
        } else if (slot == SLOT_SEARCH) {
            searching.put(uuid, true);
            player.closeInventory();
            player.sendMessage(LangManager.get(uuid, "gui.exclude.search.start"));
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
                            player.sendMessage(LangManager.get(uuid, targetUUID == null ? "gui.exclude.search.not_found" : "gui.exclude.search.self"));
                        } else {
                            MailDataManager.getInstance().toggleExclude(uuid, targetUUID);
                            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                        }
                        open(player, page);
                    });
                });
            });
        } else if (slot < PAGE_SIZE) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            OfflinePlayer target = PlayerCache.getByName(name);
            if (target != null) {
                MailDataManager.getInstance().toggleExclude(uuid, target.getUniqueId());
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                open(player, page);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof SendAllExcludeGUI) {
            UUID uuid = e.getPlayer().getUniqueId();
            if (!Boolean.TRUE.equals(searching.get(uuid))) {
                pageMap.remove(uuid);
                lastClickMap.remove(uuid);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        pageMap.remove(uuid);
        lastClickMap.remove(uuid);
        searching.remove(uuid);
    }
}