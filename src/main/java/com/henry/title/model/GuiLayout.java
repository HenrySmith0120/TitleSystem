package com.henry.title.model;

/**
 * GUI 布局配置（config.yml 的 gui.shop / gui.chest 段）。
 * itemsStartSlot / itemsSlots 决定称号物品区域；
 * prevSlot / closeSlot / nextSlot 为导航按钮槽位；playerHeadSlot 为左下角玩家信息头颅。
 */
public record GuiLayout(int itemsStartSlot, int itemsSlots, int prevSlot, int closeSlot, int nextSlot, int playerHeadSlot) {
}
