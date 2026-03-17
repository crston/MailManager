package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LanguageSelectGUI implements Listener, InventoryHolder {

    private static final int GUI_SIZE = 27;
    private static final int BACK_SLOT = 26; // 뒤로가기 버튼 위치 추가
    private final Plugin plugin;
    private final NamespacedKey langKey;

    public LanguageSelectGUI(Plugin plugin) {
        this.plugin = plugin;
        this.langKey = new NamespacedKey(plugin, "lang_code");
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, GUI_SIZE, "Language Selection");
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String currentLang = LangManager.getLanguage(uuid);
        List<String> sortedLangs = new ArrayList<>(LangManager.getAvailableLanguages());
        sortedLangs.sort(String::compareToIgnoreCase);

        String lang = LangManager.getLanguage(uuid);
        String guiTitle = LangManager.get(uuid, "gui.language.title");
        Inventory inv = Bukkit.createInventory(this, GUI_SIZE, guiTitle);

        // 언어 목록 배치 (중앙 정렬 로직은 생략하고 0번부터 순차 배치)
        int slot = 0;
        for (String langCode : sortedLangs) {
            if (slot >= BACK_SLOT) break; // 뒤로가기 버튼 자리는 비워둠

            boolean isSelected = langCode.equalsIgnoreCase(currentLang);
            String nameKey = isSelected ? "gui.language.selected" : "gui.language.unselected";

            // 언어 이름 가져오기 (파일에 없으면 코드 자체를 표시)
            String langName = LangManager.get(langCode, "language.name");
            if (langName.equals("language.name")) langName = langCode.toUpperCase();

            String displayName = langName + " [" + langCode + "]";

            ItemStack item = new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.LANGUAGE_GUI_ITEM))
                    .name(LangManager.get(uuid, nameKey).replace("%lang%", displayName))
                    .lore(LangManager.getList(uuid, "gui.language.lore"))
                    .build();

            // PDC를 이용한 언어 코드 저장
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(langKey, PersistentDataType.STRING, langCode);
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        // 뒤로가기 버튼 추가
        inv.setItem(BACK_SLOT, new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.BACK_BUTTON))
                .name("§c" + LangManager.get(uuid, "gui.back.name"))
                .build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof LanguageSelectGUI)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0) return;

        // 뒤로가기 버튼 처리
        if (slot == BACK_SLOT) {
            ConfigManager.playSound(player, ConfigManager.SoundType.GUI_CLICK);
            MailManager.getInstance().mailSettingGUI.open(player);
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String selectedLang = meta.getPersistentDataContainer().get(langKey, PersistentDataType.STRING);

        if (selectedLang != null && LangManager.getAvailableLanguages().contains(selectedLang)) {
            LangManager.setLanguage(player.getUniqueId(), selectedLang);
            ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SETTING_CHANGE);

            // 언어 변경 후 메시지 출력 및 이전 메뉴로 복귀
            player.sendMessage(LangManager.get(player.getUniqueId(), "cmd.setlang.success")
                    .replace("%lang%", selectedLang));

            MailManager.getInstance().mailSettingGUI.open(player);
        }
    }
}