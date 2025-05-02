package com.gmail.bobason01.utils;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ConfigLoader {

    private static Plugin plugin;

    public static void load(Plugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static boolean isNotifyDefaultEnabled(UUID uuid, Set<UUID> notifySet) {
        return notifySet.contains(uuid) || (
                !notifySet.contains(uuid) && plugin.getConfig().getBoolean("notify.default-enabled", true)
        );
    }

    public static ItemStack getGuiItem(String key) {
        FileConfiguration config = plugin.getConfig();
        String path = "gui-items." + key;

        Material material = Material.matchMaterial(config.getString(path + ".material", "BARRIER"));
        if (material == null) material = Material.BARRIER;

        String name = config.getString(path + ".name", "§c알 수 없는 항목: " + key);
        List<String> lore = config.getStringList(path + ".lore");
        int customModelData = config.getInt(path + ".custom-model-data", 0);
        int damage = config.getInt(path + ".damage", -1);
        String owner = config.getString(path + ".skull-owner", null);
        String uuid = config.getString(path + ".skull-uuid", null);
        String base64 = config.getString(path + ".skull-base64", null);

        ItemBuilder builder = new ItemBuilder(material).name(name);
        if (lore != null && !lore.isEmpty()) builder.lore(lore);

        if (customModelData > 0) builder.customModelData(customModelData);
        if (damage >= 0) builder.damage(damage);
        if (owner != null) builder.owner(owner);
        if (uuid != null) builder.skullUUID(UUID.fromString(uuid));
        if (base64 != null) builder.skullBase64(base64);

        return builder.build();
    }
}
