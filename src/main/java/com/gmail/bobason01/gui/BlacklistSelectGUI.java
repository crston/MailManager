package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

public class BlacklistSelectGUI implements Listener, InventoryHolder {

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

    // 헤드 아이콘 캐시 (메모리 관리용)
    private final Map<UUID, ItemStack> headCache = new ConcurrentHashMap<>();

    public BlacklistSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54, "Blacklist Selection");
    }

    public void open(Player player) {
        open(player, pageMap.getOrDefault(player.getUniqueId(), 0));
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        if (waitingForSearch.contains(uuid)) return;
        if (!loadingSet.add(uuid)) return;

        pageMap.put(uuid, page);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 플레이어 목록 필터링 및 정렬
                List<OfflinePlayer> players = new ArrayList<>(PlayerCache.getCachedPlayers());
                players.removeIf(p -> p == null || p.getName() == null || p.getUniqueId().equals(uuid));
                players.sort(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER));

                int maxPage = Math.max(0, (players.size() - 1) / PAGE_SIZE);
                int safePage = Math.min(Math.max(page, 0), maxPage);
                int start = safePage * PAGE_SIZE;
                int end = Math.min(start + PAGE_SIZE, players.size());
                List<OfflinePlayer> subList = players.subList(start, end);

                Set<UUID> blocked = MailDataManager.getInstance().getBlacklist(uuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (!player.isOnline()) return;

                        String lang = LangManager.getLanguage(uuid);
                        String title = LangManager.get(lang, "gui.blacklist.title")
                                .replace("%page%", String.valueOf(safePage + 1))
                                .replace("%maxpage%", String.valueOf(maxPage + 1));

                        Inventory inv = Bukkit.createInventory(this, 54, title);

                        for (int i = 0; i < subList.size(); i++) {
                            inv.setItem(i, createPlayerHead(subList.get(i), blocked.contains(subList.get(i).getUniqueId()), lang));
                        }

                        // 기능 버튼 배치
                        setupButtons(inv, lang, safePage, safePage < maxPage);

                        player.openInventory(inv);
                    } finally {
                        loadingSet.remove(uuid);
                    }
                });
            } catch (Exception ex) {
                loadingSet.remove(uuid);
            }
        });
    }

    private ItemStack createPlayerHead(OfflinePlayer target, boolean isBlocked, String lang) {
        if (headCache.size() > 500) headCache.clear();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName((isBlocked ? "§c" : "§a") + target.getName());
            meta.setLore(LangManager.getList(lang, isBlocked ? "gui.blacklist.blocked" : "gui.blacklist.allowed"));
            head.setItemMeta(meta);
        }
        return head;
    }

    private void setupButtons(Inventory inv, String lang, int page, boolean hasNext) {
        inv.setItem(SLOT_SEARCH, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH))
                .name("§b" + LangManager.get(lang, "gui.search.name"))
                .lore(LangManager.getList(lang, "gui.blacklist.search_prompt")).build());

        if (page > 0) inv.setItem(SLOT_PREV, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON)).name("§a" + LangManager.get(lang, "gui.previous")).build());
        if (hasNext) inv.setItem(SLOT_NEXT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON)).name("§a" + LangManager.get(lang, "gui.next")).build());

        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(lang, "gui.back.name")).build());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof BlacklistSelectGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        AtomicLong lastClick = lastClickMap.computeIfAbsent(uuid, k -> new AtomicLong(0));
        if (now - lastClick.get() < PAGE_COOLDOWN_MS) return;
        lastClick.set(now);

        int page = pageMap.getOrDefault(uuid, 0);

        if (slot == SLOT_SEARCH) {
            waitingForSearch.add(uuid);
            player.closeInventory();
            player.sendMessage(LangManager.get(uuid, "gui.blacklist.search_prompt_single"));
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
        } else if (slot == SLOT_PREV) {
            open(player, page - 1);
        } else if (slot == SLOT_NEXT) {
            open(player, page + 1);
        } else if (slot == SLOT_BACK) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            MailManager.getInstance().mailSettingGUI.open(player);
        } else if (slot < PAGE_SIZE) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                toggleBlacklist(player, meta.getOwningPlayer(), page);
            }
        }
    }

    private void toggleBlacklist(Player player, OfflinePlayer target, int page) {
        UUID uuid = player.getUniqueId();
        UUID targetId = target.getUniqueId();

        Set<UUID> blocked = new HashSet<>(MailDataManager.getInstance().getBlacklist(uuid));
        if (blocked.contains(targetId)) {
            blocked.remove(targetId);
            player.sendMessage(LangManager.get(uuid, "gui.blacklist.unblocked").replace("%name%", target.getName()));
        } else {
            blocked.add(targetId);
            player.sendMessage(LangManager.get(uuid, "gui.blacklist.blocked_msg").replace("%name%", target.getName()));
        }

        MailDataManager.getInstance().setBlacklist(uuid, blocked);
        ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
        open(player, page);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!waitingForSearch.contains(uuid)) return;

        e.setCancelled(true);
        waitingForSearch.remove(uuid);

        String input = e.getMessage().trim();
        int currentPage = pageMap.getOrDefault(uuid, 0);

        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("취소")) {
            player.sendMessage(LangManager.get(uuid, "gui.search.cancelled"));
            Bukkit.getScheduler().runTask(plugin, () -> open(player, currentPage));
            return;
        }

        MailDataManager.getInstance().getGlobalUUID(input).thenAccept(targetUUID -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (targetUUID == null || targetUUID.equals(uuid)) {
                    player.sendMessage(LangManager.get(uuid, targetUUID == null ? "gui.blacklist.not_found" : "gui.blacklist.self_error"));
                    open(player, currentPage);
                    return;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
                toggleBlacklist(player, target, currentPage);
            });
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof BlacklistSelectGUI) {
            UUID uuid = e.getPlayer().getUniqueId();
            if (!waitingForSearch.contains(uuid)) {
                pageMap.remove(uuid);
                lastClickMap.remove(uuid);
            }
            loadingSet.remove(uuid);
        }
    }
}