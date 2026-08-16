package com.henry.title.gui;

import com.henry.title.TitleSystem;
import com.henry.title.model.ConfiguredTitle;
import com.henry.title.model.GuiLayout;
import com.henry.title.model.GuiStyle;
import com.henry.title.util.GuiUtil;
import com.henry.title.util.TextUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 称号商店 GUI：分页展示所有可购买称号，支持 Vault 金币购买。
 * 槽位布局由 config.yml 的 gui.shop 段配置（物品槽会自动避开按钮槽）。
 */
public final class ShopGui {

    private ShopGui() { }

    public static void open(TitleSystem plugin, Player player, int page) {
        List<ConfiguredTitle> all = new ArrayList<>(plugin.getConfigManager().getTitles().values());
        GuiLayout layout = plugin.getConfigManager().getShopLayout();
        GuiStyle style = plugin.getConfigManager().getShopStyle();
        Set<Integer> reserved = new HashSet<>(List.of(
                layout.prevSlot(), layout.closeSlot(), layout.nextSlot(),
                layout.playerHeadSlot(), style.pageIndicatorSlot()));
        reserved.addAll(style.decoration().slots());
        List<Integer> itemSlots = GuiUtil.buildItemSlots(layout, reserved);
        int pageSize = itemSlots.size();
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;
        final int currentPage = page;

        GuiHolder holder = new GuiHolder() {
            @Override
            public void onClick(Player clicker, int slot) {
                int position = itemSlots.indexOf(slot);
                if (position >= 0) {
                    int index = currentPage * pageSize + position;
                    if (index < all.size()) {
                        purchase(plugin, clicker, all.get(index), currentPage);
                    }
                } else if (slot == layout.prevSlot()) {
                    if (currentPage <= 0) {
                        plugin.getMessageManager().send(clicker, "gui.first-page");
                    } else {
                        open(plugin, clicker, currentPage - 1);
                    }
                } else if (slot == layout.nextSlot()) {
                    if (currentPage >= pages - 1) {
                        plugin.getMessageManager().send(clicker, "gui.last-page");
                    } else {
                        open(plugin, clicker, currentPage + 1);
                    }
                } else if (slot == layout.closeSlot()) {
                    clicker.closeInventory();
                }
            }
        };

        Inventory inv = Bukkit.createInventory(holder, 54,
                TextUtil.parse(plugin.getConfigManager().getShopTitle(), null, Map.of()));
        // 左下角玩家信息头颅（ID / 余额 / 拥有称号数）
        inv.setItem(layout.playerHeadSlot(), GuiUtil.buildInfoHead(plugin, player));
        for (int i = 0; i < pageSize; i++) {
            int index = currentPage * pageSize + i;
            if (index >= all.size()) break;
            inv.setItem(itemSlots.get(i), buildItem(plugin, player, all.get(index)));
        }
        GuiUtil.fillNavigation(inv, layout, style, currentPage, pages, plugin);
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    private static ItemStack buildItem(TitleSystem plugin, Player player, ConfiguredTitle title) {
        ItemStack item = new ItemStack(title.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parse(title.getName(), null, Map.of()));
        List<Component> lore = new ArrayList<>();
        for (String line : title.getLore()) {
            lore.add(TextUtil.parse(line, null, Map.of()));
        }
        boolean owned = plugin.getTitleManager().owns(player.getUniqueId(), title.getId());
        if (owned) {
            lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("shop.owned-lore"), null, Map.of()));
        } else {
            String price = plugin.getEconomyHook().isReady()
                    ? plugin.getEconomyHook().format(title.getPrice())
                    : String.valueOf(title.getPrice());
            lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("shop.price-lore"), null,
                    Map.of("price", price)));
            lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("shop.duration-lore"), null,
                    Map.of("duration", plugin.getMessageManager().duration(title.getDurationDays()))));
            lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("shop.click-buy"), null, Map.of()));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        // CustomModelData 使用 1.21.4+ 数据组件 API（不使用已弃用的 ItemMeta#setCustomModelData(Integer)）
        if (title.getModelData() > 0) {
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelData.customModelData().addFloat((float) title.getModelData()).build());
        }
        return item;
    }

    private static void purchase(TitleSystem plugin, Player player, ConfiguredTitle title, int page) {
        // 已拥有
        if (plugin.getTitleManager().owns(player.getUniqueId(), title.getId())) {
            plugin.getMessageManager().send(player, "shop.already-owned", Map.of("title", title.getDisplay()));
            GuiUtil.sound(player, false);
            return;
        }
        // 购买权限（title.buy.<id> 等）
        if (title.getPermission() != null && !player.hasPermission(title.getPermission())) {
            plugin.getMessageManager().send(player, "no-permission", Map.of());
            GuiUtil.sound(player, false);
            return;
        }
        double price = title.getPrice();
        if (price > 0) {
            if (!plugin.getEconomyHook().isReady()) {
                plugin.getMessageManager().send(player, "shop.no-economy", Map.of());
                GuiUtil.sound(player, false);
                return;
            }
            if (plugin.getEconomyHook().getBalance(player) < price
                    || !plugin.getEconomyHook().withdraw(player, price)) {
                plugin.getMessageManager().send(player, "shop.no-money",
                        Map.of("price", plugin.getEconomyHook().format(price)));
                GuiUtil.sound(player, false);
                return;
            }
        }
        // 有效期：-1 = 永久；>0 为天数
        long expire = title.getDurationDays() > 0
                ? System.currentTimeMillis() + title.getDurationDays() * 86400000L
                : -1L;
        plugin.getTitleManager().grant(player, title.getId(), expire, extended -> {
            String key = price > 0 ? "shop.bought" : "shop.bought-free";
            plugin.getMessageManager().send(player, key, Map.of("title", title.getDisplay()));
            GuiUtil.sound(player, true);
            open(plugin, player, page);
        });
    }
}
