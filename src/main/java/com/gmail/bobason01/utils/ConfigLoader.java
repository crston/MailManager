package com.gmail.bobason01.utils;

import com.gmail.bobason01.MailManager;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigLoader {

    private final MailManager plugin;

    public ConfigLoader(MailManager plugin) {
        this.plugin = plugin;
    }

    public void reloadConfig() {
        plugin.reloadConfig();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public String getString(String path) {
        return plugin.getConfig().getString(path);
    }

    public int getInt(String path) {
        return plugin.getConfig().getInt(path);
    }

    public boolean getBoolean(String path) {
        return plugin.getConfig().getBoolean(path);
    }

    public double getDouble(String path) {
        return plugin.getConfig().getDouble(path);
    }
}
