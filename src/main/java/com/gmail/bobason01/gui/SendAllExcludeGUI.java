package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
    private static final long PAGE_COOLDOWN_MS = 500L;

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> lastClickMap = new ConcurrentHashMap<>();
    private final Set<UUID> loadingSet = ConcurrentHashMap.newKeySet();
    private final Set<UUID> waitingForSearch = ConcurrentHashMap.newKeySet();

    private final Map<UUID, ItemStack> baseHeadCache = new WeakHashMap<>();

    public SendAllExcludeGUI(Plugin plugin) {
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
        if (waitingForSearch.contains(uuid)) return;

        if (!loadingSet.add(uuid)) return;
        pageMap.put(uuid, page);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Load Player List (Local Cache for GUI display)
                List<OfflinePlayer> players = new ArrayList<>(PlayerCache.getCachedPlayers());
                players.removeIf(p -> p.getName() == null || p.getUniqueId().equals(uuid));
                players.sort(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER));

                // Pagination
                int maxPage = Math.max(0, (players.size() - 1) / PAGE_SIZE);
                int safePage = Math.min(Math.max(page, 0), maxPage);
                int start = safePage * PAGE_SIZE;
                int end = Math.min(start + PAGE_SIZE, players.size());
                List<OfflinePlayer> subList = players.subList(start, end);

                Set<UUID> excludedSet = MailDataManager.getInstance().getExclude(uuid);
                String lang = LangManager.getLanguage(uuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (!player.isOnline()) return;

                        String title = LangManager.get(lang, "gui.exclude.title")
                                .replace("%page%", String.valueOf(safePage + 1))
                                .replace("%maxpage%", String.valueOf(maxPage + 1));
                        Inventory inv = Bukkit.createInventory(this, 54, title);

                        // Render Heads
                        for (int i = 0; i < subList.size(); i++) {
                            OfflinePlayer target = subList.get(i);
                            boolean isExcluded = excludedSet.contains(target.getUniqueId());
                            inv.setItem(i, createPlayerItem(target, isExcluded, lang));
                        }

                        // Navigation
                        if (safePage > 0) {
                            inv.setItem(SLOT_PREV, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON))
                                    .name(LangManager.get(lang, "gui.previous"))
                                    .lore(LangManager.getList(lang, "gui.page.prev_lore"))
                                    .build());
                        }
                        if (safePage < maxPage) {
                            inv.setItem(SLOT_NEXT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON))
                                    .name(LangManager.get(lang, "gui.next"))
                                    .lore(LangManager.getList(lang, "gui.page.next_lore"))
                                    .build());
                        }

                        // Search Button
                        inv.setItem(SLOT_SEARCH, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH))
                                .name(LangManager.get(lang, "gui.exclude.search.name"))
                                .lore(LangManager.getList(lang, "gui.exclude.search.prompt"))
                                .build());

                        // Back Button
                        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                                .name("§c" + LangManager.get(lang, "gui.back.name"))
                                .lore(LangManager.getList(lang, "gui.back.lore"))
                                .build());

                        player.openInventory(inv);
                    } finally {
                        loadingSet.remove(uuid);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                loadingSet.remove(uuid);
            }
        });
    }

    private ItemStack createPlayerItem(OfflinePlayer target, boolean isExcluded, String lang) {
        ItemStack head = baseHeadCache.computeIfAbsent(target.getUniqueId(), k -> {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                item.setItemMeta(meta);
            }
            return item;
        }).clone();

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            String name = target.getName() != null ? target.getName() : "Unknown";
            if (isExcluded) {
                meta.setDisplayName("§c" + name);
                meta.setLore(LangManager.getList(lang, "gui.exclude.status.excluded"));
            } else {
                meta.setDisplayName("§a" + name);
                meta.setLore(LangManager.getList(lang, "gui.exclude.status.included"));
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    private boolean hasCooldownPassed(Player player) {
        long now = System.currentTimeMillis();
        AtomicLong lastClick = lastClickMap.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        if (now - lastClick.get() < PAGE_COOLDOWN_MS) {
            player.sendMessage(LangManager.get(player.getUniqueId(), "gui.page.cooldown"));
            return false;
        }
        lastClick.set(now);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof SendAllExcludeGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0) return;

        if (!hasCooldownPassed(player)) return;

        UUID uuid = player.getUniqueId();
        int page = pageMap.getOrDefault(uuid, 0);

        switch (slot) {
            case SLOT_PREV -> open(player, page - 1);
            case SLOT_NEXT -> open(player, page + 1);
            case SLOT_BACK -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                MailManager.getInstance().mailSendAllGUI.open(player);
            }
            case SLOT_SEARCH -> {
                player.closeInventory();
                waitingForSearch.add(uuid);
                player.sendMessage(LangManager.get(uuid, "gui.exclude.search.start"));
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            }
            default -> {
                if (slot < PAGE_SIZE) {
                    ItemStack clicked = e.getCurrentItem();
                    if (clicked == null || !clicked.hasItemMeta()) return;

                    String displayName = clicked.getItemMeta().getDisplayName();
                    String targetName = ChatColor.stripColor(displayName);

                    OfflinePlayer target = PlayerCache.getByName(targetName);
                    if (target == null) return;

                    // Toggle Exclude State
                    boolean isNowExcluded = MailDataManager.getInstance().toggleExclude(uuid, target.getUniqueId());

                    if (isNowExcluded) {
                        player.sendMessage(LangManager.get(uuid, "gui.exclude.msg.excluded").replace("%name%", targetName));
                        ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                    } else {
                        player.sendMessage(LangManager.get(uuid, "gui.exclude.msg.included").replace("%name%", targetName));
                        ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                    }

                    open(player, page);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof SendAllExcludeGUI) {
            UUID uuid = e.getPlayer().getUniqueId();
            if (!waitingForSearch.contains(uuid)) {
                pageMap.remove(uuid);
            }
            loadingSet.remove(uuid);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!waitingForSearch.remove(uuid)) return;

        e.setCancelled(true);
        String input = e.getMessage().trim();
        int currentPage = pageMap.getOrDefault(uuid, 0);

        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("취소")) {
            player.sendMessage(LangManager.get(uuid, "gui.search.cancelled"));
            Bukkit.getScheduler().runTask(plugin, () -> open(player, currentPage));
            return;
        }

        // [수정됨] 글로벌 플레이어 검색
        MailDataManager.getInstance().getGlobalUUID(input).thenAccept(targetUUID -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (targetUUID == null) {
                    player.sendMessage(LangManager.get(uuid, "gui.exclude.search.not_found").replace("%input%", input));
                    open(player, currentPage);
                    return;
                }

                if (targetUUID.equals(uuid)) {
                    player.sendMessage(LangManager.get(uuid, "gui.exclude.search.self"));
                    open(player, currentPage);
                    return;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
                String name = target.getName() != null ? target.getName() : input;

                boolean isNowExcluded = MailDataManager.getInstance().toggleExclude(uuid, targetUUID);

                if (isNowExcluded) {
                    player.sendMessage(LangManager.get(uuid, "gui.exclude.msg.excluded").replace("%name%", name));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                } else {
                    player.sendMessage(LangManager.get(uuid, "gui.exclude.msg.included").replace("%name%", name));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                }

                open(player, currentPage);
            });
        });
    }
}