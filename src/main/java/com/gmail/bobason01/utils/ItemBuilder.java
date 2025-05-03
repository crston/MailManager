package com.gmail.bobason01.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ItemBuilder {

    private final ItemStack item;
    private String name;
    private List<String> lore;
    private Integer customModelData;
    private Integer damage;
    private String owner;
    private UUID skullUUID;
    private String base64;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    public ItemBuilder(ItemStack original) {
        this.item = original.clone();
    }

    public ItemBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        this.lore = lore;
        return this;
    }

    public ItemBuilder lore(String line) {
        this.lore = List.of(line);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        this.lore = Arrays.asList(lines);
        return this;
    }

    public ItemBuilder customModelData(int data) {
        this.customModelData = data;
        return this;
    }

    public ItemBuilder damage(int damage) {
        this.damage = damage;
        return this;
    }

    public ItemBuilder owner(String owner) {
        this.owner = owner;
        return this;
    }

    public ItemBuilder skullUUID(UUID uuid) {
        this.skullUUID = uuid;
        return this;
    }

    public ItemBuilder skullBase64(String base64) {
        this.base64 = base64;
        return this;
    }

    public ItemStack build() {
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            if (customModelData != null) meta.setCustomModelData(customModelData);
            item.setItemMeta(meta);
        }

        if (damage != null && item.getType().getMaxDurability() > 0) {
            item.setDurability((short) damage.intValue());
        }

        if (item.getType() == Material.PLAYER_HEAD) {
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            try {
                if (skullUUID != null) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(skullUUID);
                    assert skullMeta != null;
                    skullMeta.setOwningPlayer(offline);
                } else if (owner != null && owner.length() <= 16) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
                    assert skullMeta != null;
                    skullMeta.setOwningPlayer(offline);
                }
                // base64는 구현만 해두고 사용은 선택적으로
            } catch (Exception ignored) {
            }
            item.setItemMeta(skullMeta);
        }

        return item;
    }
}