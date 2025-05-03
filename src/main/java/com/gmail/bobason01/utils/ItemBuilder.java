package com.gmail.bobason01.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import java.util.*;

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

            // Damage (only if meta supports it)
            if (damage != null && meta instanceof Damageable damageable && item.getType().getMaxDurability() > 0) {
                damageable.setDamage(damage);
            }

            item.setItemMeta(meta);
        }

        // Handle Skull
        if (item.getType() == Material.PLAYER_HEAD) {
            ItemMeta rawMeta = item.getItemMeta();
            if (rawMeta instanceof SkullMeta skullMeta) {
                if (skullUUID != null) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(skullUUID);
                    skullMeta.setOwningPlayer(offline);
                } else if (owner != null && owner.length() <= 16) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
                    skullMeta.setOwningPlayer(offline);
                }
                // TODO: base64 texture 지원 시 구현
                item.setItemMeta(skullMeta);
            }
        }

        return item;
    }
}
