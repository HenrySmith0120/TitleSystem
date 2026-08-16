package com.henry.title.gui;

import com.henry.title.TitleSystem;
import com.henry.title.data.TitleEntry;
import com.henry.title.model.ConfiguredTitle;
import com.henry.title.model.GuiLayout;
import com.henry.title.model.GuiStyle;
import com.henry.title.util.GuiUtil;
import com.henry.title.util.TextUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 称号仓库 GUI：展示玩家已拥有的称号，点击穿戴 / 卸下，显示获得时间与剩余时间。
 * 只显示实际数据（获得时间/真实剩余时间/穿戴状态），不显示称号的商店营销 lore。
 * 槽位布局由 config.yml 的 gui.chest 段配置（物品槽会自动避开按钮槽）。
 */
public final class ChestGui {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private ChestGui() { }

    public static void open(TitleSystem plugin, Player player, int page) {
        List<TitleEntry> owned = new ArrayList<>(plugin.getTitleManager().getOwnedTitles(player.getUniqueId()));
        owned.sort((a, b) -> Long.compare(b.getAcquireTime(), a.getAcquireTime()));
        GuiLayout layout = plugin.getConfigManager().getChestLayout();
        GuiStyle style = plugin.getConfigManager().getChestStyle();
        Set<Integer> reserved = new HashSet<>(List.of(
                layout.prevSlot(), layout.closeSlot(), layout.nextSlot(),
                layout.playerHeadSlot(), style.pageIndicatorSlot()));
        reserved.addAll(style.decoration().slots());
        List<Integer> itemSlots = GuiUtil.buildItemSlots(layout, reserved);
        int pageSize = itemSlots.size();
        int pages = Math.max(1, (int) Math.ceil(owned.size() / (double) pageSize));
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;
        final int currentPage = page;

        GuiHolder holder = new GuiHolder() {
            @Override
            public void onClick(Player clicker, int slot) {
                int position = itemSlots.indexOf(slot);
                if (position >= 0) {
                    int index = currentPage * pageSize + position;
                    if (index < owned.size()) {
                        TitleEntry entry = owned.get(index);
                        String titleId = entry.getTitleId();
                        boolean active = titleId.equals(plugin.getTitleManager().getActiveTitleId(clicker.getUniqueId()));
                        ConfiguredTitle title = plugin.getConfigManager().getTitle(titleId);
                        String display = title == null ? titleId : title.getDisplay();
                        if (active) {
                            plugin.getTitleManager().unequip(clicker);
                            plugin.getMessageManager().send(clicker, "chest.unequipped", Map.of("title", display));
                        } else {
                            plugin.getTitleManager().equip(clicker, titleId);
                            plugin.getMessageManager().send(clicker, "chest.equipped", Map.of("title", display));
                        }
                        GuiUtil.sound(clicker, true);
                        open(plugin, clicker, currentPage);
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
                TextUtil.parse(plugin.getConfigManager().getChestTitle(), null, Map.of()));
        // 左下角玩家信息头颅（ID / 余额 / 拥有称号数）
        inv.setItem(layout.playerHeadSlot(), GuiUtil.buildInfoHead(plugin, player));
        if (owned.isEmpty()) {
            inv.setItem(itemSlots.get(itemSlots.size() / 2), GuiUtil.button(Material.BARRIER,
                    TextUtil.parse(plugin.getMessageManager().getRaw("chest.empty"), null, Map.of())));
        }
        for (int i = 0; i < pageSize; i++) {
            int index = currentPage * pageSize + i;
            if (index >= owned.size()) break;
            inv.setItem(itemSlots.get(i), buildItem(plugin, player, owned.get(index)));
        }
        GuiUtil.fillNavigation(inv, layout, style, currentPage, pages, plugin);
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    private static ItemStack buildItem(TitleSystem plugin, Player player, TitleEntry entry) {
        ConfiguredTitle title = plugin.getConfigManager().getTitle(entry.getTitleId());
        Material material = title == null ? Material.PAPER : title.getMaterial();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String display = title == null ? entry.getTitleId() : title.getDisplay();
        meta.displayName(TextUtil.parse(display, null, Map.of()));
        // 仓库只显示实际数据（获得时间/真实剩余时间/穿戴状态），
        // 不显示称号配置里的商店营销 lore（价格/有效期文案属于商店）
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("chest.acquire-lore"), null,
                Map.of("time", DATE_FORMAT.format(new Date(entry.getAcquireTime())))));
        if (entry.isPermanent()) {
            lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("chest.permanent"), null, Map.of()));
        } else {
            lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("chest.expire-lore"), null,
                    Map.of("time", plugin.getMessageManager().timeLeft(entry.getExpireTime()))));
        }
        boolean active = entry.getTitleId().equals(plugin.getTitleManager().getActiveTitleId(player.getUniqueId()));
        if (active) {
            lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("chest.active-lore"), null, Map.of()));
        } else {
            lore.add(TextUtil.parse(plugin.getMessageManager().getRaw("chest.click-toggle"), null, Map.of()));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        // CustomModelData 使用 1.21.4+ 数据组件 API（不使用已弃用的 ItemMeta#setCustomModelData(Integer)）
        if (title != null && title.getModelData() > 0) {
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelData.customModelData().addFloat((float) title.getModelData()).build());
        }
        return item;
    }
}
