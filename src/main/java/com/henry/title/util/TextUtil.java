package com.henry.title.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 文本工具：统一使用 Adventure 组件（不使用旧式 ChatColor / String 消息）。
 * 解析流程：
 *   1. "&" 颜色代码 → "§"；
 *   2. 内部占位符（%player_name%、%title%、%online% ...）；
 *   3. 可选：PlaceholderAPI 解析（仅当 PAPI 已安装且 player 非空，无反射）；
 *   4. LegacyComponentSerializer 生成 Adventure 组件。
 */
public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private TextUtil() { }

    /** 解析带 & 颜色代码与占位符的文本为 Adventure 组件。player 为 null 时跳过 PlaceholderAPI。 */
    public static Component parse(String text, Player player, Map<String, String> placeholders) {
        if (text == null || text.isEmpty()) return Component.empty();
        String result = text.replace('&', '§');
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                String key = e.getKey();
                String value = e.getValue() == null ? "" : e.getValue();
                result = result.replace("%" + key + "%", value);
            }
        }
        // PlaceholderAPI（显式插件检测，非反射；任何异常都不影响主流程）
        if (player != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result);
            } catch (Exception ignored) {
                // 保持原文本
            }
        }
        return LEGACY.deserialize(result);
    }
}
