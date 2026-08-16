package com.henry.title.model;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;

/**
 * 属性加成配置。
 * 全部使用现代 API：
 *  - AttributeModifier(NamespacedKey, double, Operation, EquipmentSlotGroup) 构造器；
 *  - AttributeModifier.Operation 枚举（不使用已弃用的 int 常量）；
 *  - Attribute 使用 1.21.2+ 现代名称（MAX_HEALTH / ATTACK_DAMAGE ...）。
 */
public record AttributeSpec(Attribute attribute, double amount,
                            AttributeModifier.Operation operation, EquipmentSlotGroup slot) {

    public AttributeModifier buildModifier(NamespacedKey key) {
        return new AttributeModifier(key, amount, operation, slot);
    }
}
