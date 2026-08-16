package com.henry.title.hook;

import com.henry.title.TitleSystem;
import com.henry.title.model.ConfiguredTitle;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI 扩展：%titlesystem_<参数>%
 * 参数：
 *   title       - 当前穿戴称号的展示文本
 *   title_id    - 当前穿戴称号的 ID
 *   owned_count - 拥有的称号数量
 * 不使用反射：仅在 PlaceholderAPI 已安装时由主类注册。
 */
public final class PlaceholderHook extends PlaceholderExpansion {

    private final TitleSystem plugin;

    public PlaceholderHook(TitleSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() { return "titlesystem"; }

    @Override
    public String getAuthor() { return "Henry"; }

    @Override
    public String getVersion() { return plugin.getPluginMeta().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";
        switch (params.toLowerCase()) {
            case "title": {
                ConfiguredTitle title = plugin.getTitleManager().getActiveTitle(player.getUniqueId());
                return title == null ? "" : title.getDisplay();
            }
            case "title_id": {
                String id = plugin.getTitleManager().getActiveTitleId(player.getUniqueId());
                return id == null ? "" : id;
            }
            case "owned_count":
                return String.valueOf(plugin.getTitleManager().getOwnedTitles(player.getUniqueId()).size());
            default:
                return null;
        }
    }
}
