// LangUtil removed and replaced with hardcoded English messages

package com.gmail.bobason01.commands;

import com.gmail.bobason01.gui.MailGUI;
import com.gmail.bobason01.gui.MailSendAllGUI;
import com.gmail.bobason01.gui.MailSendGUI;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.MailManager;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.*;

public class MailCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        UUID senderId = player.getUniqueId();

        if (args.length < 1) {
            new MailGUI(Bukkit.getPluginManager().getPlugin("MailManager")).open(player);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "send" -> {
                if (args.length == 1) {
                    new MailSendGUI(Bukkit.getPluginManager().getPlugin("MailManager")).open(player);
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || !target.hasPlayedBefore()) {
                    player.sendMessage("§c[Mail] Invalid player: " + args[1]);
                    return true;
                }

                if (args.length == 2) {
                    MailSendGUI gui = new MailSendGUI(Bukkit.getPluginManager().getPlugin("MailManager"));
                    MailService.setTarget(senderId, target);
                    gui.open(player);
                    return true;
                }

                ItemStack item = parseItem(args[2]);
                if (item == null || item.getType() == Material.AIR) {
                    player.sendMessage("§c[Mail] Invalid item: " + args[2]);
                    return true;
                }

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expire;

                if (args.length >= 4) {
                    expire = parseExpireTime(args[3], now);
                    if (expire == null) {
                        player.sendMessage("§c[Mail] Invalid time format. Use formats like: 7d, 12h, 30m");
                        return true;
                    }
                } else {
                    expire = now.plusDays(30);
                }

                Mail mail = new Mail(senderId, target.getUniqueId(), item, now, expire);
                MailDataManager.getInstance().addMail(target.getUniqueId(), mail);

                player.sendMessage("§a[Mail] Sent mail to " + target.getName());
                return true;
            }
            case "sendall" -> {
                new MailSendAllGUI(Bukkit.getPluginManager().getPlugin("MailManager")).open(player);
                return true;
            }
            case "reload" -> {
                MailManager plugin = (MailManager) Bukkit.getPluginManager().getPlugin("MailManager");
                plugin.reloadConfig();
                player.sendMessage("§a[Mail] Configuration reloaded.");
                return true;
            }
        }

        return false;
    }

    private ItemStack parseItem(String id) {
        if (id.toLowerCase().startsWith("mmoitems:")) {
            try {
                String[] parts = id.substring("mmoitems:".length()).split("\\.");
                if (parts.length != 2) return null;

                String typeId = parts[0].toLowerCase();
                String itemId = parts[1].toUpperCase();

                Type type = MMOItems.plugin.getTypes().getAll()
                        .stream().filter(t -> t.getId().equalsIgnoreCase(typeId))
                        .findFirst().orElse(null);
                if (type == null) return null;

                MMOItem mmoItem = MMOItems.plugin.getMMOItem(type, itemId);
                return mmoItem != null ? mmoItem.newBuilder().build() : null;
            } catch (Exception e) {
                return null;
            }
        }

        try {
            return new ItemStack(Material.matchMaterial(id.toUpperCase()));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseExpireTime(String input, LocalDateTime now) {
        try {
            if (input.endsWith("d")) {
                int days = Integer.parseInt(input.replace("d", ""));
                return now.plusDays(days);
            } else if (input.endsWith("h")) {
                int hours = Integer.parseInt(input.replace("h", ""));
                return now.plusHours(hours);
            } else if (input.endsWith("m")) {
                int minutes = Integer.parseInt(input.replace("m", ""));
                return now.plusMinutes(minutes);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        if (args.length == 1) {
            return List.of("send", "sendall", "reload", "setlang").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("send")) {
            String currentInput = args[2].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            for (Type type : MMOItems.plugin.getTypes().getAll()) {
                Collection<MMOItemTemplate> templates = MMOItems.plugin.getTemplates().getTemplates(type);
                for (MMOItemTemplate template : templates) {
                    String suggestion = "mmoitems:" + type.getId() + "." + template.getId();
                    if (suggestion.toLowerCase().startsWith(currentInput)) {
                        suggestions.add(suggestion);
                    }
                }
            }

            for (Material mat : Material.values()) {
                if (mat.isItem() && mat.name().toLowerCase().startsWith(currentInput)) {
                    suggestions.add(mat.name().toLowerCase());
                }
            }

            return suggestions;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("send")) {
            return List.of("7d", "12h", "30m", "1h", "5m").stream()
                    .filter(s -> s.startsWith(args[3].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setlang")) {
            return List.of("en", "ko").stream()
                    .filter(lang -> lang.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}
