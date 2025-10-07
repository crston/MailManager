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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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

    private final Plugin plugin;
    private final Map<UUID, Class<? extends InventoryHolder>> parentGuiMap = new ConcurrentHashMap<>();

    public MailTimeSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }

    public void open(Player player, Class<? extends InventoryHolder> parent) {
        UUID uuid = player.getUniqueId();
        parentGuiMap.put(uuid, parent);

        String lang = LangManager.getLanguage(uuid);
        Map<String, Integer> time = MailService.getTimeData(uuid);
        Inventory inv = Bukkit.createInventory(this, 36, LangManager.get(lang, "gui.time.title"));

        for (int i = 0; i < TIME_UNITS.size(); i++) {
            String unit = TIME_UNITS.get(i);
            int value = time.getOrDefault(unit, 0);

            inv.setItem(UNIT_START_SLOT + i, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.TIME_GUI_UNIT).clone())
                    .name(LangManager.get(lang, "gui.time-unit." + unit + ".name").replace("%value%", String.valueOf(value)))
                    .lore(LangManager.get(lang, "gui.time-unit.lore"))
                    .build());
        }

        inv.setItem(PERMANENT_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.TIME_GUI_PERMANENT).clone())
                .name(LangManager.get(lang, "gui.permanent.name"))
                .lore(LangManager.get(lang, "gui.permanent.lore"))
                .build());

        inv.setItem(CHAT_INPUT_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.TIME_GUI_CHAT_INPUT).clone())
                .name(LangManager.get(lang, "gui.time.chat-input.name"))
                .lore(LangManager.get(lang, "gui.time.chat-input.lore"))
                .build());

        inv.setItem(CONFIRM_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.TIME_GUI_CONFIRM).clone())
                .name(LangManager.get(lang, "gui.select-complete.name"))
                .lore(LangManager.get(lang, "gui.select-complete.lore"))
                .build());

        inv.setItem(BACK_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON).clone())
                .name(LangManager.get(lang, "gui.back.name"))
                .lore(LangManager.get(lang, "gui.back.lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailTimeSelectGUI) || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        e.setCancelled(true);
        UUID uuid = player.getUniqueId();
        int slot = e.getRawSlot();
        Map<String, Integer> time = MailService.getTimeData(uuid);

        if (slot >= UNIT_START_SLOT && slot < UNIT_START_SLOT + TIME_UNITS.size()) {
            int index = slot - UNIT_START_SLOT;
            String unit = TIME_UNITS.get(index);
            int value = time.getOrDefault(unit, 0);

            ClickType click = e.getClick();
            if (click.isShiftClick()) value += (click.isLeftClick() ? 10 : -10);
            else value += (click.isLeftClick() ? 1 : -1);
            value = Math.max(0, value);

            time.put(unit, value);
            MailService.setTimeData(uuid, time);
            player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.GUI_CLICK), 1, 1);
            open(player, parentGuiMap.get(uuid));
            return;
        }

        switch (slot) {
            case PERMANENT_SLOT -> {
                MailService.setTimeData(uuid, new HashMap<>());
                player.sendMessage(LangManager.get(uuid, "time.permanent"));
                player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.GUI_CLICK), 1, 1);
                open(player, parentGuiMap.get(uuid));
            }
            case CHAT_INPUT_SLOT -> {
                player.closeInventory();
                player.sendMessage(LangManager.get(uuid, "time.chat.prompt"));
                buildConversation(player).begin();
            }
            case CONFIRM_SLOT, BACK_SLOT -> goBack(player);
        }
    }

    private void goBack(Player player) {
        player.playSound(player.getLocation(), ConfigManager.getSound(ConfigManager.SoundType.ACTION_SELECTION_COMPLETE), 1, 1.2f);
        Class<?> parentClass = parentGuiMap.remove(player.getUniqueId());
        MailManager manager = MailManager.getInstance();

        if (parentClass == MailSendGUI.class) {
            manager.mailSendGUI.open(player);
        } else if (parentClass == MailSendAllGUI.class) {
            manager.mailSendAllGUI.open(player);
        } else {
            manager.mailGUI.open(player);
        }
    }

    private Conversation buildConversation(Player player) {
        UUID uuid = player.getUniqueId();
        return new ConversationFactory(plugin)
                .withFirstPrompt(new StringPrompt() {
                    @NotNull
                    @Override
                    public String getPromptText(@NotNull ConversationContext context) {
                        return LangManager.get(uuid, "time.chat.instruction");
                    }

                    @Override
                    public Prompt acceptInput(@NotNull ConversationContext context, String input) {
                        if (input == null || input.isBlank()) {
                            player.sendMessage(LangManager.get(uuid, "time.chat.invalid"));
                        } else if (input.trim().equals("-1")) {
                            MailService.setTimeData(uuid, new HashMap<>());
                            player.sendMessage(LangManager.get(uuid, "time.permanent"));
                        } else {
                            Map<String, Integer> result = parseTimeInput(input);
                            if (result.isEmpty()) {
                                player.sendMessage(LangManager.get(uuid, "time.chat.invalid"));
                            } else {
                                MailService.setTimeData(uuid, result);
                                player.sendMessage(LangManager.get(uuid, "time.chat.success"));
                            }
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> open(player, parentGuiMap.get(uuid)));
                        return Prompt.END_OF_CONVERSATION;
                    }
                })
                .withLocalEcho(false)
                .withTimeout(30)
                .buildConversation(player);
    }

    private Map<String, Integer> parseTimeInput(String input) {
        Map<String, Integer> result = new HashMap<>();
        Matcher matcher = TIME_PATTERN.matcher(input);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = switch (matcher.group(2)) {
                case "s" -> "second";
                case "m" -> "minute";
                case "h" -> "hour";
                case "d" -> "day";
                case "M" -> "month";
                case "y" -> "year";
                default -> null;
            };
            if (unit != null) result.put(unit, value);
        }
        return result;
    }
}
