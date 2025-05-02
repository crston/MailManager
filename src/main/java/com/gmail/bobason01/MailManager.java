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
        // 1. 설정 및 다국어 파일 로드
        saveDefaultConfig();
        ConfigLoader.load(this);
        LangUtil.load(this);

        // 2. 메일 데이터 초기화 (plugin 참조 저장 포함)
        MailDataManager.getInstance().init(this);

        // 3. 명령어 등록
        registerCommands();

        // 4. GUI 이벤트 등록
        registerListeners();
    }

    @Override
    public void onDisable() {
        // 서버 종료 시 메일 저장
        MailDataManager.getInstance().save();
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("mail")).setExecutor(new MailCommand());
        Objects.requireNonNull(getCommand("mail")).setTabCompleter(new MailCommand());
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
