package com.henry.title.listener;

import com.henry.title.TitleSystem;
import com.henry.title.gui.GuiHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/** GUI 点击监听：拦截称号商店 / 仓库的所有点击并交给对应 GUI 处理。 */
public final class GuiListener implements Listener {

    private final TitleSystem plugin;

    public GuiListener(TitleSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // 只处理顶部 GUI 栏位（底部玩家背包点击同样取消，防止物品挪动）
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) return;
        holder.onClick(player, event.getSlot());
    }
}
