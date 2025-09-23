package com.gmail.bobason01.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigLoader {

    private static final AtomicReference<FileConfiguration> configRef = new AtomicReference<>();
    private static File configFile;

    private static final Map<String, YamlConfiguration> langRefMap = new ConcurrentHashMap<>();
    private static File langFolder;

    public static void load(Plugin plugin) {
        if (configRef.get() != null) return;

        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        configRef.set(config);

        langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) langFolder.mkdirs();

        saveLangIfMissing(plugin, "en.yml");
        saveLangIfMissing(plugin, "ko.yml");
    }

    private static void saveLangIfMissing(Plugin plugin, String filename) {
        File file = new File(plugin.getDataFolder(), "lang/" + filename);
        if (!file.exists()) {
            plugin.saveResource("lang/" + filename, false);
        }
    }

    public static ItemStack getGuiItem(String key) {
        return getGuiItem(key, "en");
    }

    public static ItemStack getGuiItem(String key, String lang) {
        FileConfiguration langConfig = getLang(lang);

        String basePath = "gui." + key;
        String name = langConfig.getString(basePath + ".name", key);
        List<String> lore = langConfig.getStringList(basePath + ".lore");
        String materialName = langConfig.getString(basePath + ".material", "PAPER");
        int customModelData = langConfig.getInt(basePath + ".custom-model-data", 0);
        boolean hideFlags = langConfig.getBoolean(basePath + ".hide-flags", false);

        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (!lore.isEmpty()) meta.setLore(color(lore));
            if (customModelData != 0) {
                meta.setCustomModelData(customModelData);
            }
            if (hideFlags) {
                meta.addItemFlags(ItemFlag.values());
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    private static FileConfiguration getLang(String lang) {
        if (langFolder == null && configFile != null) {
            langFolder = new File(configFile.getParentFile(), "lang");
            if (!langFolder.exists()) langFolder.mkdirs();
        }

        return langRefMap.computeIfAbsent(lang, l -> {
            File targetFile = new File(langFolder, l + ".yml");

            if (!targetFile.exists()) {
                targetFile = new File(langFolder, "en.yml");
                if (!targetFile.exists()) {
                    return new YamlConfiguration();
                }
            }

            return YamlConfiguration.loadConfiguration(targetFile);
        });
    }

    private static String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }

    private static List<String> color(List<String> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(ConfigLoader::color).toList();
    }
}