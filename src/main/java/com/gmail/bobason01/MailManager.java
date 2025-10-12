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

    public static MailManager getInstance() {
        return instance;
    }

    // GUI 인스턴스들
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
    public MailSelectGUI mailSelectGUI;
    public MailDeleteConfirmGUI mailDeleteConfirmGUI;

    @Override
    public void onEnable() {
        instance = this;

        // 설정 및 언어 로드
        ConfigManager.load(this);
        copyLangFile("en_us.yml");
        copyLangFile("ko_kr.yml");
        copyLangFile("ja_jp.yml");
        copyLangFile("zh_cn.yml");
        copyLangFile("zh_tw.yml");
        LangManager.loadAll(getDataFolder());

        // 메일 데이터 및 서비스 초기화
        MailDataManager.getInstance().load(this);
        MailService.init(this);

        // GUI 인스턴스 생성 (ConfigManager 로드 이후)
        mailGUI = new MailGUI(this);
        mailSendGUI = new MailSendGUI(this);
        mailSendAllGUI = new MailSendAllGUI(this);
        mailSettingGUI = new MailSettingGUI(this);
        mailTimeSelectGUI = new MailTimeSelectGUI(this);
        mailTargetSelectGUI = new MailTargetSelectGUI(this);
        blacklistSelectGUI = new BlacklistSelectGUI(this);
        sendAllExcludeGUI = new SendAllExcludeGUI(this);
        languageSelectGUI = new LanguageSelectGUI(this);
        mailAttachGUI = new MailAttachGUI(this);
        mailViewGUI = new MailViewGUI(this);
        mailSelectGUI = new MailSelectGUI(this);
        mailDeleteConfirmGUI = new MailDeleteConfirmGUI(this);

        // 플레이어 캐시 갱신 (5분 주기)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> PlayerCache.refresh(this), 0L, 20L * 300);

        // 명령어 등록
        MailCommand mailCommand = new MailCommand();
        PluginCommand command = Objects.requireNonNull(getCommand("mail"), "mail command not found");
        command.setExecutor(mailCommand);
        command.setTabCompleter(mailCommand);

        // 이벤트 리스너 등록
        registerListeners(
                mailGUI, mailSendGUI, mailSendAllGUI,
                mailSettingGUI, mailTimeSelectGUI, mailTargetSelectGUI,
                blacklistSelectGUI, sendAllExcludeGUI, languageSelectGUI,
                mailAttachGUI, mailViewGUI, mailSelectGUI,
                new MailLoginListener(this),
                mailDeleteConfirmGUI
        );

        // 자동 저장
        long autoSaveInterval = 20L * getConfig().getLong("auto-save-interval", 300);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> MailDataManager.getInstance().flush(), autoSaveInterval, autoSaveInterval);

        // 메일 리마인더 시작
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
