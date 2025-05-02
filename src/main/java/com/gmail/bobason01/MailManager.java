package com.gmail.bobason01;

import com.gmail.bobason01.gui.*;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ConfigLoader;
import com.gmail.bobason01.utils.LangUtil;
import com.gmail.bobason01.utils.MailNotifySettingsManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MailManager extends JavaPlugin {

    private static MailManager instance;
    private ConfigLoader configLoader;

    public static MailManager getInstance() {
        return instance;
    }

    public ConfigLoader getConfigLoader() {
        return configLoader;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        configLoader = new ConfigLoader(this);
        LangUtil.load(this);
        MailNotifySettingsManager.load();
        MailDataManager.loadAll();

        getCommand("mail").setExecutor(new MailCommand());

        registerEvents();
        getLogger().info("[MailManager] 메일 시스템 활성화됨.");
    }

    @Override
    public void onDisable() {
        MailDataManager.saveAll();
        MailNotifySettingsManager.save();
        getLogger().info("[MailManager] 메일 시스템 비활성화됨.");
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(new MailGUI(this), this);
        getServer().getPluginManager().registerEvents(new MailSendGUI(this), this);
        getServer().getPluginManager().registerEvents(new MailSendAllGUI(this), this);
        getServer().getPluginManager().registerEvents(new MailTimeSelectGUI(this, null), this);
        getServer().getPluginManager().registerEvents(new MailTargetSelectGUI(this, null), this);
        getServer().getPluginManager().registerEvents(new MailSettingGUI(this), this);
        getServer().getPluginManager().registerEvents(new BlacklistSelectGUI(this), this);
        getServer().getPluginManager().registerEvents(new SendAllExcludeGUI(this), this);
    }
}
