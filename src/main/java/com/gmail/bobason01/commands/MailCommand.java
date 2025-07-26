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

        // /mail
        if (args.length < 1) {
            new MailGUI(Bukkit.getPluginManager().getPlugin("MailManager")).open(player);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "send" -> {
                // /mail send
                if (args.length == 1) {
                    new MailSendGUI(Bukkit.getPluginManager().getPlugin("MailManager")).open(player);
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || !target.hasPlayedBefore()) {
                    player.sendMessage("§c[우편] 존재하지 않는 플레이어입니다: " + args[1]);
                    return true;
                }

                // /mail send <대상>
                if (args.length == 2) {
                    MailSendGUI gui = new MailSendGUI(Bukkit.getPluginManager().getPlugin("MailManager"));
                    MailService.setTarget(senderId, target);
                    gui.open(player);
                    return true;
                }

                // MMOItems 사용 시 플러그인 존재 여부 확인
                if (args[2].toLowerCase().startsWith("mmoitems:") &&
                        !Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                    player.sendMessage("§c[우편] MMOItems 플러그인이 설치되어 있지 않습니다. 해당 아이템을 사용할 수 없습니다.");
                    return true;
                }

                ItemStack item = parseItem(args[2]);
                if (item == null || item.getType() == Material.AIR) {
                    player.sendMessage("§c[우편] 잘못된 아이템입니다: " + args[2]);
                    return true;
                }

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expire;

                if (args.length >= 4) {
                    expire = parseExpireTime(args[3], now);
                    if (expire == null) {
                        player.sendMessage("§c[우편] 시간 형식이 잘못되었습니다. 예: 7d, 12h, 30m");
                        return true;
                    }
                } else {
                    expire = now.plusDays(30); // 기본 만료 30일
                }

                Mail mail = new Mail(senderId, target.getUniqueId(), item, now, expire);
                MailDataManager.getInstance().addMail(target.getUniqueId(), mail);

                player.sendMessage("§a[우편] " + target.getName() + " 님에게 우편을 보냈습니다.");
                return true;
            }

            case "sendall" -> {
                new MailSendAllGUI(Bukkit.getPluginManager().getPlugin("MailManager")).open(player);
                return true;
            }

            case "reload" -> {
                MailManager plugin = (MailManager) Bukkit.getPluginManager().getPlugin("MailManager");
                plugin.reloadConfig();
                player.sendMessage("§a[우편] 설정 파일을 다시 불러왔습니다.");
                return true;
            }
        }

        return false;
    }

    // 아이템 파싱 (기본 또는 MMOItems)
    private ItemStack parseItem(String id) {
        if (id.toLowerCase().startsWith("mmoitems:")) {
            if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) return null;

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

    // 만료 시간 파싱
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

        // /mail send <플레이어>
        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        // /mail send <플레이어> <아이템>
        if (args.length == 3 && args[0].equalsIgnoreCase("send")) {
            String currentInput = args[2].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            // MMOItems 플러그인 있을 경우만 제안
            if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                for (Type type : MMOItems.plugin.getTypes().getAll()) {
                    Collection<MMOItemTemplate> templates = MMOItems.plugin.getTemplates().getTemplates(type);
                    for (MMOItemTemplate template : templates) {
                        String suggestion = "mmoitems:" + type.getId() + "." + template.getId();
                        if (suggestion.toLowerCase().startsWith(currentInput)) {
                            suggestions.add(suggestion);
                        }
                    }
                }
            }

            // 기본 마인크래프트 아이템 제안
            for (Material mat : Material.values()) {
                if (mat.isItem() && mat.name().toLowerCase().startsWith(currentInput)) {
                    suggestions.add(mat.name().toLowerCase());
                }
            }

            return suggestions;
        }

        // /mail send <플레이어> <아이템> <시간>
        if (args.length == 4 && args[0].equalsIgnoreCase("send")) {
            return List.of("7d", "12h", "30m", "1h", "5m").stream()
                    .filter(s -> s.startsWith(args[3].toLowerCase()))
                    .toList();
        }

        // /mail setlang <언어>
        if (args.length == 2 && args[0].equalsIgnoreCase("setlang")) {
            return List.of("en", "ko").stream()
                    .filter(lang -> lang.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}
