package com.gmail.bobason01.gui;

import com.gmail.bobason01.lang.LangManager;
import com.gmail.bobason01.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class LanguageSelectGUI implements Listener {

    private static final int GUI_SIZE = 27;
    private final Plugin plugin;

    public LanguageSelectGUI(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String currentLang = LangManager.getLanguage(uuid);
        Set<String> availableLangs = LangManager.getAvailableLanguages();
        List<String> sortedLangs = new ArrayList<>(availableLangs);
        sortedLangs.sort(String::compareToIgnoreCase);

        String guiTitle = LangManager.get(uuid, "gui.language.title");
        Inventory inv = Bukkit.createInventory(player, GUI_SIZE, guiTitle);

        int slot = 0;
        for (String lang : sortedLangs) {
            boolean selected = lang.equalsIgnoreCase(currentLang);
            String nameKey = selected ? "gui.language.selected" : "gui.language.unselected";
            String loreKey = "gui.language.lore";

            // 표시용 언어 이름 + 코드 (예: 한국어 [ko])
            String displayName = LangManager.get(lang, "language.name") + " [" + lang + "]";

            inv.setItem(slot++, new ItemBuilder(Material.PAPER)
                    .name(LangManager.get(uuid, nameKey).replace("%lang%", displayName))
                    .lore(LangManager.get(uuid, loreKey).replace("%lang%", displayName))
                    .build());

            if (slot >= GUI_SIZE) break;
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        String expectedTitle = LangManager.get(uuid, "gui.language.title");
        if (!e.getView().getTitle().equals(expectedTitle)) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = Objects.requireNonNull(clicked.getItemMeta()).getDisplayName();
        if (displayName == null || displayName.isEmpty()) return;

        // 색 코드 제거
        String strippedName = ChatColor.stripColor(displayName).trim();

        // 선택한 언어 코드 찾기 (예: "한국어 [ko]"에서 ko 추출)
        String selectedLang = LangManager.getAvailableLanguages().stream()
                .filter(langCode -> {
                    String expectedName = LangManager.get(langCode, "language.name") + " [" + langCode + "]";
                    return strippedName.equalsIgnoreCase(expectedName);
                })
                .findFirst()
                .orElse(null);

        if (selectedLang == null) return;

        // 언어 설정
        LangManager.setLanguage(uuid, selectedLang);

        // 확인 메시지
        LangManager.get(selectedLang, "language.name");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

        // 설정 GUI 다시 열기
        new MailSettingGUI(plugin).open(player);
    }
}
