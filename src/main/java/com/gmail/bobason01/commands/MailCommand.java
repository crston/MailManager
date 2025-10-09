package com.gmail.bobason01.commands;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import dev.lone.itemsadder.api.CustomStack;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
    private static final String ITEMSADDER_PREFIX = "itemsadder:";
    private static final String MODEL_PREFIX = "model:";
    private static final List<String> SUB_COMMANDS = Arrays.asList("send", "sendall", "reload", "setlang");
    private static final List<String> TIME_SUGGESTIONS = Arrays.asList("7d", "12h", "30m", "15s", "1h", "5m");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        UUID senderId = (sender instanceof Player p) ? p.getUniqueId() : SERVER_UUID;
        Plugin plugin = MailManager.getInstance();
        String lang = (sender instanceof Player p) ? LangManager.getLanguage(p.getUniqueId()) : "en";

        if (args.length == 0) {
            if (sender instanceof Player player) {
                MailManager.getInstance().mailGUI.open(player);
            } else {
                sender.sendMessage(LangManager.get(lang, "cmd.gui.unavailable"));
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        boolean isPlayer = sender instanceof Player;

        // ---------------- SEND ----------------
        if (sub.equals("send")) {
            if (args.length < 3) {
                if (isPlayer) MailManager.getInstance().mailSendGUI.open((Player) sender);
                else sender.sendMessage(LangManager.get(lang, "cmd.send.usage"));
                return true;
            }

            // 대상 확인
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
                sender.sendMessage(LangManager.get(lang, "cmd.player.notfound").replace("%name%", args[1]));
                return true;
            }

            // 아이템 파싱
            String itemId = args[2];
            ItemStack item = parseItem(itemId, lang, sender);
            if (item == null || item.getType() == Material.AIR) {
                sender.sendMessage(LangManager.get(lang, "cmd.item.invalid").replace("%id%", itemId));
                return true;
            }

            // 시간 + 옵션 파싱
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expire = now.plusDays(30);
            boolean onlineOnly = false, offlineOnly = false;

            if (args.length >= 4) {
                String lastArg = args[args.length - 1];
                if (lastArg.equalsIgnoreCase("-online")) {
                    onlineOnly = true;
                } else if (lastArg.equalsIgnoreCase("-offline")) {
                    offlineOnly = true;
                }
                if (args.length >= 5 || (!lastArg.startsWith("-"))) {
                    expire = parseExpireTime(args[3], now);
                    if (expire == null) {
                        sender.sendMessage(LangManager.get(lang, "cmd.expire.invalid"));
                        return true;
                    }
                }
            }

            if (onlineOnly && !target.isOnline()) {
                sender.sendMessage(LangManager.get(lang, "cmd.send.target-offline"));
                return true;
            }
            if (offlineOnly && target.isOnline()) {
                sender.sendMessage(LangManager.get(lang, "cmd.send.target-online"));
                return true;
            }

            Mail mail = new Mail(senderId, target.getUniqueId(), Collections.singletonList(item), now, expire);
            MailDataManager.getInstance().addMail(mail);
            sender.sendMessage(LangManager.get(lang, "cmd.send.success").replace("%name%", Objects.requireNonNull(target.getName())));
            return true;
        }

        // ---------------- SENDALL ----------------
        if (sub.equals("sendall")) {
            if (args.length < 2) {
                if (isPlayer) MailManager.getInstance().mailSendAllGUI.open((Player) sender);
                else sender.sendMessage(LangManager.get(lang, "cmd.sendall.usage"));
                return true;
            }

            String itemId = args[1];
            ItemStack item = parseItem(itemId, lang, sender);
            if (item == null || item.getType() == Material.AIR) {
                sender.sendMessage(LangManager.get(lang, "cmd.item.invalid").replace("%id%", itemId));
                return true;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expire = now.plusDays(30);
            boolean onlineOnly = false, offlineOnly = false;

            if (args.length >= 3) {
                String lastArg = args[args.length - 1];
                if (lastArg.equalsIgnoreCase("-online")) {
                    onlineOnly = true;
                } else if (lastArg.equalsIgnoreCase("-offline")) {
                    offlineOnly = true;
                }
                if (args.length >= 4 || (!lastArg.startsWith("-"))) {
                    expire = parseExpireTime(args[2], now);
                    if (expire == null) {
                        sender.sendMessage(LangManager.get(lang, "cmd.expire.invalid"));
                        return true;
                    }
                }
            }

            int count = 0;
            for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
                if (!target.hasPlayedBefore()) continue;
                if (onlineOnly && !target.isOnline()) continue;
                if (offlineOnly && target.isOnline()) continue;

                Mail mail = new Mail(senderId, target.getUniqueId(), Collections.singletonList(item), now, expire);
                MailDataManager.getInstance().addMail(mail);
                count++;
            }

            sender.sendMessage(LangManager.get(lang, "cmd.sendall.success").replace("%count%", Integer.toString(count)));
            return true;
        }

        // ---------------- RELOAD ----------------
        if (sub.equals("reload")) {
            Objects.requireNonNull(plugin).reloadConfig();
            ConfigManager.reload();
            LangManager.loadAll(plugin.getDataFolder());
            sender.sendMessage(LangManager.get(lang, "cmd.reload"));
            return true;
        }

        // ---------------- SETLANG ----------------
        if (sub.equals("setlang")) {
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

    private ItemStack parseItem(String id, String lang, CommandSender sender) {
        try {
            if (id.regionMatches(true, 0, MMOITEMS_PREFIX, 0, MMOITEMS_PREFIX.length())) {
                if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) return null;
                String[] parts = id.substring(MMOITEMS_PREFIX.length()).split("\\.");
                if (parts.length != 2) return null;
                Type type = MMOItems.plugin.getTypes().get(parts[0].toUpperCase());
                if (type != null) {
                    MMOItem mmoItem = MMOItems.plugin.getMMOItem(type, parts[1].toUpperCase());
                    return (mmoItem != null) ? mmoItem.newBuilder().build() : null;
                }
                return null;
            }
            if (id.regionMatches(true, 0, ITEMSADDER_PREFIX, 0, ITEMSADDER_PREFIX.length())) {
                if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return null;
                String iaId = id.substring(ITEMSADDER_PREFIX.length());
                CustomStack stack = CustomStack.getInstance(iaId);
                return (stack != null) ? stack.getItemStack() : null;
            }
            if (id.regionMatches(true, 0, MODEL_PREFIX, 0, MODEL_PREFIX.length())) {
                String[] parts = id.substring(MODEL_PREFIX.length()).split(":");
                if (parts.length != 2) return null;
                Material material = Material.matchMaterial(parts[0].toUpperCase());
                if (material == null) return null;
                int customModelData = Integer.parseInt(parts[1]);
                return new ItemBuilder(material).customModelData(customModelData).build();
            }
            Material mat = Material.matchMaterial(id.toUpperCase());
            return (mat != null) ? new ItemStack(mat) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseExpireTime(String input, LocalDateTime now) {
        try {
            int value = Integer.parseInt(input.substring(0, input.length() - 1));
            char unit = input.charAt(input.length() - 1);
            return switch (unit) {
                case 'd' -> now.plusDays(value);
                case 'h' -> now.plusHours(value);
                case 'm' -> now.plusMinutes(value);
                case 's' -> now.plusSeconds(value);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        int len = args.length;
        String prefix = args[len - 1].toLowerCase();

        if (len == 1) {
            return SUB_COMMANDS.stream().filter(cmd -> cmd.startsWith(prefix)).collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase();

        if (len == 2 && sub.equals("setlang")) {
            return LangManager.getAvailableLanguages().stream().filter(l -> l.startsWith(prefix)).collect(Collectors.toList());
        }

        if (len == 2 && sub.equals("send")) {
            return PlayerCache.getCachedPlayers().stream()
                    .filter(OfflinePlayer::hasPlayedBefore)
                    .map(OfflinePlayer::getName)
                    .filter(Objects::nonNull)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if ((len == 2 && sub.equals("sendall")) || (len == 3 && sub.equals("send"))) {
            List<String> result = new ArrayList<>();
            if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                try {
                    for (Type type : MMOItems.plugin.getTypes().getAll()) {
                        for (MMOItemTemplate template : MMOItems.plugin.getTemplates().getTemplates(type)) {
                            String fullId = MMOITEMS_PREFIX + type.getId().toLowerCase() + "." + template.getId().toLowerCase();
                            if (fullId.startsWith(prefix)) result.add(fullId);
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
                try {
                    for (Object namespacedId : CustomStack.getNamespacedIdsInRegistry()) {
                        String fullId = ITEMSADDER_PREFIX + namespacedId;
                        if (fullId.toLowerCase().startsWith(prefix)) result.add(fullId);
                    }
                } catch (Exception ignored) {}
            }
            for (Material mat : Material.values()) {
                if (mat.isItem() && mat.name().toLowerCase().startsWith(prefix)) {
                    result.add(mat.name().toLowerCase());
                }
            }
            return result;
        }

        if ((len == 3 && sub.equals("sendall")) || (len == 4 && sub.equals("send"))) {
            return TIME_SUGGESTIONS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }

        if (len >= 3 && (sub.equals("sendall") || sub.equals("send"))) {
            return Arrays.asList("-online", "-offline").stream().filter(opt -> opt.startsWith(prefix)).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
