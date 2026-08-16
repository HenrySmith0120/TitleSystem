package com.henry.title.model;

import org.bukkit.Material;

import java.util.List;

/** GUI 装饰玻璃板配置：指定槽位 + 材质 + 名称 + lore（name/lore 默认为一个空格）。 */
public record GuiDecoration(List<Integer> slots, Material material, String name, List<String> lore) {
}
