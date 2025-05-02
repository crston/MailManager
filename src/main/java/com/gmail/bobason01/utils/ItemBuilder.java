package com.gmail.bobason01.utils;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack base) {
        this.item = base.clone();
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null) meta.setDisplayName(name);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        if (meta != null) meta.setLore(Arrays.asList(lines));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null) meta.setLore(lines);
        return this;
    }

    public ItemBuilder flagAll() {
        if (meta != null) meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (meta != null) meta.addEnchant(enchantment, level, true);
        return this;
    }

    public ItemStack build() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }
}
