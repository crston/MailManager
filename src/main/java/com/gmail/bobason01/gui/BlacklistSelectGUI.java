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
    private static final long PAGE_COOLDOWN_MS = 1000L;

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> lastClickMap = new ConcurrentHashMap<>();
    private final Set<UUID> loadingSet = ConcurrentHashMap.newKeySet();
    private final Set<UUID> waitingForSearch = ConcurrentHashMap.newKeySet();

    public BlacklistSelectGUI(Plugin plugin) {
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
        pageMap.put(uuid, page);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<OfflinePlayer> players = PlayerCache.getCachedPlayers().stream()
                        .filter(p -> p.getName() != null && !p.getUniqueId().equals(uuid))
                        .sorted(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();

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
                            OfflinePlayer target = subList.get(i);
                            boolean isBlocked = blocked.contains(target.getUniqueId());
                            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                            SkullMeta meta = (SkullMeta) head.getItemMeta();
                            if (meta != null) {
                                meta.setDisplayName((isBlocked ? "§c" : "§a") + target.getName());
                                meta.setOwningPlayer(target);
                                meta.setLore(Collections.singletonList(isBlocked
                                        ? LangManager.get(lang, "gui.blacklist.blocked")
                                        : LangManager.get(lang, "gui.blacklist.allowed")));
                                head.setItemMeta(meta);
                                inv.setItem(i, head);
                            }
                        }

                        // 검색 버튼
                        inv.setItem(SLOT_SEARCH, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH).clone())
                                .name("§b" + LangManager.get(lang, "gui.search.name"))
                                .lore(LangManager.get(lang, "gui.blacklist.search_prompt"))
                                .build());

                        // 이전/다음 버튼
                        if (safePage > 0)
                            inv.setItem(SLOT_PREV, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON).clone())
                                    .name("§a" + LangManager.get(lang, "gui.previous"))
                                    .build());
                        if (safePage < maxPage)
                            inv.setItem(SLOT_NEXT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON).clone())
                                    .name("§a" + LangManager.get(lang, "gui.next"))
                                    .build());

                        // 뒤로가기 버튼
                        inv.setItem(SLOT_BACK, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON).clone())
                                .name("§c" + LangManager.get(lang, "gui.back.name"))
                                .lore("§7" + LangManager.get(lang, "gui.back.lore"))
                                .build());

                        player.openInventory(inv);
                    } finally {
                        loadingSet.remove(uuid);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> loadingSet.remove(uuid));
            }
        });
    }

    private boolean hasCooldownPassed(Player player) {
        long now = System.currentTimeMillis();
        AtomicLong lastClick = lastClickMap.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        if (now - lastClick.get() < PAGE_COOLDOWN_MS) {
            player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.GUI_CLICK_FAIL), 1.0f, 0.5f);
            return false;
        }
        lastClick.set(now);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof BlacklistSelectGUI) || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0) return;

        UUID uuid = player.getUniqueId();
        int page = pageMap.getOrDefault(uuid, 0);

        if (!hasCooldownPassed(player)) return;

        switch (slot) {
            case SLOT_SEARCH -> {
                player.closeInventory();
                waitingForSearch.add(uuid);
                player.sendMessage(LangManager.get(uuid, "gui.blacklist.search_prompt"));
            }
            case SLOT_PREV -> open(player, page - 1);
            case SLOT_NEXT -> open(player, page + 1);
            case SLOT_BACK -> {
                player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.GUI_CLICK), 1.0f, 1.0f);
                MailManager.getInstance().mailSettingGUI.open(player);
            }
            default -> {
                if (slot < PAGE_SIZE) {
                    ItemStack clicked = e.getCurrentItem();
                    if (clicked == null || !clicked.hasItemMeta()) return;
                    String name = Objects.requireNonNull(clicked.getItemMeta()).getDisplayName().substring(2).trim();
                    if (name.isEmpty()) return;

                    OfflinePlayer target = PlayerCache.getByName(name);
                    if (target == null) return;

                    Set<UUID> blocked = new HashSet<>(MailDataManager.getInstance().getBlacklist(uuid));
                    UUID targetId = target.getUniqueId();
                    if (blocked.contains(targetId)) {
                        blocked.remove(targetId);
                        player.sendMessage(LangManager.get(uuid, "gui.blacklist.unblocked").replace("%name%", name));
                    } else {
                        blocked.add(targetId);
                        player.sendMessage(LangManager.get(uuid, "gui.blacklist.blocked").replace("%name%", name));
                    }
                    player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.GUI_CLICK), 1f, 1f);
                    MailDataManager.getInstance().setBlacklist(uuid, blocked);
                    open(player, page);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof BlacklistSelectGUI) {
            UUID uuid = e.getPlayer().getUniqueId();
            pageMap.remove(uuid);
            loadingSet.remove(uuid);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!waitingForSearch.remove(uuid)) return;

        e.setCancelled(true);
        String input = e.getMessage();
        OfflinePlayer target = PlayerCache.getByName(input);

        Bukkit.getScheduler().runTask(plugin, () -> {
            int currentPage = pageMap.getOrDefault(uuid, 0);
            if (target == null || target.getName() == null) {
                player.sendMessage(LangManager.get(uuid, "gui.blacklist.not_found").replace("%input%", input));
                open(player, currentPage);
                return;
            }

            Set<UUID> blocked = new HashSet<>(MailDataManager.getInstance().getBlacklist(uuid));
            UUID targetId = target.getUniqueId();
            if (blocked.contains(targetId)) {
                blocked.remove(targetId);
                player.sendMessage(LangManager.get(uuid, "gui.blacklist.unblocked").replace("%name%", target.getName()));
            } else {
                blocked.add(targetId);
                player.sendMessage(LangManager.get(uuid, "gui.blacklist.blocked").replace("%name%", target.getName()));
            }
            MailDataManager.getInstance().setBlacklist(uuid, blocked);
            open(player, currentPage);
        });
    }
}
