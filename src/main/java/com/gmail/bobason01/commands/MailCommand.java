package com.gmail.bobason01.commands;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.gui.MailInventoryGUI;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import dev.lone.itemsadder.api.CustomStack;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

    private static final List<String> SUB_COMMANDS = Arrays.asList("send", "sendall", "reload", "setlang", "inv", "reset");
    private static final List<String> TIME_SUGGESTIONS = Arrays.asList("7d", "12h", "30m", "15s", "1h", "5m");

    private static final Map<Integer, ItemStack[]> invMap = new HashMap<>(32);
    private static final File invFile = new File(MailManager.getInstance().getDataFolder(), "data/inventories.yml");
    private static final FileConfiguration invConfig = YamlConfiguration.loadConfiguration(invFile);

    static { loadInventories(); }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        UUID senderId = (sender instanceof Player p) ? p.getUniqueId() : SERVER_UUID;
        Plugin plugin = MailManager.getInstance();
        String lang = (sender instanceof Player p) ? LangManager.getLanguage(p.getUniqueId()) : "ko_kr";

        if (args.length == 0) {
            if (sender instanceof Player player) MailManager.getInstance().mailGUI.open(player);
            else sender.sendMessage(LangManager.get(lang, "cmd.gui.unavailable"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean isPlayer = sender instanceof Player;

        if (sub.equals("send")) {
            if (args.length < 3) {
                if (!sender.hasPermission("mail.send")) {
                    sender.sendMessage(LangManager.get(lang, "cmd.send.no-permission"));
                    return true;
                }
                if (isPlayer) MailManager.getInstance().mailSendGUI.open((Player) sender);
                else sender.sendMessage(LangManager.get(lang, "cmd.send.usage"));
                return true;
            } else {
                if (!sender.hasPermission("mail.admin")) {
                    sender.sendMessage(LangManager.get(lang, "cmd.inv.no-permission"));
                    return true;
                }

                String targetName = args[1];
                final UUID finalSenderId = senderId;

                MailDataManager.getInstance().getGlobalUUID(targetName).thenAccept(targetUUID -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player currentSender = Bukkit.getPlayer(finalSenderId);
                        if (currentSender != null && !currentSender.hasPermission("mail.admin")) {
                            currentSender.sendMessage(LangManager.get(lang, "cmd.inv.no-permission"));
                            return;
                        }

                        if (targetUUID == null) {
                            sender.sendMessage(LangManager.get(lang, "player.notfound").replace("%name%", targetName));
                            return;
                        }

                        List<ItemStack> items = parseItems(args[2], lang);
                        if (items.isEmpty()) {
                            sender.sendMessage(LangManager.get(lang, "item.invalid").replace("%id%", args[2]));
                            return;
                        }

                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime expire = (args.length >= 4) ? parseExpireTime(args[3], now) : now.plusDays(30);

                        Mail mail = new Mail(finalSenderId, targetUUID, items, now, expire);
                        MailDataManager.getInstance().addMail(mail);
                        sender.sendMessage(LangManager.get(lang, "cmd.send.success").replace("%name%", targetName));
                    });
                });
                return true;
            }
        }

        if (!sender.hasPermission("mail.admin")) {
            sender.sendMessage(LangManager.get(lang, "cmd.inv.no-permission"));
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
                sender.sendMessage(LangManager.get(lang, "item.invalid").replace("%id%", args[1]));
                return true;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expire = (args.length >= 3) ? parseExpireTime(args[2], now) : now.plusDays(30);

            sender.sendMessage(LangManager.get(lang, "mail.sendall.start"));

            MailDataManager.getInstance().getAllGlobalUUIDsAsync().thenAccept(targets -> {
                int count = 0;
                for (UUID targetUUID : targets) {
                    if (targetUUID.equals(senderId)) continue;
                    MailDataManager.getInstance().addMail(new Mail(senderId, targetUUID, items, now, expire));
                    count++;
                }
                final int finalCount = count;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(LangManager.get(lang, "cmd.sendall.success").replace("%count%", Integer.toString(finalCount)));
                });
            });
            return true;
        }

        if (sub.equals("reset")) {
            if (args.length < 2) {
                sender.sendMessage(LangManager.get(lang, "cmd.reset.usage"));
                return true;
            }
            String targetName = args[1];
            MailDataManager.getInstance().getGlobalUUID(targetName).thenAccept(targetUUID -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (targetUUID != null) {
                        MailDataManager.getInstance().resetPlayerMails(targetUUID);
                        sender.sendMessage(LangManager.get(lang, "cmd.reset.success").replace("%name%", targetName));
                    }
                });
            });
            return true;
        }

        if (sub.equals("reload")) {
            plugin.reloadConfig();
            ConfigManager.reload();
            LangManager.loadAll(MailManager.getInstance());
            sender.sendMessage(LangManager.get(lang, "cmd.reload"));
            return true;
        }

        if (sub.equals("setlang")) {
            if (!isPlayer || args.length < 2) return true;
            String targetLang = args[1].toLowerCase(Locale.ROOT);
            if (LangManager.getAvailableLanguages().contains(targetLang)) {
                LangManager.setLanguage(senderId, targetLang);
                sender.sendMessage(LangManager.get(senderId, "cmd.setlang.success").replace("%lang%", targetLang));
            }
            return true;
        }

        if (sub.equals("inv")) {
            if (!isPlayer || args.length < 3) return true;
            String action = args[1].toLowerCase(Locale.ROOT);
            int id;
            try { id = Integer.parseInt(args[2]); } catch (Exception e) { return true; }

            switch (action) {
                case "create" -> {
                    if (!invMap.containsKey(id)) {
                        invMap.put(id, new ItemStack[36]);
                        saveInventories();
                        sender.sendMessage(LangManager.get(lang, "cmd.inv.created").replace("%id%", String.valueOf(id)));
                    }
                }
                case "edit" -> {
                    if (invMap.containsKey(id)) new MailInventoryGUI(id, true).open((Player) sender);
                }
                case "delete" -> {
                    invMap.remove(id);
                    invConfig.set(String.valueOf(id), null);
                    saveInventories();
                    sender.sendMessage(LangManager.get(lang, "cmd.inv.deleted").replace("%id%", String.valueOf(id)));
                }
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
            try {
                int id = Integer.parseInt(key);
                List<ItemStack> list = (List<ItemStack>) invConfig.getList(key);
                if (list != null) invMap.put(id, list.toArray(new ItemStack[0]));
            } catch (Exception ignored) {}
        }
    }

    private static void saveInventories() {
        for (Map.Entry<Integer, ItemStack[]> e : invMap.entrySet()) {
            invConfig.set(String.valueOf(e.getKey()), Arrays.asList(e.getValue()));
        }
        try { invConfig.save(invFile); } catch (IOException ignored) {}
    }

    private List<ItemStack> parseItems(String input, String lang) {
        String[] parts = input.split(",");
        List<ItemStack> result = new ArrayList<>();
        for (String raw : parts) {
            String part = raw.trim();
            if (part.isEmpty()) continue;

            if (part.startsWith("inv:")) {
                try {
                    int id = Integer.parseInt(part.substring(4));
                    for (ItemStack item : getInventory(id)) {
                        if (item != null && item.getType() != Material.AIR) result.add(item.clone());
                    }
                } catch (Exception ignored) {}
                continue;
            }

            int amount = 1;
            String itemName = part;

            int amountIdx = part.lastIndexOf(':');
            if (amountIdx > 0) {
                String potentialAmount = part.substring(amountIdx + 1);
                String potentialName = part.substring(0, amountIdx);

                if (potentialAmount.matches("\\d+")) {
                    if (!(potentialName.contains(MMOITEMS_PREFIX) || potentialName.contains(ITEMSADDER_PREFIX))) {
                        amount = Integer.parseInt(potentialAmount);
                        itemName = potentialName;
                    } else if (potentialName.chars().filter(ch -> ch == ':').count() == 1 && potentialName.startsWith(MMOITEMS_PREFIX)) {
                        amount = Integer.parseInt(potentialAmount);
                        itemName = potentialName;
                    }
                }
            }

            ItemStack item = parseSingleItem(itemName);
            if (item != null) {
                item.setAmount(amount);
                result.add(item);
            }
        }
        return result;
    }

    private ItemStack parseSingleItem(String id) {
        try {
            if (id.startsWith(MMOITEMS_PREFIX) && Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                String content = id.substring(MMOITEMS_PREFIX.length());
                String[] split = content.split("\\.");
                if (split.length < 2) return null;
                Type type = MMOItems.plugin.getTypes().get(split[0].toUpperCase());
                if (type == null) return null;
                MMOItem mmo = MMOItems.plugin.getMMOItem(type, split[1].toUpperCase());
                return mmo != null ? mmo.newBuilder().build() : null;
            }

            if (id.startsWith(ITEMSADDER_PREFIX) && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
                CustomStack cs = CustomStack.getInstance(id.substring(ITEMSADDER_PREFIX.length()));
                return cs != null ? cs.getItemStack() : null;
            }

            if (id.startsWith(MODEL_PREFIX)) {
                String content = id.substring(MODEL_PREFIX.length());
                String[] split = content.split("\\.");
                if (split.length < 2) return null;
                Material mat = Material.matchMaterial(split[0].toUpperCase());
                if (mat == null) return null;
                int modelData = Integer.parseInt(split[1]);
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(modelData);
                    item.setItemMeta(meta);
                }
                return item;
            }

            Material mat = Material.matchMaterial(id.toUpperCase());
            return mat != null ? new ItemStack(mat) : null;
        } catch (Exception e) { return null; }
    }

    private LocalDateTime parseExpireTime(String input, LocalDateTime now) {
        try {
            if (input.startsWith("-")) return now.plusDays(30);
            int val = Integer.parseInt(input.substring(0, input.length() - 1));
            char unit = input.charAt(input.length() - 1);
            return switch (unit) {
                case 'd' -> now.plusDays(val);
                case 'h' -> now.plusHours(val);
                case 'm' -> now.plusMinutes(val);
                case 's' -> now.plusSeconds(val);
                default -> now.plusDays(30);
            };
        } catch (Exception e) { return now.plusDays(30); }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        int len = args.length;
        String prefix = args[len - 1].toLowerCase(Locale.ROOT);

        if (len == 1) return filterPrefix(SUB_COMMANDS, prefix);

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("inv") && len == 2) {
            if (!sender.hasPermission("mail.admin")) return Collections.emptyList();
            return filterPrefix(Arrays.asList("create", "edit", "delete"), prefix);
        }

        if (sub.equals("setlang") && len == 2) return filterPrefix(new ArrayList<>(LangManager.getAvailableLanguages()), prefix);

        if (len == 2 && (sub.equals("send") || sub.equals("reset"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(prefix)).toList();
        }

        if ((len == 2 && sub.equals("sendall")) || (len == 3 && sub.equals("send"))) {
            // 보안 강화: 아이템 목록 조회는 mail.admin 권한이 있을 때만 허용
            if (!sender.hasPermission("mail.admin")) return Collections.emptyList();

            List<String> result = new ArrayList<>();

            if (prefix.startsWith(MMOITEMS_PREFIX) && Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                String currentInput = prefix.substring(MMOITEMS_PREFIX.length());
                for (Type type : MMOItems.plugin.getTypes().getAll()) {
                    String typeName = type.getId().toLowerCase();
                    if (!currentInput.contains(".")) {
                        result.add(MMOITEMS_PREFIX + typeName + ".");
                    } else {
                        String[] split = currentInput.split("\\.");
                        if (split[0].equalsIgnoreCase(typeName)) {
                            MMOItems.plugin.getTemplates().getTemplates(type).forEach(template -> {
                                result.add(MMOITEMS_PREFIX + typeName + "." + template.getId().toLowerCase());
                            });
                        }
                    }
                }
                return filterPrefix(result, prefix);
            }

            if (prefix.startsWith(ITEMSADDER_PREFIX) && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
                for (String namespacedId : CustomStack.getNamespacedIdsInRegistry()) {
                    result.add(ITEMSADDER_PREFIX + namespacedId.toLowerCase());
                }
                return filterPrefix(result, prefix);
            }

            for (Material mat : Material.values()) {
                if (mat.isItem()) result.add(mat.name().toLowerCase());
            }
            for (Integer invId : invMap.keySet()) {
                result.add("inv:" + invId);
            }

            if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) result.add(MMOITEMS_PREFIX);
            if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) result.add(ITEMSADDER_PREFIX);
            result.add(MODEL_PREFIX);

            return filterPrefix(result, prefix);
        }

        if ((len == 3 && sub.equals("sendall")) || (len == 4 && sub.equals("send"))) {
            if (!sender.hasPermission("mail.admin")) return Collections.emptyList();
            return filterPrefix(TIME_SUGGESTIONS, prefix);
        }

        return Collections.emptyList();
    }

    private List<String> filterPrefix(Collection<String> base, String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : base) if (s.toLowerCase().startsWith(prefix)) out.add(s);
        return out;
    }
}