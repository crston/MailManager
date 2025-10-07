package com.gmail.bobason01.config;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private static JavaPlugin plugin;
    private static FileConfiguration config;

    private static final Map<ItemType, ItemStack> itemCache = new EnumMap<>(ItemType.class);
    private static final Map<SoundType, Sound> soundCache = new EnumMap<>(SoundType.class);

    public enum ItemType {
        MAIL_GUI_SEND_BUTTON("mail.gui.send-button", Material.WRITABLE_BOOK),
        MAIL_GUI_SETTING_BUTTON("mail.gui.setting-button", Material.COMPARATOR),
        PAGE_NEXT_BUTTON("page.next-button", Material.ARROW),
        PAGE_PREVIOUS_BUTTON("page.previous-button", Material.ARROW),
        BACK_BUTTON("back-button", Material.BARRIER),
        SETTING_GUI_NOTIFY_ON("setting.notify-on", Material.LIME_DYE),
        SETTING_GUI_NOTIFY_OFF("setting.notify-off", Material.GRAY_DYE),
        SETTING_GUI_BLACKLIST("setting.blacklist", Material.BARRIER),
        SETTING_GUI_LANGUAGE("setting.language", Material.BOOK),
        LANGUAGE_GUI_ITEM("language.gui-item", Material.PAPER),
        SEND_GUI_TIME("send.gui.time", Material.CLOCK),
        SEND_GUI_TARGET("send.gui.target", Material.PLAYER_HEAD),
        SEND_GUI_CONFIRM("send.gui.confirm", Material.GREEN_WOOL),
        SEND_ALL_GUI_TIME("sendall.gui.time", Material.CLOCK),
        SEND_ALL_GUI_EXCLUDE("sendall.gui.exclude", Material.BARRIER),
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
        GUI_CLICK("gui.click", Sound.UI_BUTTON_CLICK),
        GUI_CLICK_FAIL("gui.click-fail", Sound.BLOCK_NOTE_BLOCK_BASS),
        GUI_PAGE_TURN("gui.page-turn", Sound.ITEM_BOOK_PAGE_TURN),
        MAIL_CLAIM_SUCCESS("mail.claim-success", Sound.ENTITY_ITEM_PICKUP),
        MAIL_CLAIM_FAIL("mail.claim-fail", Sound.ENTITY_VILLAGER_NO),
        MAIL_DELETE_SUCCESS("mail.delete-success", Sound.BLOCK_ANVIL_LAND),
        MAIL_SEND_SUCCESS("mail.send-success", Sound.ENTITY_PLAYER_LEVELUP),
        MAIL_RECEIVE_NOTIFICATION("mail.receive-notification", Sound.UI_TOAST_IN),
        MAIL_REMINDER("mail.reminder", Sound.ENTITY_EXPERIENCE_ORB_PICKUP),
        ACTION_SETTING_CHANGE("action.setting-change", Sound.ENTITY_EXPERIENCE_ORB_PICKUP),
        ACTION_SELECTION_COMPLETE("action.selection-complete", Sound.BLOCK_NOTE_BLOCK_PLING);

        private final String path;
        private final Sound defaultSound;

        SoundType(String path, Sound defaultSound) {
            this.path = path;
            this.defaultSound = defaultSound;
        }

        public String getPath() {
            return path;
        }

        public Sound getDefaultSound() {
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
        itemCache.clear();
        soundCache.clear();
        preload();
    }

    private static void preload() {
        for (ItemType type : ItemType.values()) {
            String path = "items." + type.getPath();
            String materialName = config.getString(path + ".material", type.getDefaultMaterial().name());
            int customModelData = config.getInt(path + ".custom-model-data", 0);
            int damage = config.getInt(path + ".damage", 0);
            boolean hideFlags = config.getBoolean(path + ".hide-flags", false);
            List<String> flagList = config.getStringList(path + ".flags");
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                ItemStack itemStack = new ItemStack(material);
                ItemMeta itemMeta = itemStack.getItemMeta();
                if (itemMeta != null) {
                    itemMeta.setCustomModelData(customModelData);

                    if (itemMeta instanceof Damageable damageable) {
                        damageable.setDamage(damage);
                    }

                    if (hideFlags) {
                        itemMeta.addItemFlags(ItemFlag.values());
                    }

                    if (!flagList.isEmpty()) {
                        for (String flagName : flagList) {
                            try {
                                ItemFlag flag = ItemFlag.valueOf(flagName.toUpperCase());
                                itemMeta.addItemFlags(flag);
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Invalid item flag in config.yml at '" + path + ".flags': " + flagName);
                            }
                        }
                    }

                    itemStack.setItemMeta(itemMeta);
                }
                itemCache.put(type, itemStack);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material name in config.yml at '" + path + "': " + materialName);
                itemCache.put(type, new ItemStack(type.getDefaultMaterial()));
            }
        }

        for (SoundType type : SoundType.values()) {
            String path = "sounds." + type.getPath();
            String soundName = config.getString(path, type.getDefaultSound().name());
            try {
                soundCache.put(type, Sound.valueOf(soundName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound name in config.yml at '" + path + "': " + soundName);
                soundCache.put(type, type.getDefaultSound());
            }
        }
    }

    public static ItemStack getItem(ItemType type) {
        return itemCache.getOrDefault(type, new ItemStack(type.getDefaultMaterial())).clone();
    }

    public static Sound getSound(SoundType type) {
        return soundCache.getOrDefault(type, type.getDefaultSound());
    }
}
