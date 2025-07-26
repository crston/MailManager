package com.gmail.bobason01;

import com.gmail.bobason01.cache.PlayerCache;
import com.gmail.bobason01.commands.MailCommand;
import com.gmail.bobason01.gui.*;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.mail.MailService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class MailManager extends JavaPlugin {

    private static volatile MailManager instance;

    public static MailManager getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // 2. 데이터 관리 초기화
        MailDataManager.getInstance().load(this);
        MailService.init(this);

        // 3. 플레이어 캐시 초기화
        PlayerCache.refresh(this, 0); // 즉시 초기화

        // 4. 명령어 등록
        MailCommand mailCommand = new MailCommand();
        Objects.requireNonNull(getCommand("mail")).setExecutor(mailCommand);
        Objects.requireNonNull(getCommand("mail")).setTabCompleter(mailCommand);

        // 5. 이벤트 리스너 등록
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

        // 6. 자동 저장 (5분 간격)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
                MailDataManager.getInstance().save(), 20L * 300, 20L * 300
        );

        getLogger().info("[MailManager] Enabled successfully.");
    }

    private void registerListeners(Object... listeners) {
        for (Object listener : listeners) {
            Bukkit.getPluginManager().registerEvents((org.bukkit.event.Listener) listener, this);
        }
    }

    @Override
    public void onDisable() {
        MailDataManager.getInstance().unload();
    }
}
