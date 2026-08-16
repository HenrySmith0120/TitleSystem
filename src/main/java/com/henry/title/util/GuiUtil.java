package com.henry.title.util;

import com.henry.title.TitleSystem;
import com.henry.title.model.GuiDecoration;
import com.henry.title.model.GuiLayout;
import com.henry.title.model.GuiStyle;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GUI 通用工具：按钮构建、翻页导航、物品槽位计算、提示音。 */
public final class GuiUtil {

    private GuiUtil() { }

    public static ItemStack button(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    /** 带 lore 的按钮。 */
    public static ItemStack button(Material material, Component name, List<Component> lore) {
        ItemStack item = button(material, name);
        ItemMeta meta = item.getItemMeta();
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 底栏渲染（位置与物品来自 config.yml 的 gui.* 段）：
     *  1. 装饰玻璃板（指定槽位，name/lore 默认为一个空格）；
     *  2. 上一页/下一页/关闭按钮（物品可自定义，始终显示；点击边界页时由 GUI 层发送提示消息）；
     *  3. 页码指示器（默认下界之星，名称支持 %page%/%pages% 占位符）。
     */
    public static void fillNavigation(Inventory inv, GuiLayout layout, GuiStyle style,
                                      int page, int pages, TitleSystem plugin) {
        // 装饰玻璃板
        for (int slot : style.decoration().slots()) {
            inv.setItem(slot, decorationItem(style.decoration()));
        }
        // 页码指示器（显示当前页/总页数，1 起编号）
        inv.setItem(style.pageIndicatorSlot(), pageIndicator(style, page, pages));
        // 翻页与关闭按钮（物品可自定义；放在最后，优先级最高）
        inv.setItem(layout.prevSlot(), button(style.prevItem(),
                TextUtil.parse(plugin.getMessageManager().getRaw("gui.prev-page"), null, Map.of())));
        inv.setItem(layout.closeSlot(), button(style.closeItem(),
                TextUtil.parse(plugin.getMessageManager().getRaw("gui.close"), null, Map.of())));
        inv.setItem(layout.nextSlot(), button(style.nextItem(),
                TextUtil.parse(plugin.getMessageManager().getRaw("gui.next-page"), null, Map.of())));
    }

    /** 页码指示器：物品与名称来自 GuiStyle，占位符 %page%（当前页）/%pages%（总页数）。 */
    private static ItemStack pageIndicator(GuiStyle style, int page, int pages) {
        ItemStack item = new ItemStack(style.pageIndicatorItem());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parse(style.pageIndicatorName(), null,
                Map.of("page", String.valueOf(page + 1), "pages", String.valueOf(pages))));
        item.setItemMeta(meta);
        return item;
    }

    /** 装饰玻璃板：按配置槽位放置，name/lore 默认为一个空格。 */
    private static ItemStack decorationItem(GuiDecoration decoration) {
        ItemStack item = new ItemStack(decoration.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parse(decoration.name(), null, Map.of()));
        List<Component> lore = new ArrayList<>();
        for (String line : decoration.lore()) {
            lore.add(TextUtil.parse(line, null, Map.of()));
        }
        meta.setHideTooltip(true);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** 玩家头颅（PLAYER_HEAD，现代 API：SkullMeta#setOwningPlayer，非弃用）。 */
    public static ItemStack playerHead(Player player, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            meta.displayName(name);
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 左下角玩家信息头颅：名字=玩家 ID，lore=余额 + 拥有称号数量。 */
    public static ItemStack buildInfoHead(TitleSystem plugin, Player player) {
        List<Component> lore = new ArrayList<>();
        if (plugin.getEconomyHook().isReady()) {
            lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("gui.head-balance"), null,
                    Map.of("balance", plugin.getEconomyHook().format(plugin.getEconomyHook().getBalance(player)))));
        } else {
            lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("gui.head-balance-none"), null, Map.of()));
        }
        lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("gui.head-owned"), null,
                Map.of("count", String.valueOf(
                        plugin.getTitleManager().getOwnedTitles(player.getUniqueId()).size()))));
        Component name = TextUtil.parse(plugin.getMessageManager().getRaw("gui.head-name"), null,
                Map.of("player", player.getName()));
        return playerHead(player, name, lore);
    }

    /** 计算称号物品可用槽位（自动避开翻页/关闭等保留槽位）。 */
    public static List<Integer> buildItemSlots(GuiLayout layout, Set<Integer> reserved) {
        List<Integer> slots = new ArrayList<>();
        for (int s = layout.itemsStartSlot(); s < 54 && slots.size() < layout.itemsSlots(); s++) {
            if (!reserved.contains(s)) slots.add(s);
        }
        return slots;
    }

    /** 成功 / 失败提示音（1.21.3+ Sound 为类静态字段形式）。 */
    public static void sound(Player player, boolean success) {
        player.playSound(player.getLocation(),
                success ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
    }
}
