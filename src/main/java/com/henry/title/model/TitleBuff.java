package com.henry.title.model;

import org.bukkit.potion.PotionEffect;

import java.util.Collections;
import java.util.List;

/** 称号 Buff：原版药水效果（PotionEffect）+ 属性加成（AttributeSpec）。 */
public final class TitleBuff {

    private final List<PotionEffect> potions;
    private final List<AttributeSpec> attributes;

    public TitleBuff(List<PotionEffect> potions, List<AttributeSpec> attributes) {
        this.potions = potions;
        this.attributes = attributes;
    }

    public List<PotionEffect> getPotions() { return Collections.unmodifiableList(potions); }
    public List<AttributeSpec> getAttributes() { return Collections.unmodifiableList(attributes); }
}
