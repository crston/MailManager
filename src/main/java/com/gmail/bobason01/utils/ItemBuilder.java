package com.gmail.bobason01.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        this.item = itemStack.clone();
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null) meta.setDisplayName(name);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lore) {
        if (meta != null) {
            List<String> current = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            current.addAll(lore);
            meta.setLore(current);
        }
        return this;
    }

    public ItemBuilder clearLore() {
        if (meta != null) meta.setLore(null);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null) meta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder customModelData(Integer data) {
        if (meta != null && data != null) meta.setCustomModelData(data);
        return this;
    }

    public ItemStack build() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }
}