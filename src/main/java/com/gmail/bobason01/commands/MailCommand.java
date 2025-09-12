package com.gmail.bobason01.commands;

import com.gmail.bobason01.gui.MailGUI;
import com.gmail.bobason01.gui.MailSendAllGUI;
import com.gmail.bobason01.gui.MailSendGUI;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class MailCommand implements CommandExecutor, TabCompleter {

    public static final UUID SERVER_UUID = UUID.nameUUIDFromBytes("ServerSender".getBytes());
    private static final String MMOITEMS_PREFIX = "mmoitems:";
    private static final List<String> SUB_COMMANDS = Arrays.asList("send", "sendall", "reload", "setlang");
    private static final List<String> TIME_SUGGESTIONS = Arrays.asList("7d", "12h", "30m", "15s", "1h", "5m");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        UUID senderId = (sender instanceof Player p) ? p.getUniqueId() : SERVER_UUID;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MailManager");
        String lang = (sender instanceof Player p) ? LangManager.getLanguage(p.getUniqueId()) : LangManager.getLanguage(null);

        if (args.length == 0) {
            if (sender instanceof Player player) {
                new MailGUI(plugin).open(player);
            } else {
                sender.sendMessage(LangManager.get(lang, "cmd.gui.unavailable"));
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        boolean isPlayer = sender instanceof Player;

        if (sub.equalsIgnoreCase("send")) {
            if (args.length == 1) {
                if (isPlayer) new MailSendGUI(plugin).open((Player) sender);
                else sender.sendMessage(LangManager.get(lang, "cmd.gui.unavailable"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target == null || !target.hasPlayedBefore()) {
                sender.sendMessage(LangManager.get(lang, "cmd.player.notfound").replace("%name%", args[1]));
                return true;
            }

            if (args.length == 2) {
                if (isPlayer) new MailSendGUI(plugin).open((Player) sender);
                else sender.sendMessage(LangManager.get(lang, "cmd.gui.unavailable"));
                return true;
            }

            String itemId = args[2];
            if (itemId.regionMatches(true, 0, MMOITEMS_PREFIX, 0, MMOITEMS_PREFIX.length()) &&
                    !Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                sender.sendMessage(LangManager.get(lang, "cmd.mmoitems.missing"));
                return true;
            }

            ItemStack item = parseItem(itemId);
            if (item == null || item.getType() == Material.AIR) {
                sender.sendMessage(LangManager.get(lang, "cmd.item.invalid").replace("%id%", itemId));
                return true;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expire = (args.length >= 4) ? parseExpireTime(args[3], now) : now.plusDays(30);
            if (expire == null) {
                sender.sendMessage(LangManager.get(lang, "cmd.expire.invalid"));
                return true;
            }

            Mail mail = new Mail(senderId, target.getUniqueId(), item, now, expire);
            MailDataManager.getInstance().addMail(target.getUniqueId(), mail);
            sender.sendMessage(LangManager.get(lang, "cmd.send.success").replace("%name%", target.getName()));
            return true;
        }

        if (sub.equalsIgnoreCase("sendall")) {
            if (args.length == 1) {
                if (isPlayer) new MailSendAllGUI(plugin).open((Player) sender);
                else sender.sendMessage(LangManager.get(lang, "cmd.gui.unavailable"));
                return true;
            }

            String itemId = args[1];
            if (itemId.regionMatches(true, 0, MMOITEMS_PREFIX, 0, MMOITEMS_PREFIX.length()) &&
                    !Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                sender.sendMessage(LangManager.get(lang, "cmd.mmoitems.missing"));
                return true;
            }

            ItemStack item = parseItem(itemId);
            if (item == null || item.getType() == Material.AIR) {
                sender.sendMessage(LangManager.get(lang, "cmd.item.invalid").replace("%id%", itemId));
                return true;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expire = (args.length >= 3) ? parseExpireTime(args[2], now) : now.plusDays(30);
            if (expire == null) {
                sender.sendMessage(LangManager.get(lang, "cmd.expire.invalid"));
                return true;
            }

            int count = 0;
            for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
                if (target == null || !target.hasPlayedBefore()) continue;
                Mail mail = new Mail(senderId, target.getUniqueId(), item, now, expire);
                MailDataManager.getInstance().addMail(target.getUniqueId(), mail);
                count++;
            }

            sender.sendMessage(LangManager.get(lang, "cmd.sendall.success").replace("%count%", Integer.toString(count)));
            return true;
        }

        if (sub.equalsIgnoreCase("reload")) {
            Objects.requireNonNull(plugin).reloadConfig();
            sender.sendMessage(LangManager.get(lang, "cmd.reload"));
            return true;
        }

        if (sub.equalsIgnoreCase("setlang")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LangManager.get(lang, "cmd.player-only"));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(LangManager.get(lang, "cmd.setlang.usage"));
                return true;
            }
            String targetLang = args[1].toLowerCase();
            if (!LangManager.getAvailableLanguages().contains(targetLang)) {
                player.sendMessage(LangManager.get(lang, "cmd.setlang.not-found").replace("%lang%", targetLang));
                return true;
            }
            LangManager.setLanguage(player.getUniqueId(), targetLang);
            player.sendMessage(LangManager.get(player.getUniqueId(), "cmd.setlang.success").replace("%lang%", targetLang));
            return true;
        }

        return false;
    }

    private ItemStack parseItem(String id) {
        if (id.regionMatches(true, 0, MMOITEMS_PREFIX, 0, MMOITEMS_PREFIX.length())) {
            if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) return null;
            try {
                String[] parts = id.substring(MMOITEMS_PREFIX.length()).split("\\.");
                if (parts.length != 2) return null;

                String typeId = parts[0];
                String itemId = parts[1];

                for (Type type : MMOItems.plugin.getTypes().getAll()) {
                    if (type.getId().equalsIgnoreCase(typeId)) {
                        MMOItem mmoItem = MMOItems.plugin.getMMOItem(type, itemId.toUpperCase());
                        return (mmoItem != null) ? mmoItem.newBuilder().build() : null;
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }

        Material mat = Material.matchMaterial(id.toUpperCase());
        return (mat != null) ? new ItemStack(mat) : null;
    }

    private LocalDateTime parseExpireTime(String input, LocalDateTime now) {
        try {
            int value = Integer.parseInt(input.substring(0, input.length() - 1));
            switch (input.charAt(input.length() - 1)) {
                case 'd' -> { return now.plusDays(value); }
                case 'h' -> { return now.plusHours(value); }
                case 'm' -> { return now.plusMinutes(value); }
                case 's' -> { return now.plusSeconds(value); }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        int len = args.length;
        String prefix = args[len - 1].toLowerCase();

        if (len == 1) {
            return SUB_COMMANDS.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase();

        if (len == 2 && sub.equalsIgnoreCase("setlang")) {
            return LangManager.getAvailableLanguages().stream()
                    .filter(lang -> lang.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (len == 2 && sub.equalsIgnoreCase("send")) {
            return Arrays.stream(Bukkit.getOfflinePlayers())
                    .map(OfflinePlayer::getName)
                    .filter(Objects::nonNull)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if ((len == 2 && sub.equalsIgnoreCase("sendall")) || (len == 3 && sub.equalsIgnoreCase("send"))) {
            String itemPrefix = args[len - 1].toLowerCase();
            List<String> result = new ArrayList<>();

            if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                // MMOItems logic...
            }

            for (Material mat : Material.values()) {
                if (mat.isItem() && mat.name().toLowerCase().startsWith(itemPrefix)) {
                    result.add(mat.name().toLowerCase());
                }
            }
            return result;
        }

        if ((len == 3 && sub.equalsIgnoreCase("sendall")) || (len == 4 && sub.equalsIgnoreCase("send"))) {
            return TIME_SUGGESTIONS.stream()
                    .filter(s -> s.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}