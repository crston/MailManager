package com.gmail.bobason01;

import com.gmail.bobason01.gui.*;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MailCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangUtil.get("only-player"));
            return true;
        }

        Plugin plugin = MailManager.getInstance();

        if (args.length == 0) {
            new MailGUI(plugin).open(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "send" -> {
                new MailSendGUI(plugin).open(player);
            }
            case "sendall" -> {
                new MailSendAllGUI(plugin).open(player);
            }
            case "notify" -> {
                boolean enabled = MailDataManager.getInstance().toggleNotify(player.getUniqueId());
                player.sendMessage(LangUtil.get(enabled ? "notify-on" : "notify-off"));
            }
            case "reload" -> {
                MailDataManager.getInstance().reload(plugin);
                LangUtil.load(plugin);
                player.sendMessage(LangUtil.get("reload-success"));
            }
            case "reset" -> {
                if (args.length < 2) {
                    player.sendMessage(LangUtil.get("invalid-args"));
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

                if (target.getName() == null) { // UUID만 있는 플레이어
                    player.sendMessage(LangUtil.get("player-not-found"));
                    return true;
                }

                MailDataManager.getInstance().reset(target.getUniqueId());
                player.sendMessage(LangUtil.get("reset-success"));
            }
            case "setting" -> {
                new MailSettingGUI(plugin).open(player);
            }
            default -> {
                player.sendMessage(LangUtil.get("invalid-args"));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return Stream.of("send", "sendall", "notify", "reset", "reload", "setting")
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
