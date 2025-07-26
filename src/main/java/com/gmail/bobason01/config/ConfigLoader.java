package com.gmail.bobason01.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigLoader {

    private static final AtomicReference<FileConfiguration> configRef = new AtomicReference<>();
    private static File configFile;

    /**
     * 설정 파일을 불러옵니다. 최초 한 번만 호출되어야 합니다.
     */
    public static void load(Plugin plugin) {
        if (configRef.get() != null) return;

        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        configRef.set(config);
    }

    /**
     * GUI용 아이템을 config.yml에서 로드합니다.
     * 예시:
     * gui-items:
     *   back:
     *     material: BARRIER
     *     name: "&cBack"
     *     lore:
     *       - "&7Return to previous menu"
     */
    public static ItemStack getGuiItem(String key) {
        FileConfiguration config = configRef.get();
        if (config == null) throw new IllegalStateException("Config not loaded");

        String path = "gui-items." + key;
        String materialName = config.getString(path + ".material", "BARRIER");
        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null) material = Material.BARRIER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(config.getString(path + ".name", "&fUnnamed")));
            List<String> lore = config.getStringList(path + ".lore");
            if (!lore.isEmpty()) {
                meta.setLore(color(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 컬러 코드 변환
     */
    private static String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }

    private static List<String> color(List<String> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(ConfigLoader::color).toList();
    }
}
