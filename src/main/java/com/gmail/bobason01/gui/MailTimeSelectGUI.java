package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailTimeSelectGUI implements Listener, InventoryHolder {

    private static final List<String> TIME_UNITS = List.of("second", "minute", "hour", "day", "month", "year");
    private static final int UNIT_START_SLOT = 10;
    private static final int PERMANENT_SLOT = 16;
    private static final int CHAT_INPUT_SLOT = 27;
    private static final int CONFIRM_SLOT = 31;
    private static final int BACK_SLOT = 35;

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smhdMy])");
    private static final Map<Character, String> UNIT_MAP = Map.of(
            's', "second", 'm', "minute", 'h', "hour", 'd', "day", 'M', "month", 'y', "year"
    );

    private final Plugin plugin;
    private final Map<UUID, Class<? extends InventoryHolder>> parentGuiMap = new ConcurrentHashMap<>();

    public MailTimeSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 36);
    }

    public void open(Player player, Class<? extends InventoryHolder> parent) {
        UUID uuid = player.getUniqueId();
        if (parent != null) parentGuiMap.put(uuid, parent);

        String lang = LangManager.getLanguage(uuid);
        Map<String, Integer> time = MailService.getTimeData(uuid);
        Inventory inv = Bukkit.createInventory(this, 36, LangManager.get(lang, "gui.time.title"));

        for (int i = 0; i < TIME_UNITS.size(); i++) {
            String unit = TIME_UNITS.get(i);
            inv.setItem(UNIT_START_SLOT + i, createUnitItem(lang, unit, time.getOrDefault(unit, 0)));
        }

        inv.setItem(PERMANENT_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.TIME_GUI_PERMANENT)).name(LangManager.get(lang, "gui.permanent.name")).build());
        inv.setItem(CHAT_INPUT_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.TIME_GUI_CHAT_INPUT)).name(LangManager.get(lang, "gui.time.chat-input.name")).build());
        inv.setItem(CONFIRM_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.TIME_GUI_CONFIRM)).name(LangManager.get(lang, "gui.select-complete.name")).build());
        inv.setItem(BACK_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON)).name(LangManager.get(lang, "gui.back.name")).build());

        player.openInventory(inv);
    }

    private ItemStack createUnitItem(String lang, String unit, int value) {
        return new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.TIME_GUI_UNIT))
                .name(LangManager.get(lang, "gui.time-unit." + unit + ".name").replace("%value%", String.valueOf(value)))
                .lore(LangManager.getList(lang, "gui.time-unit.lore"))
                .build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailTimeSelectGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        UUID uuid = player.getUniqueId();
        Map<String, Integer> time = MailService.getTimeData(uuid);

        if (slot >= UNIT_START_SLOT && slot < UNIT_START_SLOT + TIME_UNITS.size()) {
            String unit = TIME_UNITS.get(slot - UNIT_START_SLOT);
            int value = time.getOrDefault(unit, 0);
            int amount = e.getClick().isShiftClick() ? 10 : 1;
            value = Math.max(0, value + (e.getClick().isLeftClick() ? amount : -amount));

            time.put(unit, value);
            MailService.setTimeData(uuid, time);
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            e.getInventory().setItem(slot, createUnitItem(LangManager.getLanguage(uuid), unit, value));
            return;
        }

        if (slot == PERMANENT_SLOT) {
            MailService.setTimeData(uuid, new HashMap<>());
            player.sendMessage(LangManager.get(uuid, "time.permanent"));
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            open(player, null);
        } else if (slot == CHAT_INPUT_SLOT) {
            player.closeInventory();
            buildConversation(player).begin();
        } else if (slot == CONFIRM_SLOT || slot == BACK_SLOT) {
            goBack(player);
        }
    }

    private void goBack(Player player) {
        ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SELECTION_COMPLETE);
        Class<? extends InventoryHolder> parent = parentGuiMap.remove(player.getUniqueId());
        if (parent == MailSendGUI.class) MailManager.getInstance().mailSendGUI.open(player);
        else if (parent == MailSendAllGUI.class) MailManager.getInstance().mailSendAllGUI.open(player);
        else MailManager.getInstance().mailGUI.open(player);
    }

    private Conversation buildConversation(Player player) {
        return new ConversationFactory(plugin)
                .withFirstPrompt(new StringPrompt() {
                    @NotNull @Override public String getPromptText(@NotNull ConversationContext c) { return LangManager.get(player.getUniqueId(), "time.chat.instruction"); }
                    @Override public Prompt acceptInput(@NotNull ConversationContext c, String input) {
                        if (input != null && input.equals("-1")) {
                            MailService.setTimeData(player.getUniqueId(), new HashMap<>());
                        } else {
                            Map<String, Integer> result = parseTimeInput(input);
                            if (!result.isEmpty()) MailService.setTimeData(player.getUniqueId(), result);
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> open(player, null));
                        return Prompt.END_OF_CONVERSATION;
                    }
                }).withLocalEcho(false).buildConversation(player);
    }

    private Map<String, Integer> parseTimeInput(String input) {
        if (input == null) return Collections.emptyMap();
        Map<String, Integer> result = new HashMap<>();
        Matcher matcher = TIME_PATTERN.matcher(input);
        while (matcher.find()) {
            String unit = UNIT_MAP.get(matcher.group(2).charAt(0));
            if (unit != null) result.put(unit, Integer.parseInt(matcher.group(1)));
        }
        return result;
    }
}