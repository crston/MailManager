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
    public MailSelectGUI mailSelectGUI;
    public MailViewGUI mailViewGUI;
    public MailDeleteConfirmGUI mailDeleteConfirmGUI;

    @Override
    public void onEnable() {
        instance = this;

        // 1. 설정 로드
        ConfigManager.load(this);

        // 2. 언어 파일 복사 및 로드 (자동 업데이트 포함)
        copyLangFile("en_us.yml");
        copyLangFile("ko_kr.yml");
        copyLangFile("ja_jp.yml");
        copyLangFile("zh_cn.yml");
        copyLangFile("zh_tw.yml");

        // 플러그인 인스턴스를 넘겨주어 리소스 비교 후 누락된 키 자동 추가
        LangManager.loadAll(this);

        // 3. 데이터 및 서비스 초기화
        MailDataManager.getInstance().load(this);
        MailService.init(this);

        // 4. GUI 초기화
        mailGUI = new MailGUI(this);
        mailSendGUI = new MailSendGUI(this);
        mailSendAllGUI = new MailSendAllGUI(this);
        mailSettingGUI = new MailSettingGUI();
        mailTimeSelectGUI = new MailTimeSelectGUI(this);
        mailTargetSelectGUI = new MailTargetSelectGUI(this);
        blacklistSelectGUI = new BlacklistSelectGUI(this);
        sendAllExcludeGUI = new SendAllExcludeGUI(this);
        languageSelectGUI = new LanguageSelectGUI(this);
        mailAttachGUI = new MailAttachGUI(this);
        mailSelectGUI = new MailSelectGUI(this);
        mailViewGUI = new MailViewGUI(this);
        mailDeleteConfirmGUI = new MailDeleteConfirmGUI(this);

        // 5. 플레이어 캐시 갱신 태스크 시작
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> PlayerCache.refresh(this), 0L, 6000L);

        // 6. 명령어 등록
        MailCommand mailCommand = new MailCommand();
        PluginCommand command = Objects.requireNonNull(getCommand("mail"), "mail command not found");
        command.setExecutor(mailCommand);
        command.setTabCompleter(mailCommand);

        // 7. 이벤트 리스너 등록
        registerListeners(
                new MailLoginListener(this),
                mailGUI,
                mailSendGUI,
                mailSendAllGUI,
                mailSettingGUI,
                mailTimeSelectGUI,
                mailTargetSelectGUI,
                blacklistSelectGUI,
                sendAllExcludeGUI,
                languageSelectGUI,
                mailAttachGUI,
                mailSelectGUI,
                mailViewGUI,
                mailDeleteConfirmGUI
        );

        // 8. 알림 태스크 시작
        MailReminderTask.start(this);

        getLogger().info("[MailManager] Enabled successfully.");
    }

    private void registerListeners(Listener... listeners) {
        for (int i = 0; i < listeners.length; i++) {
            Bukkit.getPluginManager().registerEvents(listeners[i], this);
        }
    }

    private void copyLangFile(String fileName) {
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists() && !langFolder.mkdirs()) return;
        File langFile = new File(langFolder, fileName);
        if (!langFile.exists()) saveResource("lang/" + fileName, false);
    }

    @Override
    public void onDisable() {
        // 데이터 저장 및 DB 연결 종료
        MailDataManager.getInstance().unload();
        MailService.shutdown();
        getLogger().info("[MailManager] Disabled successfully.");
    }
}