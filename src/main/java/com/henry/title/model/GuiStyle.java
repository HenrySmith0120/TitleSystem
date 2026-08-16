package com.henry.title.model;

import org.bukkit.Material;

/**
 * GUI 样式配置（config.yml 的 gui.shop / gui.chest 段）：
 * 页码指示器（默认下界之星，名称支持 %page%/%pages% 占位符）与各按钮的自定义物品、装饰玻璃板。
 */
public record GuiStyle(int pageIndicatorSlot, Material pageIndicatorItem, String pageIndicatorName,
                       Material prevItem, Material nextItem, Material closeItem,
                       GuiDecoration decoration) {
}
