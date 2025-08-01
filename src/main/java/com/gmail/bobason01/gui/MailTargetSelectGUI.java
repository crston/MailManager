package com.gmail.bobason01.gui;

import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailService;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MailTargetSelectGUI implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_BACK = 53;
    private static final long PAGE_COOLDOWN_MS = 1000L;

    private final Plugin plugin;
    private final Map<UUID, Integer> playerPageMap = new HashMap<>();
    private final Map<UUID, AtomicLong> lastPageClickMap = new ConcurrentHashMap<>();
    private final Set<UUID> loadingSet = ConcurrentHashMap.newKeySet();

    public MailTargetSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        final int targetPage = Math.max(0, page);

        UUID uuid = player.getUniqueId();
        AtomicLong lastClick = lastPageClickMap.computeIfAbsent(uuid, k -> new AtomicLong(0));
        long now = System.currentTimeMillis();
        if (now - lastClick.get() < PAGE_COOLDOWN_MS) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }
        lastClick.set(now);

        if (!loadingSet.add(uuid)) return;
        playerPageMap.put(uuid, targetPage);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<OfflinePlayer> allPlayers = PlayerCache.getCachedPlayers().stream()
                    .filter(p -> p.getName() != null && !p.getUniqueId().equals(uuid))
                    .sorted(Comparator.comparing(OfflinePlayer::getName))
                    .toList();

            int maxPage = Math.max((allPlayers.size() - 1) / PAGE_SIZE, 0);
            int safePage = Math.min(targetPage, maxPage);
            int start = safePage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, allPlayers.size());

            if (start >= allPlayers.size() || start < 0 || end < 0 || end > allPlayers.size()) {
                loadingSet.remove(uuid);
                return;
            }

            List<OfflinePlayer> pagePlayers = allPlayers.subList(start, end);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || playerPageMap.getOrDefault(uuid, -1) != safePage) {
                    loadingSet.remove(uuid);
                    return;
                }

                String lang = LangManager.getLanguage(uuid);
                String title = LangManager.get(lang, "gui.target.title")
                        .replace("%page%", String.valueOf(safePage + 1))
                        .replace("%total%", String.valueOf(maxPage + 1));

                Inventory inv = Bukkit.createInventory(player, 54, title);

                for (int i = 0; i < pagePlayers.size(); i++) {
                    OfflinePlayer target = pagePlayers.get(i);
                    String name = target.getName();
                    if (name == null || name.isEmpty()) continue;

                    ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta) skull.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName("§e" + name);
                        meta.setOwningPlayer(target);
                        skull.setItemMeta(meta);
                        inv.setItem(i, skull);
                    }
                }

                if (safePage < maxPage) {
                    inv.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                            .name("§a" + LangManager.get(lang, "gui.next"))
                            .build());
                }

                if (safePage > 0) {
                    inv.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                            .name("§a" + LangManager.get(lang, "gui.previous"))
                            .build());
                }

                inv.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                        .name("§c" + LangManager.get(lang, "gui.back.name"))
                        .lore(LangManager.get(lang, "gui.back.lore"))
                        .build());

                player.openInventory(inv);
                loadingSet.remove(uuid);
            });
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String titlePrefix = LangManager.get(lang, "gui.target.title")
                .replace("%page%", "")
                .replace("%total%", "");

        if (!e.getView().getTitle().startsWith(titlePrefix)) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int slot = e.getRawSlot();
        int currentPage = playerPageMap.getOrDefault(uuid, 0);

        switch (slot) {
            case SLOT_NEXT -> open(player, currentPage + 1);
            case SLOT_PREV -> open(player, currentPage - 1);
            case SLOT_BACK -> {
                playerPageMap.remove(uuid);
                loadingSet.remove(uuid);
                new MailSendGUI(plugin).open(player);
            }
            default -> {
                if (!clicked.getType().equals(Material.PLAYER_HEAD)) return;
                String name = Objects.requireNonNull(clicked.getItemMeta()).getDisplayName().replace("§e", "").trim();
                if (name.isEmpty()) return;

                OfflinePlayer target = PlayerCache.getByName(name);
                if (target == null || target.getUniqueId().equals(uuid)) {
                    player.sendMessage(LangManager.get(uuid, "gui.target.invalid"));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
                    return;
                }

                playerPageMap.remove(uuid);
                loadingSet.remove(uuid);
                MailService.setTarget(uuid, target);
                player.sendMessage(LangManager.get(uuid, "gui.target.selected").replace("%target%", name));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                new MailSendGUI(plugin).open(player);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String titlePrefix = LangManager.get(lang, "gui.target.title")
                .replace("%page%", "")
                .replace("%total%", "");

        if (!e.getView().getTitle().startsWith(titlePrefix)) return;

        playerPageMap.remove(uuid);
        loadingSet.remove(uuid);
    }
}
