package com.gmail.bobason01;

import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.commands.MailCommand;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.gui.*;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.listeners.MailLoginListener;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.task.MailReminderTask;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

public final class MailManager extends JavaPlugin {

    private static MailManager instance;

    public MailGUI mailGUI;
    public MailSendGUI mailSendGUI;
    public MailSendAllGUI mailSendAllGUI;
    public MailSettingGUI mailSettingGUI;
    public MailTimeSelectGUI mailTimeSelectGUI;
    public MailTargetSelectGUI mailTargetSelectGUI;
    public BlacklistSelectGUI blacklistSelectGUI;
    public SendAllExcludeGUI sendAllExcludeGUI;
    public LanguageSelectGUI languageSelectGUI;
    public MailAttachGUI mailAttachGUI;
    public MailViewGUI mailViewGUI;

    public static MailManager getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        ConfigManager.load(this);
        copyLangFile("en_us.yml");
        copyLangFile("ko_kr.yml");
        copyLangFile("ja_jp.yml");
        copyLangFile("zh_cn.yml");
        copyLangFile("zh_tw.yml");
        LangManager.loadAll(getDataFolder());
        MailDataManager.getInstance().load(this);
        MailService.init(this);

        this.mailSendGUI = new MailSendGUI(this);
        this.mailSendAllGUI = new MailSendAllGUI(this);
        this.mailSettingGUI = new MailSettingGUI(this);
        this.mailTimeSelectGUI = new MailTimeSelectGUI(this);
        this.mailTargetSelectGUI = new MailTargetSelectGUI(this);
        this.blacklistSelectGUI = new BlacklistSelectGUI(this);
        this.sendAllExcludeGUI = new SendAllExcludeGUI(this);
        this.languageSelectGUI = new LanguageSelectGUI(this);
        this.mailGUI = new MailGUI(this);
        this.mailAttachGUI = new MailAttachGUI(this);
        this.mailViewGUI = new MailViewGUI(this);


        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> PlayerCache.refresh(this), 0L, 20L * 300);

        MailCommand mailCommand = new MailCommand();
        PluginCommand command = Objects.requireNonNull(getCommand("mail"));
        command.setExecutor(mailCommand);
        command.setTabCompleter(mailCommand);

        registerListeners(
                mailGUI, mailSendGUI, mailSendAllGUI,
                mailSettingGUI, mailTimeSelectGUI, mailTargetSelectGUI,
                blacklistSelectGUI, sendAllExcludeGUI, languageSelectGUI, mailAttachGUI, mailViewGUI,
                new MailLoginListener(this)
        );

        long autoSaveInterval = 20L * getConfig().getLong("auto-save-interval", 300);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> MailDataManager.getInstance().flush(), autoSaveInterval, autoSaveInterval);

        MailReminderTask.start(this);

        getLogger().info("[MailManager] Enabled successfully.");
    }

    private void registerListeners(Listener... listeners) {
        for (Listener listener : listeners) {
            Bukkit.getPluginManager().registerEvents(listener, this);
        }
    }

    private void copyLangFile(String fileName) {
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            getLogger().warning("Could not create lang folder.");
            return;
        }

        File langFile = new File(langFolder, fileName);
        if (!langFile.exists()) {
            saveResource("lang/" + fileName, false);
        }
    }

    @Override
    public void onDisable() {
        MailDataManager.getInstance().unload();
        getLogger().info("[MailManager] Disabled successfully.");
    }
}