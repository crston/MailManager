package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.mail.Mail;
import com.gmail.bobason01.mail.MailDataManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MailDeleteConfirmGUI implements Listener {

    private static final int SIZE = 27;
    private static final int YES_SLOT = 11;
    private static final int NO_SLOT = 15;
    private final Plugin plugin;

    public MailDeleteConfirmGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    // [보안] 데이터를 안전하게 담기 위한 커스텀 홀더
    private static class DeleteHolder implements InventoryHolder {
        private final Inventory inv;
        private final List<Mail> targetMails;

        public DeleteHolder(List<Mail> mails, String title) {
            this.targetMails = new ArrayList<>(mails);
            this.inv = Bukkit.createInventory(this, SIZE, title);
        }

        @Override
        public @NotNull Inventory getInventory() { return inv; }
        public List<Mail> getTargetMails() { return targetMails; }
    }

    public void open(Player player, List<Mail> mails) {
        if (mails == null || mails.isEmpty()) return;

        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);

        // 커스텀 홀더 생성
        DeleteHolder holder = new DeleteHolder(mails, LangManager.get(lang, "gui.delete.title"));
        Inventory inv = holder.getInventory();

        // YES 버튼
        inv.setItem(YES_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.DELETE_GUI_CONFIRM_BUTTON))
                .name(LangManager.get(lang, "gui.delete.yes_name"))
                .lore(LangManager.getList(lang, "gui.delete.yes_lore"))
                .build());

        // NO 버튼
        inv.setItem(NO_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.DELETE_GUI_CANCEL_BUTTON))
                .name(LangManager.get(lang, "gui.delete.no_name"))
                .lore(LangManager.getList(lang, "gui.delete.no_lore"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof DeleteHolder holder)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);

        if (slot == YES_SLOT) {
            List<Mail> mailsToDelete = holder.getTargetMails();
            MailDataManager manager = MailDataManager.getInstance();

            // [최적화] 대량 삭제 처리
            for (Mail mail : mailsToDelete) {
                manager.removeMail(mail);
            }

            // flushNow() 대신 비동기 저장을 유도하거나, 명시적으로 한 번만 수행
            // (이미 manager 내부에서 삭제 처리가 메모리에 반영되므로 flush는 선택사항입니다.)

            // 메시지 변수 치환 (%count% 사용)
            String msgKey = (mailsToDelete.size() > 1) ? "mail.deleted_multi" : "mail.deleted";
            player.sendMessage(LangManager.get(lang, msgKey).replace("%count%", String.valueOf(mailsToDelete.size())));

            ConfigManager.playSound(player, ConfigManager.SoundType.MAIL_DELETE_SUCCESS);
            player.closeInventory();

            // 약간의 딜레이 후 메인 우편함 재오픈 (데이터 갱신 반영을 위해)
            Bukkit.getScheduler().runTaskLater(plugin, () -> MailManager.getInstance().mailGUI.open(player), 2L);

        } else if (slot == NO_SLOT) {
            player.sendMessage(LangManager.get(lang, "mail.delete_cancel"));
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            player.closeInventory();

            Bukkit.getScheduler().runTaskLater(plugin, () -> MailManager.getInstance().mailGUI.open(player), 2L);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof DeleteHolder) {
            e.setCancelled(true);
        }
    }
}