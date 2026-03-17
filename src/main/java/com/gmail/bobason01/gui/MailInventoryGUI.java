package com.gmail.bobason01.gui;

import com.gmail.bobason01.MailManager;
import com.gmail.bobason01.commands.MailCommand;
import com.gmail.bobason01.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MailInventoryGUI implements Listener, InventoryHolder {

    private static final int SIZE = 36;

    private final int invId;
    private final boolean editable;
    private Inventory inv;

    public MailInventoryGUI(int invId, boolean editable) {
        this.invId = invId;
        this.editable = editable;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    /**
     * GUI를 오픈합니다. 인벤토리 데이터는 MailCommand의 데이터를 참조합니다.
     */
    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);
        String titleKey = editable ? "gui.mailinv.edit.title" : "gui.mailinv.view.title";
        String title = LangManager.get(lang, titleKey).replace("%id%", String.valueOf(invId));

        this.inv = Bukkit.createInventory(this, SIZE, title);

        // MailCommand에 저장된 정적 인벤토리 데이터를 불러옵니다.
        ItemStack[] contents = MailCommand.getInventory(invId);
        if (contents != null) {
            inv.setContents(contents);
        }

        player.openInventory(inv);
    }

    /**
     * 현재 인벤토리 내용을 MailCommand의 정적 저장소에 저장합니다.
     */
    private void save() {
        if (editable && inv != null) {
            MailCommand.saveInventory(invId, inv.getContents());
        }
    }

    // ---------------- 리스너 로직 (MailManager에서 한 번만 등록해야 함) ----------------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailInventoryGUI gui)) return;

        // 편집 모드가 아니면 클릭 원천 차단
        if (!gui.editable) {
            e.setCancelled(true);
            return;
        }

        // 편집 모드여도 하단 인벤토리에서 상단으로 아이템을 옮기는 행위는 추후 onClose에서 저장됨
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailInventoryGUI gui)) return;

        if (!gui.editable) {
            // 상단 인벤토리가 포함된 드래그라면 차단
            for (int slot : e.getRawSlots()) {
                if (slot < e.getInventory().getSize()) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MailInventoryGUI gui)) return;
        if (!(e.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        String lang = LangManager.getLanguage(uuid);

        if (gui.editable) {
            // 인벤토리를 닫는 시점에 최종 저장 (성능 최적화)
            gui.save();
            player.sendMessage(LangManager.get(lang, "gui.mailinv.saved")
                    .replace("%id%", String.valueOf(gui.invId)));
        } else {
            player.sendMessage(LangManager.get(lang, "gui.mailinv.closed")
                    .replace("%id%", String.valueOf(gui.invId)));
        }
    }
}