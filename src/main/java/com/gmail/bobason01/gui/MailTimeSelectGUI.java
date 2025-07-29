package com.gmail.bobason01.gui;

import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailTimeSelectGUI implements Listener {

    private static final List<String> TIME_UNITS = List.of("second", "minute", "hour", "day", "month", "year");
    private static final int UNIT_START_SLOT = 10;
    private static final int PERMANENT_SLOT = 16;
    private static final int CHAT_INPUT_SLOT = 27;
    private static final int CONFIRM_SLOT = 31;
    private static final int BACK_SLOT = 35;

    private final Plugin plugin;

    public MailTimeSelectGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        Map<String, Integer> time = MailService.getTimeData(uuid);
        Inventory inv = Bukkit.createInventory(player, 36, LangManager.get(lang, "gui.time.title"));

        for (int i = 0; i < TIME_UNITS.size(); i++) {
            String unit = TIME_UNITS.get(i);
            int value = time.getOrDefault(unit, 0);

            inv.setItem(UNIT_START_SLOT + i, new ItemBuilder(Material.CLOCK)
                    .name(LangManager.get(lang, "gui.time-unit." + unit + ".name")
                            .replace("%value%", String.valueOf(value)))
                    .lore(LangManager.get(lang, "gui.time-unit.lore"))
                    .build());
        }

        inv.setItem(PERMANENT_SLOT, new ItemBuilder(Material.BARRIER)
                .name(LangManager.get(lang, "gui.permanent.name"))
                .lore(LangManager.get(lang, "gui.permanent.lore"))
                .build());

        inv.setItem(CHAT_INPUT_SLOT, new ItemBuilder(Material.WRITABLE_BOOK)
                .name(LangManager.get(lang, "gui.time-chat-input.name"))
                .lore(LangManager.get(lang, "gui.time-chat-input.lore"))
                .build());

        inv.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_CONCRETE)
                .name(LangManager.get(lang, "gui.select-complete.name"))
                .lore(LangManager.get(lang, "gui.select-complete.lore"))
                .build());

        inv.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name(LangManager.get(lang, "gui.back.name"))
                .lore(LangManager.get(lang, "gui.back.lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);

        if (!e.getView().getTitle().equals(LangManager.get(lang, "gui.time.title"))) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        Map<String, Integer> time = MailService.getTimeData(uuid);

        if (slot >= UNIT_START_SLOT && slot < UNIT_START_SLOT + TIME_UNITS.size()) {
            int index = slot - UNIT_START_SLOT;
            String unit = TIME_UNITS.get(index);
            int value = time.getOrDefault(unit, 0);

            switch (e.getClick()) {
                case SHIFT_LEFT -> value = Math.max(0, value - 10);
                case SHIFT_RIGHT -> value += 10;
                case LEFT -> value = Math.max(0, value - 1);
                case RIGHT -> value += 1;
            }

            time.put(unit, value);
            MailService.setTimeData(uuid, time);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            open(player);
            return;
        }

        if (slot == PERMANENT_SLOT) {
            MailService.setTimeData(uuid, new HashMap<>());
            player.sendMessage(LangManager.get(uuid, "time.permanent"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            open(player);
            return;
        }

        if (slot == CHAT_INPUT_SLOT) {
            player.closeInventory();
            player.sendMessage(LangManager.get(uuid, "time.chat.prompt"));

            new ConversationFactory(plugin)
                    .withFirstPrompt(new Prompt() {
                        @Override
                        public boolean blocksForInput(ConversationContext context) {
                            return true;
                        }

                        @Override
                        public String getPromptText(ConversationContext context) {
                            return LangManager.get(uuid, "time.chat.instruction");
                        }

                        @Override
                        public Prompt acceptInput(ConversationContext context, String input) {
                            if (input.trim().equals("-1")) {
                                MailService.setTimeData(uuid, new HashMap<>());
                                player.sendMessage(LangManager.get(uuid, "time.permanent"));
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                            } else {
                                Map<String, Integer> result = parseTimeInput(input);
                                if (result.isEmpty()) {
                                    player.sendMessage(LangManager.get(uuid, "time.chat.invalid"));
                                } else {
                                    MailService.setTimeData(uuid, result);
                                    player.sendMessage(LangManager.get(uuid, "time.chat.success"));
                                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                                }
                            }
                            open(player);
                            return Prompt.END_OF_CONVERSATION;
                        }
                    }).withLocalEcho(false).buildConversation(player).begin();
            return;
        }

        if (slot == CONFIRM_SLOT) {
            player.sendMessage(LangManager.get(uuid, "time.confirmed"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
            new MailSendGUI(plugin).open(player);
            return;
        }

        if (slot == BACK_SLOT) {
            new MailSendGUI(plugin).open(player);
        }
    }

    private Map<String, Integer> parseTimeInput(String input) {
        Map<String, Integer> result = new HashMap<>();
        Matcher matcher = Pattern.compile("(\\d+)([smhdMy])").matcher(input);
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
