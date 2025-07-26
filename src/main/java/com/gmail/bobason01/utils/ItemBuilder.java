package com.gmail.bobason01.utils;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;

import java.util.*;

public class ItemBuilder {

    private final ItemStack item;
    private String name;
    private List<String> lore;
    private Integer customModelData;
    private Integer damage;
    private Boolean unbreakable;
    private final Set<ItemFlag> itemFlags = EnumSet.noneOf(ItemFlag.class);
    private String owner;
    private UUID skullUUID;

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder of(ItemStack item) {
        return new ItemBuilder(item);
    }

    public static ItemBuilder of(String id) {
        if (MMOItems.plugin != null) {
            try {
                for (Type type : MMOItems.plugin.getTypes().getAll()) {
                    MMOItemTemplate template = MMOItems.plugin.getTemplates().getTemplate(type, id);
                    if (template != null) {
                        MMOItem item = MMOItems.plugin.getMMOItem(type, id);
                        if (item != null) {
                            return new ItemBuilder(Objects.requireNonNull(item.newBuilder().build()));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        try {
            Material mat = Material.valueOf(id.toUpperCase());
            return new ItemBuilder(mat);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<String> getAvailableItemIds() {
        Set<String> ids = new HashSet<>();

        try {
            if (MMOItems.plugin != null) {
                for (Type type : MMOItems.plugin.getTypes().getAll()) {
                    Collection<MMOItemTemplate> templates = MMOItems.plugin.getTemplates().getTemplates(type);
                    for (MMOItemTemplate template : templates) {
                        ids.add(template.getId());
                    }
                }
            }
        } catch (Exception ignored) {}

        for (Material mat : Material.values()) {
            ids.add(mat.name());
        }

        return new ArrayList<>(ids);
    }

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
        this.lore = (lore != null) ? new ArrayList<>(lore) : null;
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder customModelData(int data) {
        this.customModelData = data;
        return this;
    }

    public ItemBuilder damage(int damage) {
        this.damage = damage;
        return this;
    }

    public ItemBuilder unbreakable() {
        this.unbreakable = true;
        return this;
    }

    public ItemBuilder owner(String name) {
        this.owner = name;
        return this;
    }

    public ItemBuilder skullUUID(UUID uuid) {
        this.skullUUID = uuid;
        return this;
    }

    public ItemBuilder flag(ItemFlag... flags) {
        Collections.addAll(this.itemFlags, flags);
        return this;
    }

    public ItemBuilder flagAll() {
        this.itemFlags.addAll(EnumSet.allOf(ItemFlag.class));
        return this;
    }

    public ItemStack build() {
        final Material type = item.getType();
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            if (customModelData != null) meta.setCustomModelData(customModelData);
            if (unbreakable != null) meta.setUnbreakable(unbreakable);
            if (!itemFlags.isEmpty()) meta.addItemFlags(itemFlags.toArray(new ItemFlag[0]));

            if (damage != null && meta instanceof Damageable dmg && type.getMaxDurability() > 0) {
                dmg.setDamage(damage);
            }

            if (type == Material.PLAYER_HEAD && meta instanceof SkullMeta skullMeta) {
                if (skullUUID != null) {
                    String name = Bukkit.getOfflinePlayer(skullUUID).getName();
                    if (name != null && name.length() <= 16) {
                        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(skullUUID));
                        meta = skullMeta;
                    }
                } else if (owner != null && owner.length() <= 16) {
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                    meta = skullMeta;
                }
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    public static List<String> getAvailableItemIdsForTab() {
        List<String> results = new ArrayList<>();

        try {
            if (MMOItems.plugin != null) {
                for (Type type : MMOItems.plugin.getTypes().getAll()) {
                    Collection<MMOItemTemplate> templates = MMOItems.plugin.getTemplates().getTemplates(type);
                    for (MMOItemTemplate template : templates) {
                        results.add("mmoitems:" + type.getId().toLowerCase() + "." + template.getId());
                    }
                }
            }
        } catch (Exception ignored) {}

        for (Material mat : Material.values()) {
            results.add(mat.name().toLowerCase());
        }

        return results;
    }
}
