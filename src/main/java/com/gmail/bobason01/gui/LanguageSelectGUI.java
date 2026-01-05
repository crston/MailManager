package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.config.ConfigManager;
import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey; // 추가됨
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta; // 추가됨
import org.bukkit.persistence.PersistentDataType; // 추가됨
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LanguageSelectGUI implements Listener, InventoryHolder {

    private static final int GUI_SIZE = 27;
    private final Plugin plugin;
    // 언어 코드를 저장할 키 생성
    private final NamespacedKey langKey;

    public LanguageSelectGUI(Plugin plugin) {
        this.plugin = plugin;
        this.langKey = new NamespacedKey(plugin, "lang_code");
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, GUI_SIZE);
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String currentLang = LangManager.getLanguage(uuid);
        List<String> sortedLangs = new ArrayList<>(LangManager.getAvailableLanguages());
        sortedLangs.sort(String::compareToIgnoreCase);

        String guiTitle = LangManager.get(uuid, "gui.language.title");
        Inventory inv = Bukkit.createInventory(this, GUI_SIZE, guiTitle);

        int slot = 0;
        for (String lang : sortedLangs) {
            if (slot >= GUI_SIZE) break;
            boolean selected = lang.equalsIgnoreCase(currentLang);

            String nameKey = selected ? "gui.language.selected" : "gui.language.unselected";
            String displayName = LangManager.get(lang, "language.name") + " [" + lang + "]";

            // 아이템 생성
            ItemStack item = new ItemBuilder(ConfigManager.getItem(ConfigManager.ItemType.LANGUAGE_GUI_ITEM))
                    .name(LangManager.get(uuid, nameKey).replace("%lang%", displayName))
                    .lore(LangManager.getList(uuid, "gui.language.lore"))
                    .build();

            // 아이템 메타(PDC)에 언어 코드를 직접 저장 (이름 파싱 의존성 제거)
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(langKey, PersistentDataType.STRING, lang);
                item.setItemMeta(meta);
            }

            inv.setItem(slot++, item);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof LanguageSelectGUI) || !(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();

        // 이름 파싱 대신 NBT 데이터 확인
        if (!meta.getPersistentDataContainer().has(langKey, PersistentDataType.STRING)) return;

        String selectedLang = meta.getPersistentDataContainer().get(langKey, PersistentDataType.STRING);

        // 언어 파일이 실제 로드되어 있는지 안전 검사
        if (selectedLang == null || !LangManager.getAvailableLanguages().contains(selectedLang)) return;

        LangManager.setLanguage(player.getUniqueId(), selectedLang);
        ConfigManager.playSound(player, ConfigManager.SoundType.ACTION_SETTING_CHANGE);

        // GUI 다시 열기 (변경 사항 반영) 또는 이전 메뉴로 돌아가기
        MailManager.getInstance().mailSettingGUI.open(player);
    }
}