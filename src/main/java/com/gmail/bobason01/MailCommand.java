package com.gmail.bobason01;

import com.gmail.bobason01.gui.*;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.utils.LangUtil;
import com.gmail.bobason01.utils.MailNotifySettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.UUID;

public class MailCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (args.length == 0) {
            new MailGUI(MailManager.getInstance()).open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "send" -> {
                if (!player.hasPermission("mail.send")) {
                    player.sendMessage(LangUtil.get(uuid, "no-permission"));
                    return true;
                }
                new MailSendGUI(MailManager.getInstance()).open(player);
            }

            case "sendall" -> {
                if (!player.hasPermission("mail.sendall")) {
                    player.sendMessage(LangUtil.get(uuid, "no-permission"));
                    return true;
                }
                new MailSendAllGUI(MailManager.getInstance()).open(player);
            }

            case "reload" -> {
                if (!player.hasPermission("mail.reload")) {
                    player.sendMessage(LangUtil.get(uuid, "no-permission"));
                    return true;
                }
                MailManager.getInstance().getConfigLoader().reloadConfig();
                LangUtil.load(MailManager.getInstance());
                player.sendMessage(LangUtil.get(uuid, "reload-success"));
            }

            case "reset" -> {
                if (!player.hasPermission("mail.reset")) {
                    player.sendMessage(LangUtil.get(uuid, "no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(LangUtil.get(uuid, "command.reset.usage"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                MailService.clearInbox(target.getUniqueId());
                player.sendMessage(LangUtil.get(uuid, "reset-success")
                        .replace("%target%", target.getName() != null ? target.getName() : args[1]));
            }

            case "notify" -> {
                if (args.length == 1) {
                    MailNotifySettingsManager.toggle(uuid);
                } else {
                    boolean enabled = args[1].equalsIgnoreCase("on");
                    MailNotifySettingsManager.setNotify(uuid, enabled);
                }
                MailNotifySettingsManager.save();
                player.sendMessage(LangUtil.get(uuid,
                        MailNotifySettingsManager.isNotifyEnabled(uuid)
                                ? "notify.enabled"
                                : "notify.disabled"));
            }

            default -> {
                player.sendMessage(LangUtil.get(uuid, "command.usage"));
            }
        }

        return true;
    }
}
