package com.paimc.anticheat.util;

import org.bukkit.ChatColor;

import java.util.Locale;

public final class Colors {
    private Colors() {}

    @SuppressWarnings("deprecation")
    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
