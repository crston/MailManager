package com.gmail.bobason01.gui;

import com.gmail.bobason01.config.ConfigLoader;
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
    private static final int CONFIRM_SLOT = 31;
    private static final int CHAT_INPUT_SLOT = 27;
    private static final int BACK_SLOT = 35;

    private final Plugin plugin;

    public MailTimeSelectGUI(Plugin plugin) {
        this.plugin = plugin;
        ConfigLoader.load(plugin);
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> time = MailService.getTimeData(uuid);

        Inventory inv = Bukkit.createInventory(player, 36, "만료 시간 설정");

        for (int i = 0; i < TIME_UNITS.size(); i++) {
            String unit = TIME_UNITS.get(i);
            int value = time.getOrDefault(unit, 0);
            inv.setItem(UNIT_START_SLOT + i, new ItemBuilder(Material.PAPER)
                    .name("§f" + getUnitDisplay(unit) + ": " + value)
                    .build());
        }

        inv.setItem(PERMANENT_SLOT, ConfigLoader.getGuiItem("permanent"));
        inv.setItem(CONFIRM_SLOT, ConfigLoader.getGuiItem("select-complete"));
        inv.setItem(CHAT_INPUT_SLOT, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("§b채팅으로 시간 입력")
                .lore("§7예: 2h30m 형식으로 수동 입력하려면 클릭")
                .build());
        inv.setItem(BACK_SLOT, ConfigLoader.getGuiItem("back"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals("만료 시간 설정")) return;

        e.setCancelled(true);

        UUID uuid = player.getUniqueId();
        Map<String, Integer> time = MailService.getTimeData(uuid);
        int slot = e.getRawSlot();

        if (slot >= UNIT_START_SLOT && slot < UNIT_START_SLOT + TIME_UNITS.size()) {
            int index = slot - UNIT_START_SLOT;
            String unit = TIME_UNITS.get(index);
            int value = time.getOrDefault(unit, 0);

            if (e.getClick().isShiftClick()) {
                if (e.getClick().isLeftClick()) value = Math.max(0, value - 10);
                else value += 10;
            } else {
                if (e.getClick().isLeftClick()) value = Math.max(0, value - 1);
                else value += 1;
            }

            time.put(unit, value);
            MailService.setTimeData(uuid, time);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            open(player);
            return;
        }

        if (slot == PERMANENT_SLOT) {
            MailService.setTimeData(uuid, new HashMap<>());
            player.sendMessage("§a[우편] 만료 시간이 제거되었습니다. 이 우편은 만료되지 않습니다.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            open(player);
            return;
        }

        if (slot == CHAT_INPUT_SLOT) {
            player.closeInventory();
            player.sendMessage("§b채팅에 시간을 입력하세요. (예: 2h30m, -1 입력 시 만료 없음)");

            ConversationFactory factory = new ConversationFactory(plugin);
            Conversation convo = factory.withFirstPrompt(new Prompt() {
                @Override
                public boolean blocksForInput(ConversationContext context) {
                    return true;
                }

                @Override
                public String getPromptText(ConversationContext context) {
                    return "시간 형식을 입력하세요. (예: 1d12h), 만료 없음은 -1:";
                }

                @Override
                public Prompt acceptInput(ConversationContext context, String input) {
                    if (input.trim().equalsIgnoreCase("-1")) {
                        MailService.setTimeData(uuid, new HashMap<>());
                        player.sendMessage("§a[우편] 만료 시간이 제거되었습니다.");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                        open(player);
                        return Prompt.END_OF_CONVERSATION;
                    }

                    Map<String, Integer> result = parseTimeInput(input);
                    if (result.isEmpty()) {
                        player.sendMessage("§c[우편] 잘못된 시간 형식입니다. 예: 2h30m");
                        open(player);
                    } else {
                        MailService.setTimeData(uuid, result);
                        player.sendMessage("§a[우편] 만료 시간이 설정되었습니다.");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                        open(player);
                    }
                    return Prompt.END_OF_CONVERSATION;
                }
            }).withLocalEcho(false).buildConversation(player);

            convo.begin();
            return;
        }

        if (slot == CONFIRM_SLOT) {
            player.sendMessage("§a[우편] 시간이 확인되었습니다.");
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

    private String getUnitDisplay(String unit) {
        return switch (unit) {
            case "second" -> "초";
            case "minute" -> "분";
            case "hour" -> "시간";
            case "day" -> "일";
            case "month" -> "달";
            case "year" -> "년";
            default -> unit;
        };
    }
}
