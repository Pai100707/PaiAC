package com.paimc.anticheat.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Version-tolerant lookups. Anything whose constant name changed between 1.21.1 and 1.21.11
 * (attributes, enchantments) is resolved by namespaced key instead of a static field.
 */
public final class CompatUtil {
    private CompatUtil() {}

    /** Value of an attribute by any of the given keys (old/new naming), or {@code fallback}. */
    public static double attributeValue(LivingEntity e, double fallback, String... keys) {
        try {
            for (String k : keys) {
                Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(k));
                if (a == null) continue;
                AttributeInstance inst = e.getAttribute(a);
                if (inst != null) return inst.getValue();
            }
        } catch (Throwable ignored) {
            // registry shape differs on this version: use fallback
        }
        return fallback;
    }

    @SuppressWarnings("deprecation")
    public static int enchantLevel(ItemStack item, String key) {
        if (item == null || item.getType().isAir()) return 0;
        try {
            Enchantment en = Enchantment.getByKey(NamespacedKey.minecraft(key));
            return en == null ? 0 : item.getEnchantmentLevel(en);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean typeNameContains(Entity e, String part) {
        return e != null && e.getType().name().contains(part);
    }

    public static boolean isCrystal(Entity e) {
        return typeNameContains(e, "CRYSTAL");
    }

    public static int ping(Player p) {
        try {
            return Math.max(0, p.getPing());
        } catch (Throwable t) {
            return 0;
        }
    }
}
