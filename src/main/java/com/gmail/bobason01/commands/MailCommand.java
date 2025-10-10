package com.gmail.bobason01.commands;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.gui.MailInventoryGUI;
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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

public class MailCommand implements CommandExecutor, TabCompleter {

    public static final UUID SERVER_UUID = UUID.nameUUIDFromBytes("ServerSender".getBytes());
    private static final String MMOITEMS_PREFIX = "mmoitems:";
    private static final String ITEMSADDER_PREFIX = "itemsadder:";
    private static final String MODEL_PREFIX = "model:";

    private static final List<String> SUB_COMMANDS = Arrays.asList("send", "sendall", "reload", "setlang", "inv");
    private static final List<String> TIME_SUGGESTIONS = Arrays.asList("7d", "12h", "30m", "15s", "1h", "5m");

    private static final Map<Integer, ItemStack[]> invMap = new HashMap<>(16);
    private static final File invFile = new File(MailManager.getInstance().getDataFolder(), "inventories.yml");
    private static final FileConfiguration invConfig = YamlConfiguration.loadConfiguration(invFile);

    static { loadInventories(); }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        UUID senderId = (sender instanceof Player p) ? p.getUniqueId() : SERVER_UUID;
        Plugin plugin = MailManager.getInstance();
        String lang = (sender instanceof Player p) ? LangManager.getLanguage(p.getUniqueId()) : "en";

        if (args.length == 0) {
            if (sender instanceof Player player) MailManager.getInstance().mailGUI.open(player);
            else sender.sendMessage(LangManager.get(lang, "cmd.gui.unavailable"));
            return true;
        }

        String sub = args[0].toLowerCase();
        boolean isPlayer = sender instanceof Player;

        if (sub.equals("send")) {
            if (args.length < 3) {
                if (isPlayer) MailManager.getInstance().mailSendGUI.open((Player) sender);
                else sender.sendMessage(LangManager.get(lang, "cmd.send.usage"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
                sender.sendMessage(LangManager.get(lang, "cmd.player.notfound").replace("%name%", args[1]));
                return true;
            }

            List<ItemStack> items = parseItems(args[2], lang);
            if (items.isEmpty()) {
                sender.sendMessage(LangManager.get(lang, "cmd.item.invalid").replace("%id%", args[2]));
                return true;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expire = now.plusDays(30);
            boolean onlineOnly = false, offlineOnly = false;

            if (args.length >= 4) {
                String lastArg = args[args.length - 1];
                if (lastArg.equalsIgnoreCase("-online")) onlineOnly = true;
                else if (lastArg.equalsIgnoreCase("-offline")) offlineOnly = true;

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

            Mail mail = new Mail(senderId, target.getUniqueId(), items, now, expire);
            MailDataManager.getInstance().addMail(mail);
            sender.sendMessage(LangManager.get(lang, "cmd.send.success").replace("%name%", Objects.requireNonNull(target.getName())));
            return true;
        }

        if (sub.equals("sendall")) {
            if (args.length < 2) {
                if (isPlayer) MailManager.getInstance().mailSendAllGUI.open((Player) sender);
                else sender.sendMessage(LangManager.get(lang, "cmd.sendall.usage"));
                return true;
            }

            List<ItemStack> items = parseItems(args[1], lang);
            if (items.isEmpty()) {
                sender.sendMessage(LangManager.get(lang, "cmd.item.invalid").replace("%id%", args[1]));
                return true;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expire = now.plusDays(30);
            boolean onlineOnly = false, offlineOnly = false;

            if (args.length >= 3) {
                String lastArg = args[args.length - 1];
                if (lastArg.equalsIgnoreCase("-online")) onlineOnly = true;
                else if (lastArg.equalsIgnoreCase("-offline")) offlineOnly = true;

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

                Mail mail = new Mail(senderId, target.getUniqueId(), items, now, expire);
                MailDataManager.getInstance().addMail(mail);
                count++;
            }

            sender.sendMessage(LangManager.get(lang, "cmd.sendall.success").replace("%count%", Integer.toString(count)));
            return true;
        }

        if (sub.equals("reload")) {
            Objects.requireNonNull(plugin).reloadConfig();
            ConfigManager.reload();
            LangManager.loadAll(plugin.getDataFolder());
            sender.sendMessage(LangManager.get(lang, "cmd.reload"));
            return true;
        }

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

        if (sub.equals("inv")) {
            if (!sender.hasPermission("mail.inv.manage")) {
                sender.sendMessage(LangManager.get(lang, "cmd.inv.no-permission"));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LangManager.get(lang, "cmd.inv.no-player"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(LangManager.get(lang, "cmd.inv.usage"));
                return true;
            }

            String action = args[1].toLowerCase();
            int id;
            try {
                id = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(LangManager.get(lang, "cmd.inv.not-number"));
                return true;
            }

            switch (action) {
                case "create" -> {
                    if (invMap.containsKey(id)) {
                        sender.sendMessage(LangManager.get(lang, "cmd.inv.exists").replace("%id%", String.valueOf(id)));
                        return true;
                    }
                    invMap.put(id, new ItemStack[36]);
                    saveInventories();
                    sender.sendMessage(LangManager.get(lang, "cmd.inv.created").replace("%id%", String.valueOf(id)));
                }
                case "edit" -> {
                    if (!invMap.containsKey(id)) {
                        sender.sendMessage(LangManager.get(lang, "cmd.inv.not-found").replace("%id%", String.valueOf(id)));
                        return true;
                    }
                    MailInventoryGUI gui = new MailInventoryGUI(id, true);
                    Bukkit.getPluginManager().registerEvents(gui, MailManager.getInstance());
                    gui.open(player);
                }
                case "open" -> {
                    if (!invMap.containsKey(id)) {
                        sender.sendMessage(LangManager.get(lang, "cmd.inv.not-found").replace("%id%", String.valueOf(id)));
                        return true;
                    }
                    MailInventoryGUI gui = new MailInventoryGUI(id, false);
                    Bukkit.getPluginManager().registerEvents(gui, MailManager.getInstance());
                    gui.open(player);
                }
                case "delete" -> {
                    if (!invMap.containsKey(id)) {
                        sender.sendMessage(LangManager.get(lang, "cmd.inv.not-found").replace("%id%", String.valueOf(id)));
                        return true;
                    }
                    invMap.remove(id);
                    saveInventories();
                    sender.sendMessage(LangManager.get(lang, "cmd.inv.deleted").replace("%id%", String.valueOf(id)));
                }
                default -> sender.sendMessage(LangManager.get(lang, "cmd.inv.usage"));
            }
            return true;
        }

        return false;
    }

    public static ItemStack[] getInventory(int id) {
        return invMap.getOrDefault(id, new ItemStack[36]);
    }

    public static void saveInventory(int id, ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack item = (i < contents.length) ? contents[i] : null;
            copy[i] = (item != null && item.getType() != Material.AIR) ? item.clone() : null;
        }
        invMap.put(id, copy);
        saveInventories();
    }

    private static void loadInventories() {
        if (!invFile.exists()) return;
        for (String key : invConfig.getKeys(false)) {
            int id;
            try { id = Integer.parseInt(key); } catch (NumberFormatException ignored) { continue; }
            List<ItemStack> list = (List<ItemStack>) invConfig.getList(key);
            if (list == null) continue;
            ItemStack[] arr = new ItemStack[36];
            for (int i = 0; i < Math.min(36, list.size()); i++) arr[i] = list.get(i);
            invMap.put(id, arr);
        }
    }

    private static void saveInventories() {
        for (Map.Entry<Integer, ItemStack[]> e : invMap.entrySet()) {
            invConfig.set(String.valueOf(e.getKey()), Arrays.asList(e.getValue()));
        }
        try { invConfig.save(invFile); } catch (IOException ex) { ex.printStackTrace(); }
    }

    private List<ItemStack> parseItems(String input, String lang) {
        List<ItemStack> result = new ArrayList<>(4);
        String[] parts = input.split(",");
        for (String raw : parts) {
            String part = raw.trim();
            if (part.isEmpty()) continue;

            if (part.regionMatches(true, 0, "inv:", 0, 4)) {
                try {
                    int invId = Integer.parseInt(part.substring(4).trim());
                    ItemStack[] arr = getInventory(invId);
                    for (ItemStack item : arr) {
                        if (item != null && item.getType() != Material.AIR) result.add(item.clone());
                    }
                } catch (Exception ignored) {}
                continue;
            }

            ItemStack direct = parseSingleItem(part);
            if (direct != null && direct.getType() != Material.AIR) {
                result.add(direct);
                continue;
            }

            int idx = part.lastIndexOf(':');
            if (idx > 0) {
                String base = part.substring(0, idx);
                String tail = part.substring(idx + 1);
                try {
                    int amount = Integer.parseInt(tail);
                    ItemStack baseItem = parseSingleItem(base);
                    if (baseItem != null && baseItem.getType() != Material.AIR) {
                        baseItem.setAmount(amount);
                        result.add(baseItem);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    private ItemStack parseSingleItem(String id) {
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
                String rest = id.substring(MODEL_PREFIX.length());
                String[] parts = rest.split(":");
                if (parts.length != 2) return null;
                Material material = Material.matchMaterial(parts[0].toUpperCase());
                if (material == null) return null;
                int customModelData = Integer.parseInt(parts[1]);
                return new ItemBuilder(material).customModelData(customModelData).build();
            }
            Material mat = Material.matchMaterial(id.toUpperCase());
            return (mat != null) ? new ItemStack(mat) : null;
        } catch (Exception e) { return null; }
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
        } catch (Exception e) { return null; }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        int len = args.length;
        String prefix = args[len - 1].toLowerCase();

        if (len == 1) return filterPrefix(SUB_COMMANDS, prefix);

        String sub = args[0].toLowerCase();

        if (sub.equals("inv")) {
            if (len == 2) return filterPrefix(Arrays.asList("create", "edit", "open", "delete"), prefix);
            if (len == 3) {
                List<String> ids = new ArrayList<>();
                for (Integer id : invMap.keySet()) {
                    String s = String.valueOf(id);
                    if (s.startsWith(prefix)) ids.add(s);
                }
                return ids;
            }
        }

        if (len == 2 && sub.equals("setlang"))
            return filterPrefix(LangManager.getAvailableLanguages(), prefix);

        if (len == 2 && sub.equals("send")) {
            List<String> players = new ArrayList<>();
            for (OfflinePlayer p : PlayerCache.getCachedPlayers()) {
                if (p.hasPlayedBefore()) {
                    String name = p.getName();
                    if (name != null && name.toLowerCase().startsWith(prefix)) players.add(name);
                }
            }
            return players;
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
                        String fullId = ITEMSADDER_PREFIX + namespacedId.toString();
                        if (fullId.toLowerCase().startsWith(prefix)) result.add(fullId);
                    }
                } catch (Exception ignored) {}
            }

            for (Material mat : Material.values()) {
                if (mat.isItem()) {
                    String name = mat.name().toLowerCase();
                    if (name.startsWith(prefix)) result.add(name);
                }
            }

            for (Integer invId : invMap.keySet()) {
                String fullId = "inv:" + invId;
                if (fullId.startsWith(prefix)) result.add(fullId);
            }

            return result;
        }

        if ((len == 3 && sub.equals("sendall")) || (len == 4 && sub.equals("send")))
            return filterPrefix(TIME_SUGGESTIONS, prefix);

        if (len >= 3 && (sub.equals("sendall") || sub.equals("send")))
            return filterPrefix(Arrays.asList("-online", "-offline"), prefix);

        return Collections.emptyList();
    }

    private List<String> filterPrefix(Collection<String> base, String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : base) if (s.startsWith(prefix)) out.add(s);
        return out;
    }
}
