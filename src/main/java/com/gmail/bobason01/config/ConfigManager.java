package com.gmail.bobason01.config;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.util.List;

public class ConfigManager {

    private static JavaPlugin plugin;
    private static FileConfiguration config;

    private static final ItemStack[] itemCache = new ItemStack[ItemType.values().length];
    private static final SoundData[] soundCache = new SoundData[SoundType.values().length];

    public static class SoundData {
        private final Sound soundEnum;
        private final String soundString;
        private final float volume;
        private final float pitch;

        public SoundData(Sound soundEnum, String soundString, float volume, float pitch) {
            this.soundEnum = soundEnum;
            this.soundString = soundString;
            this.volume = volume;
            this.pitch = pitch;
        }

        public boolean isEnum() {
            return soundEnum != null;
        }

        public Sound getSoundEnum() {
            return soundEnum;
        }

        public String getSoundString() {
            return soundString;
        }

        public float getVolume() {
            return volume;
        }

        public float getPitch() {
            return pitch;
        }
    }

    public enum ItemType {
        MAIL_GUI_SEND_BUTTON("mail.gui.send-button", Material.WRITABLE_BOOK),
        MAIL_GUI_SETTING_BUTTON("mail.gui.setting-button", Material.COMPARATOR),
        PAGE_NEXT_BUTTON("page.next-button", Material.ARROW),
        PAGE_PREVIOUS_BUTTON("page.previous-button", Material.ARROW),
        BACK_BUTTON("back-button", Material.BARRIER),
        MAIL_GUI_CLAIM_BUTTON("mail.gui.claim-button", Material.CHEST),
        MAIL_GUI_DELETE_BUTTON("mail.gui.delete-button", Material.RED_WOOL),
        MAIL_GUI_SELECT_BUTTON("mail.gui.select-button", Material.YELLOW_DYE),
        MAIL_GUI_CLAIM_SELECTED_BUTTON("mail.gui.claim-selected-button", Material.ENDER_CHEST),
        MAIL_GUI_DELETE_SELECTED_BUTTON("mail.gui.delete-selected-button", Material.RED_CONCRETE),
        SETTING_GUI_NOTIFY_ON("setting.notify-on", Material.LIME_DYE),
        SETTING_GUI_NOTIFY_OFF("setting.notify-off", Material.GRAY_DYE),
        SETTING_GUI_BLACKLIST("setting.blacklist", Material.BARRIER),
        SETTING_GUI_LANGUAGE("setting.language", Material.BOOK),
        SETTING_GUI_MULTI_SELECT("setting.multi-select", Material.SUNFLOWER),

        LANGUAGE_GUI_ITEM("language.gui-item", Material.PAPER),
        SEND_GUI_TIME("send.gui.time", Material.CLOCK),
        SEND_GUI_TARGET("send.gui.target", Material.PLAYER_HEAD),
        SEND_GUI_CONFIRM("send.gui.confirm", Material.GREEN_WOOL),
        SEND_ALL_GUI_TIME("sendall.gui.time", Material.CLOCK),
        SEND_ALL_GUI_EXCLUDE("sendall.gui.exclude", Material.BARRIER),
        SEND_ALL_GUI_ATTACH("sendall.gui.attach", Material.CHEST),
        SEND_ALL_GUI_CONFIRM("sendall.gui.confirm", Material.GREEN_WOOL),
        TIME_GUI_UNIT("time.gui.unit", Material.CLOCK),
        TIME_GUI_PERMANENT("time.gui.permanent", Material.BARRIER),
        TIME_GUI_CHAT_INPUT("time.gui.chat-input", Material.WRITABLE_BOOK),
        TIME_GUI_CONFIRM("time.gui.confirm", Material.LIME_CONCRETE),
        BLACKLIST_EXCLUDE_SEARCH("blacklist.exclude-search", Material.COMPASS),
        DELETE_GUI_CONFIRM_BUTTON("delete.gui.confirm-button", Material.RED_WOOL),
        DELETE_GUI_CANCEL_BUTTON("delete.gui.cancel-button", Material.GREEN_WOOL);

        private final String path;
        private final Material defaultMaterial;

        ItemType(String path, Material defaultMaterial) {
            this.path = path;
            this.defaultMaterial = defaultMaterial;
        }

        public String getPath() {
            return path;
        }

        public Material getDefaultMaterial() {
            return defaultMaterial;
        }
    }

    public enum SoundType {
        GUI_CLICK("gui.click", "UI_BUTTON_CLICK"),
        GUI_CLICK_FAIL("gui.click-fail", "BLOCK_NOTE_BLOCK_BASS"),
        GUI_PAGE_TURN("gui.page-turn", "ITEM_BOOK_PAGE_TURN"),
        MAIL_CLAIM_SUCCESS("mail.claim-success", "ENTITY_ITEM_PICKUP"),
        MAIL_CLAIM_FAIL("mail.claim-fail", "ENTITY_VILLAGER_NO"),
        MAIL_DELETE_SUCCESS("mail.delete-success", "BLOCK_ANVIL_LAND"),
        MAIL_SEND_SUCCESS("mail.send-success", "ENTITY_PLAYER_LEVELUP"),
        MAIL_RECEIVE_NOTIFICATION("mail.receive-notification", "UI_TOAST_IN"),
        MAIL_REMINDER("mail.reminder", "ENTITY_EXPERIENCE_ORB_PICKUP"),
        ACTION_SETTING_CHANGE("action.setting-change", "ENTITY_EXPERIENCE_ORB_PICKUP"),
        ACTION_SELECTION_COMPLETE("action.selection-complete", "BLOCK_NOTE_BLOCK_PLING");

        private final String path;
        private final String defaultSound;

        SoundType(String path, String defaultSound) {
            this.path = path;
            this.defaultSound = defaultSound;
        }

        public String getPath() {
            return path;
        }

        public String getDefaultSound() {
            return defaultSound;
        }
    }

    public static void load(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        plugin.saveDefaultConfig();
        reload();
    }

    public static void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        preload();
    }

    private static void preload() {
        for (ItemType type : ItemType.values()) {
            String path = "items." + type.getPath();
            String materialName = config.getString(path + ".material", type.getDefaultMaterial().name());
            int customModelData = config.getInt(path + ".custom-model-data", 0);
            int damage = config.getInt(path + ".damage", 0);
            boolean hideFlags = config.getBoolean(path + ".hide-flags", false);
            boolean unbreakable = config.getBoolean(path + ".unbreakable", false);
            List<String> flagList = config.getStringList(path + ".flags");
            ItemStack item;
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    if (customModelData != 0) meta.setCustomModelData(customModelData);
                    if (meta instanceof Damageable d && damage > 0) d.setDamage(damage);
                    if (unbreakable) meta.setUnbreakable(true);
                    if (hideFlags) meta.addItemFlags(ItemFlag.values());
                    if (!flagList.isEmpty()) {
                        for (String f : flagList) {
                            try {
                                meta.addItemFlags(ItemFlag.valueOf(f.toUpperCase()));
                            } catch (Exception ignored) {}
                        }
                    }
                    item.setItemMeta(meta);
                }
            } catch (Exception e) {
                item = new ItemStack(type.getDefaultMaterial());
            }
            itemCache[type.ordinal()] = item;
        }

        for (SoundType type : SoundType.values()) {
            String path = "sounds." + type.getPath();
            String soundName = config.getString(path + ".name", type.getDefaultSound());
            float volume = (float) config.getDouble(path + ".volume", 1.0);
            float pitch = (float) config.getDouble(path + ".pitch", 1.0);
            Sound enumSound = null;
            try {
                enumSound = Sound.valueOf(soundName.toUpperCase());
            } catch (Exception ignored) {}
            soundCache[type.ordinal()] = new SoundData(enumSound, soundName, volume, pitch);
        }
    }

    public static ItemStack getItem(ItemType type) {
        return itemCache[type.ordinal()].clone();
    }

    public static SoundData getSoundData(SoundType type) {
        return soundCache[type.ordinal()];
    }

    public static void playSound(Player player, SoundType type) {
        SoundData data = soundCache[type.ordinal()];
        if (data.isEnum()) {
            player.playSound(player.getLocation(), data.getSoundEnum(), data.getVolume(), data.getPitch());
        } else {
            player.playSound(player.getLocation(), data.getSoundString(), data.getVolume(), data.getPitch());
        }
    }
}
