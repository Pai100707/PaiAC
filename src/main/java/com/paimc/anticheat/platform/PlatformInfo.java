package com.paimc.anticheat.platform;

import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects the server software and Minecraft version without touching version-specific API. */
public final class PlatformInfo {
    public final boolean folia;
    public final boolean paper;
    public final String serverName;
    public final String minecraftVersion;
    public final int minor;
    public final int patch;

    private PlatformInfo(boolean folia, boolean paper, String serverName, String mc, int minor, int patch) {
        this.folia = folia;
        this.paper = paper;
        this.serverName = serverName;
        this.minecraftVersion = mc;
        this.minor = minor;
        this.patch = patch;
    }

    public static PlatformInfo detect() {
        boolean folia = hasClass("io.papermc.paper.threadedregions.RegionizedServer");
        boolean paper = folia || hasClass("io.papermc.paper.event.player.AsyncChatEvent");
        String bv = Bukkit.getBukkitVersion();
        Matcher m = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(bv == null ? "" : bv);
        int minor = 0, patch = 0;
        String mc = "unknown";
        if (m.find()) {
            minor = Integer.parseInt(m.group(2));
            patch = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            mc = m.group(1) + "." + minor + (patch > 0 ? "." + patch : "");
        }
        return new PlatformInfo(folia, paper, Bukkit.getName(), mc, minor, patch);
    }

    private static boolean hasClass(String n) {
        try {
            Class.forName(n);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Officially targeted range: 1.21.1 .. 1.21.11. Outside it the plugin still loads but warns. */
    public boolean inSupportedRange() {
        return minor == 21 && patch >= 1 && patch <= 11;
    }

    public String softwareLabel() {
        return folia ? "Folia" : paper ? "Paper-family" : "Spigot/Bukkit";
    }

    public String describe() {
        return serverName + " (" + softwareLabel() + ") MC " + minecraftVersion;
    }
}
