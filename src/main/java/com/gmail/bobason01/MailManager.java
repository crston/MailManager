package com.gmail.bobason01;

import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.commands.MailCommand;
import com.gmail.bobason01.gui.*;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.mail.MailService;
import com.gmail.bobason01.task.MailReminderTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

public final class MailManager extends JavaPlugin {

    private static volatile MailManager instance;

    public static MailManager getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // 1. 언어 리소스 복사
        copyLangFile("en.yml");
        copyLangFile("ko.yml");

        // 2. 언어 파일 로드
        LangManager.loadAll(getDataFolder());

        // 3. 데이터 관리 초기화
        MailDataManager.getInstance().load(this);
        MailService.init(this);

        // 4. 플레이어 캐시 초기화
        PlayerCache.refresh(this, 0); // 즉시 초기화

        // 5. 명령어 등록
        MailCommand mailCommand = new MailCommand();
        Objects.requireNonNull(getCommand("mail")).setExecutor(mailCommand);
        Objects.requireNonNull(getCommand("mail")).setTabCompleter(mailCommand);

        // 6. 이벤트 리스너 등록
        registerListeners(
                new MailGUI(this),
                new MailSendGUI(this),
                new MailSendAllGUI(this),
                new MailTimeSelectGUI(this),
                new MailSettingGUI(this),
                new MailTargetSelectGUI(this),
                new BlacklistSelectGUI(this),
                new SendAllExcludeGUI(this)
        );

        // 7. 자동 저장 (5분 간격)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
                MailDataManager.getInstance().save(), 20L * 300, 20L * 300
        );

        // 8. 메일 리마인더 시작
        MailReminderTask.start(this);

        getLogger().info("[MailManager] Enabled successfully.");
    }

    private void registerListeners(Object... listeners) {
        for (Object listener : listeners) {
            Bukkit.getPluginManager().registerEvents((org.bukkit.event.Listener) listener, this);
        }
    }

    private void copyLangFile(String fileName) {
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists()) langFolder.mkdirs();

        File langFile = new File(langFolder, fileName);
        if (!langFile.exists()) {
            saveResource("lang/" + fileName, false);
        }
    }

    @Override
    public void onDisable() {
        MailDataManager.getInstance().unload();
    }
}
