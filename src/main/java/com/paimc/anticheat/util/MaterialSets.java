package com.paimc.anticheat.util;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/** Material groups resolved by name so a missing/renamed material never breaks class loading. */
public final class MaterialSets {
    public static final Set<Material> LIQUIDS = of("WATER", "LAVA", "BUBBLE_COLUMN", "KELP", "KELP_PLANT", "SEAGRASS", "TALL_SEAGRASS");
    public static final Set<Material> CLIMBABLE = of("LADDER", "VINE", "SCAFFOLDING", "TWISTING_VINES", "TWISTING_VINES_PLANT",
            "WEEPING_VINES", "WEEPING_VINES_PLANT", "CAVE_VINES", "CAVE_VINES_PLANT");
    public static final Set<Material> SLOWING = of("COBWEB", "SWEET_BERRY_BUSH", "POWDER_SNOW", "HONEY_BLOCK");
    public static final Set<Material> ICE = of("ICE", "PACKED_ICE", "BLUE_ICE", "FROSTED_ICE");
    public static final Set<Material> SOUL = of("SOUL_SAND", "SOUL_SOIL");
    public static final Set<Material> BOUNCY = bouncy();

    private MaterialSets() {}

    public static Set<Material> of(String... names) {
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m != null) set.add(m);
        }
        return set;
    }

    private static Set<Material> bouncy() {
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (Material m : Material.values()) {
            String n = m.name();
            if (n.startsWith("LEGACY_")) continue;
            if (n.equals("SLIME_BLOCK") || n.endsWith("_BED")) set.add(m);
        }
        return set;
    }

    /** Tall-collision blocks (collision box 1.5 high): players stand 1.5 above the block base. */
    public static boolean isTallCollision(Material m) {
        String n = m.name();
        return n.endsWith("FENCE") || n.endsWith("_WALL") || n.endsWith("FENCE_GATE");
    }
}
