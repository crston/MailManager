package com.gmail.bobason01;

import com.gmail.bobason01.gui.*;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
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

        if (args.length == 0) {
            new MailGUI(MailManager.getInstance()).open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "send" -> {
                new MailSendGUI(MailManager.getInstance()).open(player);
                return true;
            }
            case "sendall" -> {
                new MailSendAllGUI(MailManager.getInstance()).open(player);
                return true;
            }
            case "notify" -> {
                boolean newState = MailDataManager.getInstance().toggleNotify(player.getUniqueId());
                player.sendMessage(LangUtil.get(newState ? "notify-on" : "notify-off"));
                return true;
            }
            case "reload" -> {
                MailDataManager.getInstance().reload(MailManager.getInstance());
                LangUtil.load(MailManager.getInstance());
                player.sendMessage(LangUtil.get("reload-success"));
                return true;
            }
            case "reset" -> {
                if (args.length < 2) {
                    player.sendMessage(LangUtil.get("invalid-args"));
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || target.getName() == null) {
                    player.sendMessage(LangUtil.get("player-not-found"));
                    return true;
                }

                MailDataManager.getInstance().reset(target.getUniqueId());
                player.sendMessage(LangUtil.get("reset-success"));
                return true;
            }
            case "setting" -> {
                new MailSettingGUI(MailManager.getInstance()).open(player);
                return true;
            }
            default -> {
                player.sendMessage(LangUtil.get("invalid-args"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return Stream.of("send", "sendall", "notify", "reset", "reload", "setting")
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
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
