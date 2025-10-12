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
    private static final long PAGE_COOLDOWN_MS = 1000L;

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> lastClickMap = new ConcurrentHashMap<>();
    private final Set<UUID> loadingSet = ConcurrentHashMap.newKeySet();
    private final Set<UUID> waitingForSearch = ConcurrentHashMap.newKeySet();

    // 버튼은 미리 캐싱해둠
    private final ItemStack backButton;
    private final ItemStack prevButton;
    private final ItemStack nextButton;
    private final ItemStack searchButton;

    public SendAllExcludeGUI(Plugin plugin) {
        this.plugin = plugin;
        this.backButton = ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON).clone();
        this.prevButton = ConfigManager.getItem(ConfigManager.ItemType.PAGE_PREVIOUS_BUTTON).clone();
        this.nextButton = ConfigManager.getItem(ConfigManager.ItemType.PAGE_NEXT_BUTTON).clone();
        this.searchButton = ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH).clone();
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
        if (!loadingSet.add(uuid)) return;
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
            List<OfflinePlayer> subList = players.subList(start, end);
            Set<UUID> excludedSet = MailDataManager.getInstance().getExclude(uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!player.isOnline()) return;

                    String lang = LangManager.getLanguage(uuid);
                    Inventory inv = Bukkit.createInventory(this, 54,
                            LangManager.get(lang, "gui.exclude.title")
                                    .replace("%page%", Integer.toString(safePage + 1))
                                    .replace("%maxpage%", Integer.toString(maxPage + 1)));

                    for (int i = 0; i < subList.size(); i++) {
                        OfflinePlayer target = subList.get(i);
                        boolean isExcluded = excludedSet.contains(target.getUniqueId());

                        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta meta = (SkullMeta) head.getItemMeta();
                        if (meta != null) {
                            meta.setOwningPlayer(target);
                            meta.setDisplayName((isExcluded ? "§c" : "§a") + target.getName());
                            meta.setLore(LangManager.getList(lang,
                                    isExcluded ? "gui.exclude.status.excluded" : "gui.exclude.status.included"));
                            head.setItemMeta(meta);
                            inv.setItem(i, head);
                        }
                    }

                    if (safePage > 0) {
                        ItemStack prev = prevButton.clone();
                        prev.setItemMeta(new ItemBuilder(prev).name(LangManager.get(lang, "gui.previous")).build().getItemMeta());
                        inv.setItem(SLOT_PREV, prev);
                    }
                    if (safePage < maxPage) {
                        ItemStack next = nextButton.clone();
                        next.setItemMeta(new ItemBuilder(next).name(LangManager.get(lang, "gui.next")).build().getItemMeta());
                        inv.setItem(SLOT_NEXT, next);
                    }

                    ItemStack search = new ItemBuilder(searchButton.clone())
                            .name(LangManager.get(lang, "gui.search.name"))
                            .lore(LangManager.getList(lang, "gui.exclude.search.prompt"))
                            .build();
                    inv.setItem(SLOT_SEARCH, search);

                    ItemStack back = new ItemBuilder(backButton.clone())
                            .name(LangManager.get(lang, "gui.back.name"))
                            .lore(LangManager.getList(lang, "gui.back.lore"))
                            .build();
                    inv.setItem(SLOT_BACK, back);

                    player.openInventory(inv);

                } finally {
                    loadingSet.remove(uuid);
                }
            });
        });
    }

    private boolean hasCooldownPassed(Player player) {
        long now = System.currentTimeMillis();
        AtomicLong lastClick = lastClickMap.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        if (now - lastClick.get() < PAGE_COOLDOWN_MS) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
            return false;
        }
        lastClick.set(now);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof SendAllExcludeGUI) || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        UUID uuid = player.getUniqueId();
        int page = pageMap.getOrDefault(uuid, 0);
        if (!hasCooldownPassed(player)) return;

        switch (slot) {
            case SLOT_PREV -> open(player, page - 1);
            case SLOT_NEXT -> open(player, page + 1);
            case SLOT_BACK -> MailManager.getInstance().mailSendAllGUI.open(player);
            case SLOT_SEARCH -> {
                player.closeInventory();
                waitingForSearch.add(uuid);
                player.sendMessage(LangManager.get(uuid, "gui.exclude.search.prompt"));
            }
            default -> {
                if (slot < PAGE_SIZE) {
                    ItemStack clicked = e.getInventory().getItem(slot);
                    if (clicked == null || !clicked.hasItemMeta()) return;

                    String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                    OfflinePlayer target = PlayerCache.getByName(name);
                    if (target == null) return;

                    Set<UUID> excluded = new HashSet<>(MailDataManager.getInstance().getExclude(uuid));
                    UUID targetId = target.getUniqueId();
                    if (excluded.remove(targetId)) {
                        player.sendMessage(LangManager.get(uuid, "gui.exclude.included").replace("%name%", name));
                    } else {
                        excluded.add(targetId);
                        player.sendMessage(LangManager.get(uuid, "gui.exclude.excluded").replace("%name%", name));
                    }
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                    MailDataManager.getInstance().setExclude(uuid, excluded);
                    open(player, page);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof SendAllExcludeGUI) {
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
                player.sendMessage(LangManager.get(uuid, "gui.exclude.notfound").replace("%input%", input));
                open(player, currentPage);
                return;
            }

            Set<UUID> excluded = new HashSet<>(MailDataManager.getInstance().getExclude(uuid));
            UUID targetId = target.getUniqueId();
            if (excluded.remove(targetId)) {
                player.sendMessage(LangManager.get(uuid, "gui.exclude.included").replace("%name%", target.getName()));
            } else {
                excluded.add(targetId);
                player.sendMessage(LangManager.get(uuid, "gui.exclude.excluded").replace("%name%", target.getName()));
            }
            MailDataManager.getInstance().setExclude(uuid, excluded);
            open(player, currentPage);
        });
    }
}
