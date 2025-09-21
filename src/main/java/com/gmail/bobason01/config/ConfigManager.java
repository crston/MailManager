package com.gmail.bobason01.config;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

public class ConfigManager {

    private static JavaPlugin plugin;
    private static FileConfiguration config;

    private static final Map<ItemType, Material> itemCache = new EnumMap<>(ItemType.class);
    private static final Map<SoundType, Sound> soundCache = new EnumMap<>(SoundType.class);

    public enum ItemType {
        MAIL_GUI_SEND_BUTTON(Material.WRITABLE_BOOK),
        MAIL_GUI_SETTING_BUTTON(Material.COMPARATOR),
        PAGE_NEXT_BUTTON(Material.ARROW),
        PAGE_PREVIOUS_BUTTON(Material.ARROW),
        BACK_BUTTON(Material.BARRIER),
        SETTING_GUI_NOTIFY_ON(Material.LIME_DYE),
        SETTING_GUI_NOTIFY_OFF(Material.GRAY_DYE),
        SETTING_GUI_BLACKLIST(Material.BARRIER),
        SETTING_GUI_LANGUAGE(Material.BOOK),
        LANGUAGE_GUI_ITEM(Material.PAPER),
        SEND_GUI_TIME(Material.CLOCK),
        SEND_GUI_TARGET(Material.PLAYER_HEAD),
        SEND_GUI_CONFIRM(Material.GREEN_WOOL),
        SEND_ALL_GUI_EXCLUDE(Material.BARRIER),
        SEND_ALL_GUI_CONFIRM(Material.GREEN_WOOL),
        TIME_GUI_UNIT(Material.CLOCK),
        TIME_GUI_PERMANENT(Material.BARRIER),
        TIME_GUI_CHAT_INPUT(Material.WRITABLE_BOOK),
        TIME_GUI_CONFIRM(Material.LIME_CONCRETE),
        BLACKLIST_EXCLUDE_SEARCH(Material.COMPASS),
        DELETE_GUI_CONFIRM_BUTTON(Material.RED_WOOL),
        DELETE_GUI_CANCEL_BUTTON(Material.GREEN_WOOL);

        private final Material defaultMaterial;
        ItemType(Material defaultMaterial) { this.defaultMaterial = defaultMaterial; }
        public Material getDefaultMaterial() { return defaultMaterial; }
    }

    public enum SoundType {
        GUI_CLICK(Sound.UI_BUTTON_CLICK),
        GUI_CLICK_FAIL(Sound.BLOCK_NOTE_BLOCK_BASS),
        GUI_PAGE_TURN(Sound.ITEM_BOOK_PAGE_TURN),
        MAIL_CLAIM_SUCCESS(Sound.ENTITY_ITEM_PICKUP),
        MAIL_CLAIM_FAIL(Sound.ENTITY_VILLAGER_NO),
        MAIL_DELETE_SUCCESS(Sound.BLOCK_ANVIL_LAND),
        MAIL_SEND_SUCCESS(Sound.ENTITY_PLAYER_LEVELUP),
        MAIL_RECEIVE_NOTIFICATION(Sound.UI_TOAST_IN),
        MAIL_REMINDER(Sound.ENTITY_EXPERIENCE_ORB_PICKUP),
        ACTION_SETTING_CHANGE(Sound.ENTITY_EXPERIENCE_ORB_PICKUP),
        ACTION_SELECTION_COMPLETE(Sound.BLOCK_NOTE_BLOCK_PLING);

        private final Sound defaultSound;
        SoundType(Sound defaultSound) { this.defaultSound = defaultSound; }
        public Sound getDefaultSound() { return defaultSound; }
    }

    public static void load(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        plugin.saveDefaultConfig();
        reload();
    }

    public static void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        itemCache.clear();
        soundCache.clear();
        preload();
    }

    private static void preload() {
        for (ItemType type : ItemType.values()) {
            String path = "items." + type.name().toLowerCase().replace('_', '.');
            String materialName = config.getString(path, type.getDefaultMaterial().name());
            try {
                itemCache.put(type, Material.valueOf(materialName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material name in config.yml at '" + path + "': " + materialName);
                itemCache.put(type, type.getDefaultMaterial());
            }
        }

        for (SoundType type : SoundType.values()) {
            String path = "sounds." + type.name().toLowerCase().replace('_', '.');
            String soundName = config.getString(path, type.getDefaultSound().name());
            try {
                soundCache.put(type, Sound.valueOf(soundName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound name in config.yml at '" + path + "': " + soundName);
                soundCache.put(type, type.getDefaultSound());
            }
        }
    }

    public static Material getItem(ItemType type) {
        return itemCache.getOrDefault(type, type.getDefaultMaterial());
    }

    public static Sound getSound(SoundType type) {
        return soundCache.getOrDefault(type, type.getDefaultSound());
    }
}