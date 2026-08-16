package com.henry.title.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

/**
 * 配置文件中定义的称号模板（不可变）。
 * 数据模型：称号 ID、显示名、描述、材质、价格、有效期类型、Buff、粒子等。
 */
public final class ConfiguredTitle {

    private final String id;
    private final String display;        // 展示文本（& 颜色代码）
    private final String name;           // GUI 物品名称
    private final List<String> lore;     // GUI 描述
    private final Material material;     // GUI 图标材质（Material 枚举）
    private final int modelData;         // CustomModelData，0 = 不使用
    private final double price;          // 商店价格，0 = 免费
    private final String permission;     // 购买所需权限，可空
    private final int durationDays;      // 商店购买有效期（天），-1 = 永久
    private final TitleBuff buffs;       // 佩戴 Buff（可为空集合）
    private final ParticleConfig particle; // 粒子特效，可空

    public ConfiguredTitle(String id, String display, String name, List<String> lore, Material material,
                           int modelData, double price, String permission, int durationDays,
                           TitleBuff buffs, ParticleConfig particle) {
        this.id = id;
        this.display = display;
        this.name = name;
        this.lore = lore;
        this.material = material;
        this.modelData = modelData;
        this.price = price;
        this.permission = permission;
        this.durationDays = durationDays;
        this.buffs = buffs;
        this.particle = particle;
    }

    public String getId() { return id; }
    public String getDisplay() { return display; }
    public String getName() { return name; }
    public List<String> getLore() { return Collections.unmodifiableList(lore); }
    public Material getMaterial() { return material; }
    public int getModelData() { return modelData; }
    public double getPrice() { return price; }
    public String getPermission() { return permission; }
    public int getDurationDays() { return durationDays; }
    public TitleBuff getBuffs() { return buffs; }
    public ParticleConfig getParticle() { return particle; }
}
