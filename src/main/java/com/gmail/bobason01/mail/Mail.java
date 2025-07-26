package com.gmail.bobason01.mail;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Mail {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<UUID, String> nameCache = new HashMap<>();

    private final UUID mailId;
    private final UUID sender;
    private final UUID receiver;
    private final ItemStack item;
    private final LocalDateTime sentAt;
    private final LocalDateTime expireAt;

    // 캐싱된 표시용 아이템 (최적화)
    private transient ItemStack cachedDisplay;
    private transient int cachedItemHash = -1;
    private transient String cachedSenderName;

    public Mail(UUID sender, UUID receiver, ItemStack item, LocalDateTime sentAt, LocalDateTime expireAt) {
        this(UUID.randomUUID(), sender, receiver, item, sentAt, expireAt);
    }

    public Mail(UUID mailId, UUID sender, UUID receiver, ItemStack item, LocalDateTime sentAt, LocalDateTime expireAt) {
        this.mailId = Objects.requireNonNull(mailId);
        this.sender = sender;
        this.receiver = receiver;
        this.item = item != null ? item.clone() : null;
        this.sentAt = sentAt;
        this.expireAt = expireAt;
    }

    public UUID getMailId() {
        return mailId;
    }

    public UUID getSender() {
        return sender;
    }

    public UUID getReceiver() {
        return receiver;
    }

    public ItemStack getItem() {
        return item != null ? item.clone() : null;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public boolean isExpired() {
        return expireAt != null && LocalDateTime.now().isAfter(expireAt);
    }

    // 현재는 항상 false (추후 '읽음' 처리 가능)
    public boolean isRead() {
        return false;
    }

    // 받는 사람 기준 표시용 아이템 생성
    public ItemStack toItemStack() {
        return generateDisplayItem(receiver);
    }

    // 지정된 플레이어 기준 표시용 아이템 생성
    public ItemStack toItemStack(Player viewer) {
        return generateDisplayItem(viewer.getUniqueId());
    }

    private ItemStack generateDisplayItem(UUID viewer) {
        if (item == null) return null;

        int currentHash = computeItemHash(item);
        if (cachedDisplay == null || cachedItemHash != currentHash) {
            cachedDisplay = buildDisplayItem(viewer);
            cachedItemHash = currentHash;
        }
        return cachedDisplay;
    }

    // 아이템 변화를 감지하기 위한 해시 계산
    private int computeItemHash(ItemStack item) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        return Objects.hash(
                item.getType(),
                item.getAmount(),
                meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "",
                meta != null && meta.hasLore() ? meta.getLore() : Collections.emptyList()
        );
    }

    // 우편 표시용 아이템 생성 (이름, 시간 등 표시 추가)
    private ItemStack buildDisplayItem(UUID viewer) {
        ItemStack display = item.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            if (cachedSenderName == null) {
                cachedSenderName = resolveSenderName();
            }

            meta.setLore(buildLore());
            display.setItemMeta(meta);
        }
        return display;
    }

    // 우편 설명 (lore) 구성
    private List<String> buildLore() {
        List<String> lore = new ArrayList<>();
        lore.add("§7보낸 사람: §f" + cachedSenderName);
        lore.add("§7보낸 시각: §f" + FORMATTER.format(sentAt));

        if (expireAt != null) {
            lore.add("§7만료 시각: §f" + FORMATTER.format(expireAt));
            if (isExpired()) {
                lore.add("§c[만료됨]");
            }
        }

        return lore;
    }

    // 발신자 이름 조회 및 캐시
    private String resolveSenderName() {
        if (sender == null) return "알 수 없음";
        return nameCache.computeIfAbsent(sender, id -> {
            OfflinePlayer p = Bukkit.getOfflinePlayer(id);
            return p.getName() != null ? p.getName() : "알 수 없음";
        });
    }

    /**
     * GUI에서 클릭 시 실행되는 로직 (아이템 수령 처리)
     */
    public void onClick(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        if (!isExpired()) {
            Map<Integer, ItemStack> failed = player.getInventory().addItem(item);
            if (!failed.isEmpty()) {
                player.sendMessage("§c인벤토리에 공간이 부족합니다.");
                return;
            }
            player.sendMessage("§a우편에서 아이템을 수령했습니다.");
        } else {
            player.sendMessage("§c이 우편은 이미 만료되었습니다.");
        }

        MailDataManager.getInstance().removeMail(receiver, this);
    }

    // 수동 지급 (ex: 로그인 보상 등)
    public void give(Player player) {
        if (player != null && player.isOnline() && item != null) {
            player.getInventory().addItem(item.clone());
        }
    }

    // 메일 ID 기반 equals & hashCode
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Mail m && mailId.equals(m.mailId));
    }

    @Override
    public int hashCode() {
        return mailId.hashCode();
    }
}
