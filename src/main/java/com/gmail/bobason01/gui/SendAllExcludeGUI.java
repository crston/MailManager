package com.gmail.bobason01.gui;

import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SendAllExcludeGUI implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_SEARCH = 45;
    private static final int SLOT_BACK = 53;
    private static final long PAGE_COOLDOWN_MS = 1000L;

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> lastClickMap = new ConcurrentHashMap<>();
    private final Set<UUID> loadingSet = ConcurrentHashMap.newKeySet();
    private final Set<UUID> waitingForSearch = ConcurrentHashMap.newKeySet();

    public SendAllExcludeGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        if (!loadingSet.add(uuid)) return;

        pageMap.put(uuid, page);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<OfflinePlayer> players = PlayerCache.getCachedPlayers().stream()
                    .filter(p -> p.getName() != null && !p.getUniqueId().equals(uuid))
                    .sorted(Comparator.comparing(OfflinePlayer::getName))
                    .toList();

            int maxPage = Math.max((players.size() - 1) / PAGE_SIZE, 0);
            int safePage = Math.min(Math.max(page, 0), maxPage);
            int start = safePage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, players.size());
            List<OfflinePlayer> subList = players.subList(start, end);
            Set<UUID> excludedSet = MailDataManager.getInstance().getExclude(uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!player.isOnline()) return;

                    Inventory inv = Bukkit.createInventory(player, 54,
                            LangManager.get(lang, "gui.exclude.title")
                                    .replace("%page%", Integer.toString(safePage + 1))
                                    .replace("%total%", Integer.toString(maxPage + 1)));

                    for (int i = 0; i < subList.size(); i++) {
                        OfflinePlayer target = subList.get(i);
                        UUID targetId = target.getUniqueId();
                        boolean isExcluded = excludedSet.contains(targetId);

                        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta meta = (SkullMeta) head.getItemMeta();
                        if (meta != null) {
                            meta.setOwningPlayer(target);
                            meta.setDisplayName((isExcluded ? "§c" : "§a") + target.getName());
                            meta.setLore(Collections.singletonList(LangManager.get(lang, isExcluded ? "gui.exclude.status.excluded" : "gui.exclude.status.included")));
                            head.setItemMeta(meta);
                            inv.setItem(i, head);
                        }
                    }

                    if (safePage > 0)
                        inv.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                                .name(LangManager.get(lang, "gui.previous"))
                                .build());
                    if (safePage < maxPage)
                        inv.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                                .name(LangManager.get(lang, "gui.next"))
                                .build());

                    inv.setItem(SLOT_SEARCH, new ItemBuilder(Material.COMPASS)
                            .name(LangManager.get(lang, "gui.exclude.search"))
                            .lore(LangManager.get(lang, "gui.exclude.search.lore"))
                            .build());

                    inv.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                            .name(LangManager.get(lang, "gui.back.name"))
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
        AtomicLong lastClick = lastClickMap.computeIfAbsent(uuid, k -> new AtomicLong(0));

        if (now - lastClick.get() < PAGE_COOLDOWN_MS) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return false;
        }

        lastClick.set(now);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String title = LangManager.get(lang, "gui.exclude.title")
                .replace("%page%", "")
                .replace("/%total%", "");
        if (!e.getView().getTitle().startsWith(title)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();

        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        int page = pageMap.getOrDefault(uuid, 0);
        Set<UUID> excluded = MailDataManager.getInstance().getExclude(uuid);

        switch (slot) {
            case SLOT_PREV -> {
                if (!hasCooldownPassed(player)) return;
                open(player, page - 1);
            }
            case SLOT_NEXT -> {
                if (!hasCooldownPassed(player)) return;
                open(player, page + 1);
            }
            case SLOT_BACK -> {
                pageMap.remove(uuid);
                loadingSet.remove(uuid);
                new MailSendAllGUI(plugin).open(player);
            }
            case SLOT_SEARCH -> {
                player.closeInventory();
                waitingForSearch.add(uuid);
                player.sendMessage(LangManager.get(lang, "gui.exclude.search.prompt"));
            }
            default -> {
                if (slot < PAGE_SIZE) {
                    ItemStack clicked = e.getInventory().getItem(slot);
                    if (clicked == null || !clicked.hasItemMeta()) return;

                    String name = Objects.requireNonNull(clicked.getItemMeta()).getDisplayName().replace("§a", "").replace("§c", "").trim();
                    OfflinePlayer target = PlayerCache.getByName(name);
                    if (target == null) return;

                    UUID targetId = target.getUniqueId();
                    if (excluded.contains(targetId)) {
                        excluded.remove(targetId);
                        player.sendMessage(LangManager.get(lang, "gui.exclude.included").replace("%name%", name));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    } else {
                        excluded.add(targetId);
                        player.sendMessage(LangManager.get(lang, "gui.exclude.excluded").replace("%name%", name));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                    }
                    MailDataManager.getInstance().setExclude(uuid, excluded);
                    open(player, page);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        String title = e.getView().getTitle();
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String baseTitle = LangManager.get(lang, "gui.exclude.title")
                .replace("%page%", "").replace("/%total%", "");
        if (!title.startsWith(baseTitle)) return;

        pageMap.remove(uuid);
        loadingSet.remove(uuid);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        if (!waitingForSearch.remove(uuid)) return;

        e.setCancelled(true);
        String input = e.getMessage();
        OfflinePlayer target = PlayerCache.getByName(input);

        Bukkit.getScheduler().runTask(plugin, () -> {
            int currentPage = pageMap.getOrDefault(uuid, 0);

            if (target == null || target.getName() == null) {
                player.sendMessage(LangManager.get(lang, "gui.exclude.notfound").replace("%input%", input));
                open(player, currentPage);
                return;
            }

            Set<UUID> excluded = MailDataManager.getInstance().getExclude(uuid);
            UUID targetId = target.getUniqueId();

            if (excluded.contains(targetId)) {
                excluded.remove(targetId);
                player.sendMessage(LangManager.get(lang, "gui.exclude.included").replace("%name%", target.getName()));
            } else {
                excluded.add(targetId);
                player.sendMessage(LangManager.get(lang, "gui.exclude.excluded").replace("%name%", target.getName()));
            }

            MailDataManager.getInstance().setExclude(uuid, excluded);
            open(player, currentPage);
        });
    }
}
