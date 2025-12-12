package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
    private static final int SLOT_SEARCH = 49; // 검색 버튼 위치
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_BACK = 53;
    private static final long PAGE_COOLDOWN_MS = 1000L;

    private final Plugin plugin;
    private final Map<UUID, Integer> pageMap = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> cooldownMap = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Set<UUID> searchMode = ConcurrentHashMap.newKeySet(); // 채팅 검색 모드 상태
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
        // 검색 모드 중이면 GUI 열지 않음 (채팅 입력 대기)
        if (searchMode.contains(uuid)) return;

        if (!loading.add(uuid)) return;
        pageMap.put(uuid, page);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // 로컬 캐시된 플레이어 목록 가져오기 (GUI 표시용)
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

                    // 페이지 버튼
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

                    // 검색 버튼 (ConfigManager에 아이템이 없으면 나침반 사용)
                    ItemStack searchItem = ConfigManager.getItem(ConfigManager.ItemType.BLACKLIST_EXCLUDE_SEARCH); // 기존 아이템 재활용 혹은 신규 추가
                    if (searchItem.getType() == Material.AIR) searchItem = new ItemStack(Material.COMPASS);

                    inv.setItem(SLOT_SEARCH, new ItemBuilder(searchItem)
                            .name(LangManager.get(lang, "gui.search.name")) // lang 파일에 gui.search.name 추가 필요 (예: "&e플레이어 검색")
                            .lore(LangManager.getList(lang, "gui.search.lore")) // (예: "&7클릭하여 채팅으로 이름을 입력하세요.")
                            .build());

                    // 뒤로가기 버튼
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
            case SLOT_SEARCH -> {
                // 검색 모드 진입
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                player.closeInventory();
                searchMode.add(uuid);

                String lang = LangManager.getLanguage(uuid);
                player.sendMessage(LangManager.get(lang, "gui.search.start")); // (예: "&a채팅창에 검색할 플레이어 이름을 입력하세요. ('cancel' 입력 시 취소)")
            }
            case SLOT_BACK -> {
                ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
                MailManager.getInstance().mailSendGUI.open(player);
            }
            default -> {
                // 헤드 클릭 시 선택 처리
                if (clicked.getType() != Material.PLAYER_HEAD) return;
                String name = ChatColor.stripColor(Objects.requireNonNull(clicked.getItemMeta()).getDisplayName());
                OfflinePlayer target = PlayerCache.getByName(name);

                if (target == null || target.getUniqueId().equals(uuid)) {
                    player.sendMessage(LangManager.get(uuid, "gui.target.invalid"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                    return;
                }

                selectTarget(player, target);
            }
        }
    }

    // 채팅 입력 처리 (검색)
    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!searchMode.contains(uuid)) return;

        e.setCancelled(true); // 채팅 막음
        searchMode.remove(uuid); // 검색 모드 해제

        String input = e.getMessage().trim();
        String lang = LangManager.getLanguage(uuid);

        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("취소")) {
            player.sendMessage(LangManager.get(lang, "gui.search.cancelled"));
            Bukkit.getScheduler().runTask(plugin, () -> open(player, pageMap.getOrDefault(uuid, 0)));
            return;
        }

        player.sendMessage(LangManager.get(lang, "gui.search.searching").replace("%name%", input));

        // [중요] DB에서 글로벌 검색 (비동기)
        MailDataManager.getInstance().getGlobalUUID(input).thenAccept(targetUUID -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (targetUUID == null) {
                    player.sendMessage(LangManager.get(lang, "cmd.player.notfound").replace("%name%", input));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                    // 실패 시 다시 GUI 오픈
                    open(player, pageMap.getOrDefault(uuid, 0));
                    return;
                }

                if (targetUUID.equals(uuid)) {
                    player.sendMessage(LangManager.get(lang, "gui.target.self"));
                    ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK_FAIL);
                    open(player, pageMap.getOrDefault(uuid, 0));
                    return;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
                selectTarget(player, target);
            });
        });
    }

    private void selectTarget(Player player, OfflinePlayer target) {
        UUID uuid = player.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        MailService.setTarget(uuid, target);

        String lang = LangManager.getLanguage(uuid);
        player.sendMessage(LangManager.get(lang, "gui.target.selected").replace("%target%", targetName));
        ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SETTING_CHANGE);

        MailManager.getInstance().mailSendGUI.open(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        searchMode.remove(e.getPlayer().getUniqueId());
        pageMap.remove(e.getPlayer().getUniqueId());
        loading.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MailTargetSelectGUI) {
            UUID uuid = e.getPlayer().getUniqueId();
            // 검색 모드가 아닐 때만 페이지 정보 삭제 (검색 모드 진입 시에는 인벤토리가 닫혀야 하므로)
            if (!searchMode.contains(uuid)) {
                pageMap.remove(uuid);
            }
            loading.remove(uuid);
        }
    }
}