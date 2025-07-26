package com.gmail.bobason01.storage;

import com.gmail.bobason01.mail.Mail;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MailFileStorage {

    private static final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private static final String DATA_FOLDER_NAME = "data";

    public static void loadAllAsync(Plugin plugin,
                                    Map<UUID, List<Mail>> inboxMap,
                                    Map<UUID, Set<UUID>> blacklistMap,
                                    Set<UUID> notifyDisabled,
                                    Map<UUID, Set<UUID>> excludeMap,
                                    Runnable onComplete) {

        File folder = new File(plugin.getDataFolder(), DATA_FOLDER_NAME);
        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") && name.length() == 40);
        if (files == null || files.length == 0) {
            Bukkit.getScheduler().runTask(plugin, onComplete);
            return;
        }

        ExecutorService loaderExecutor = Executors.newFixedThreadPool(Math.min(8, files.length));
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (File file : files) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    String fileName = file.getName();
                    UUID uuid = UUID.fromString(fileName.substring(0, fileName.length() - 4));

                    FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                    List<Mail> inbox = new ArrayList<>();

                    for (String key : config.getKeys(false)) {
                        String base = key + ".";
                        if (!config.contains(base + "sender")) continue;

                        try {
                            UUID sender = UUID.fromString(config.getString(base + "sender"));
                            UUID receiver = UUID.fromString(config.getString(base + "receiver"));
                            ItemStack item = config.getItemStack(base + "item");
                            LocalDateTime sentAt = LocalDateTime.parse(config.getString(base + "sentAt"));
                            LocalDateTime expireAt = LocalDateTime.parse(config.getString(base + "expireAt"));

                            if (expireAt.isBefore(now)) continue;
                            if (item != null) {
                                inbox.add(new Mail(sender, receiver, item, sentAt, expireAt));
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Malformed mail entry in file: " + file.getName() + ", key: " + key, e);
                        }
                    }

                    synchronized (inboxMap) {
                        inboxMap.put(uuid, inbox);
                        blacklistMap.put(uuid, config.getStringList("blacklist").stream()
                                .map(UUID::fromString).collect(Collectors.toSet()));
                        excludeMap.put(uuid, config.getStringList("exclude").stream()
                                .map(UUID::fromString).collect(Collectors.toSet()));
                    }

                    if (config.getBoolean("notifyDisabled")) {
                        synchronized (notifyDisabled) {
                            notifyDisabled.add(uuid);
                        }
                    }

                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to load mail file: " + file.getName(), e);
                }
            }, loaderExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, throwable) -> {
                    loaderExecutor.shutdown();
                    Bukkit.getScheduler().runTask(plugin, onComplete);
                });
    }

    public static CompletableFuture<Void> saveAsync(Plugin plugin, UUID uuid, List<Mail> inbox,
                                                    Collection<UUID> blacklist,
                                                    Collection<UUID> exclude,
                                                    boolean notifyDisabled) {
        return CompletableFuture.runAsync(() -> {
            File folder = new File(plugin.getDataFolder(), DATA_FOLDER_NAME);
            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder, uuid.toString() + ".yml");
            FileConfiguration config = new YamlConfiguration();
            LocalDateTime now = LocalDateTime.now();

            int index = 0;
            for (Mail mail : inbox) {
                if (mail.getExpireAt().isBefore(now)) continue;

                String path = index++ + ".";
                config.set(path + "sender", mail.getSender().toString());
                config.set(path + "receiver", mail.getReceiver().toString());
                config.set(path + "item", mail.getItem());
                config.set(path + "sentAt", mail.getSentAt().toString());
                config.set(path + "expireAt", mail.getExpireAt().toString());
            }

            config.set("blacklist", blacklist.stream().map(UUID::toString).collect(Collectors.toList()));
            config.set("exclude", exclude.stream().map(UUID::toString).collect(Collectors.toList()));
            config.set("notifyDisabled", notifyDisabled);

            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save mail data for player: " + uuid, e);
            }
        }, saveExecutor);
    }
}
