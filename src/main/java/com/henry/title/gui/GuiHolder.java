package com.henry.title.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** 称号系统 GUI 的统一 InventoryHolder 基类。 */
public abstract class GuiHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /** 点击处理（仅顶部 GUI 栏位）。 */
    public abstract void onClick(Player player, int slot);
}
