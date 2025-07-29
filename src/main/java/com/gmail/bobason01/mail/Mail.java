package com.gmail.bobason01.mail;

import com.gmail.bobason01.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Mail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<UUID, String> nameCache = new HashMap<>();

    private final UUID mailId;
    private final UUID sender;
    private final UUID receiver;
    private final ItemStack item;
    private final LocalDateTime sentAt;
    private final LocalDateTime expireAt;

    // 캐시 필드는 직렬화 제외
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

    public boolean isRead() {
        return false;
    }

    public ItemStack toItemStack() {
        return generateDisplayItem(receiver);
    }

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

    private ItemStack buildDisplayItem(UUID viewer) {
        ItemStack display = item.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            String lang = LangManager.getLanguage(viewer);

            if (cachedSenderName == null) {
                cachedSenderName = resolveSenderName(lang);
            }

            meta.setLore(buildLore(lang));
            display.setItemMeta(meta);
        }
        return display;
    }

    private List<String> buildLore(String lang) {
        List<String> lore = new ArrayList<>();
        lore.add("§7" + LangManager.get(lang, "mail.lore.sender").replace("%sender%", cachedSenderName));
        lore.add("§7" + LangManager.get(lang, "mail.lore.sent").replace("%time%", FORMATTER.format(sentAt)));

        if (expireAt != null) {
            lore.add("§7" + LangManager.get(lang, "mail.lore.expire").replace("%time%", FORMATTER.format(expireAt)));
            if (isExpired()) {
                lore.add("§c" + LangManager.get(lang, "mail.lore.expired"));
            }
        }
        return lore;
    }

    private String resolveSenderName(String lang) {
        if (sender == null) return LangManager.get(lang, "mail.unknown");

        return nameCache.computeIfAbsent(sender, id -> {
            OfflinePlayer p = Bukkit.getOfflinePlayer(id);
            String name = p.getName();
            return (name != null) ? name : LangManager.get(lang, "mail.unknown");
        });
    }

    public void onClick(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);

        if (!isExpired()) {
            Map<Integer, ItemStack> failed = player.getInventory().addItem(item);
            if (!failed.isEmpty()) {
                player.sendMessage(LangManager.get(lang, "mail.receive.failed"));
                return;
            }
            player.sendMessage(LangManager.get(lang, "mail.receive.success"));
        } else {
            player.sendMessage(LangManager.get(lang, "mail.expired"));
        }

        MailDataManager.getInstance().removeMail(receiver, this);
    }

    public void give(Player player) {
        if (player != null && player.isOnline() && item != null) {
            player.getInventory().addItem(item.clone());
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Mail m && mailId.equals(m.mailId));
    }

    @Override
    public int hashCode() {
        return mailId.hashCode();
    }
}
