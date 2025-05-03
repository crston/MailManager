package com.gmail.bobason01;

import com.gmail.bobason01.gui.*;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ConfigLoader;
import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class MailManager extends JavaPlugin {

    private static MailManager instance;

    public static MailManager getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        initConfig();
        initLanguage();
        initMailSystem();
        registerCommands();
        registerListeners();
    }

    @Override
    public void onDisable() {
        MailDataManager.getInstance().save();
    }

    private void initConfig() {
        saveDefaultConfig();
        ConfigLoader.load(this);
    }

    private void initLanguage() {
        LangUtil.load(this);
    }

    private void initMailSystem() {
        MailDataManager.getInstance().init(this);
    }

    private void registerCommands() {
        var command = getCommand("mail");
        if (command == null) {
            getLogger().severe("Command 'mail' is not registered in plugin.yml!");
            return;
        }
        command.setExecutor(new MailCommand());
        command.setTabCompleter(new MailCommand());
    }

    private void registerListeners() {
        register(new MailGUI(this));
        register(new MailSendGUI(this));
        register(new MailSendAllGUI(this));
        register(new SendAllExcludeGUI(this));
        register(new MailTargetSelectGUI(this));
        register(new MailTimeSelectGUI(this));
        register(new MailSettingGUI(this));
        register(new BlacklistSelectGUI(this));
    }

    private void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, this);
    }
}
