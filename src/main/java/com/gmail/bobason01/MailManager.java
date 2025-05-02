package com.gmail.bobason01;

import com.gmail.bobason01.gui.*;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ConfigLoader;
import com.gmail.bobason01.utils.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

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
        // 설정 파일 로딩
        saveDefaultConfig();
        ConfigLoader.load(this);

        // 다국어 메시지 로딩
        LangUtil.load(this);

        // 메일 데이터 로드
        MailDataManager.getInstance().load(this);

        // 명령어 등록
        Objects.requireNonNull(getCommand("mail")).setExecutor(new MailCommand());
        Objects.requireNonNull(getCommand("mail")).setTabCompleter(new MailCommand());

        // GUI 이벤트 등록
        registerListeners();
    }

    @Override
    public void onDisable() {
        MailDataManager.getInstance().save(this);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new MailGUI(this), this);
        Bukkit.getPluginManager().registerEvents(new MailSendGUI(this), this);
        Bukkit.getPluginManager().registerEvents(new MailSendAllGUI(this), this);
        Bukkit.getPluginManager().registerEvents(new SendAllExcludeGUI(this), this);
        Bukkit.getPluginManager().registerEvents(new MailTargetSelectGUI(this), this);
        Bukkit.getPluginManager().registerEvents(new MailTimeSelectGUI(this), this);
        Bukkit.getPluginManager().registerEvents(new MailSettingGUI(this), this);
        Bukkit.getPluginManager().registerEvents(new BlacklistSelectGUI(this), this);
    }
}
