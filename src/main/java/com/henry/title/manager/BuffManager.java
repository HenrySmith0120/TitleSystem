package com.henry.title.manager;

import com.henry.title.TitleSystem;
import com.henry.title.model.AttributeSpec;
import com.henry.title.model.ConfiguredTitle;
import com.henry.title.model.TitleBuff;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

/**
 * Buff 管理器：佩戴称号时应用原版药水效果（PotionEffectType）+ 属性加成（AttributeModifier），
 * 卸下 / 换装 / 称号被移除时清理。
 *
 * 已知局限（README 有说明）：removePotionEffect 按效果类型移除，
 * 若其他插件给玩家添加了同类型效果也会被一并移除（与原版药水机制一致）。
 */
public final class BuffManager {

    private final TitleSystem plugin;

    public BuffManager(TitleSystem plugin) {
        this.plugin = plugin;
    }

    /** 应用称号 Buff：先清空玩家身上全部称号 Buff，再应用新称号（换装安全）。 */
    public void apply(Player player, ConfiguredTitle title) {
        removeAllTitleBuffs(player);
        if (title == null) return;
        TitleBuff buffs = title.getBuffs();

        for (PotionEffect effect : buffs.getPotions()) {
            player.addPotionEffect(effect);
        }
        for (AttributeSpec spec : buffs.getAttributes()) {
            AttributeInstance instance = player.getAttribute(spec.attribute());
            if (instance == null) {
                plugin.getLogger().warning("属性 " + spec.attribute() + " 不适用于玩家 " + player.getName());
                continue;
            }
            instance.addModifier(spec.buildModifier(keyFor(title, spec)));
        }
        // 生命上限属性变化后收敛当前血量，避免出现超出上限的血量
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    /** 移除该玩家身上所有称号 Buff（遍历全部称号定义，保证 reload 后也能清理干净）。 */
    public void removeAllTitleBuffs(Player player) {
        for (ConfiguredTitle title : plugin.getConfigManager().getTitles().values()) {
            TitleBuff buffs = title.getBuffs();
            for (PotionEffect effect : buffs.getPotions()) {
                player.removePotionEffect(effect.getType());
            }
            for (AttributeSpec spec : buffs.getAttributes()) {
                AttributeInstance instance = player.getAttribute(spec.attribute());
                if (instance != null) {
                    instance.removeModifier(keyFor(title, spec));
                }
            }
        }
    }

    /** 属性修饰符键：每个称号-属性组合唯一，便于精确移除。 */
    private NamespacedKey keyFor(ConfiguredTitle title, AttributeSpec spec) {
        // NamespacedKey 键名须符合 [a-z0-9/._-]，做一次安全清洗并限长
        String key = ("title_" + title.getId() + "_" + spec.attribute().getKey().getKey())
                .toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        if (key.length() > 100) key = key.substring(0, 100);
        return new NamespacedKey(plugin, key);
    }
}
