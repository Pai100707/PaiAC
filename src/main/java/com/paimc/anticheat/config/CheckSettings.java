package com.paimc.anticheat.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/** Validated, immutable per-check settings. Check-specific values are read from the raw section. */
public final class CheckSettings {
    public final boolean enabled;
    public final boolean debug;
    public final boolean resetOnClean;
    public final double violationPoints;
    public final double threshold;
    public final double bufferLimit;
    public final double bufferDecay;
    public final long cooldownMs;
    public final int minEvidence;
    private final ConfigurationSection section;

    private CheckSettings(boolean enabled, boolean debug, boolean resetOnClean, double vp, double threshold,
                          double bufferLimit, double bufferDecay, long cooldownMs, int minEvidence,
                          ConfigurationSection section) {
        this.enabled = enabled;
        this.debug = debug;
        this.resetOnClean = resetOnClean;
        this.violationPoints = vp;
        this.threshold = threshold;
        this.bufferLimit = bufferLimit;
        this.bufferDecay = bufferDecay;
        this.cooldownMs = cooldownMs;
        this.minEvidence = minEvidence;
        this.section = section;
    }

    /** Disabled placeholder used when a section is missing entirely. */
    public static CheckSettings disabled() {
        return new CheckSettings(false, false, true, 1, 1, 1, 0, 0, 1, null);
    }

    public static CheckSettings from(String id, ConfigurationSection s, List<String> warnings) {
        if (s == null) {
            warnings.add("Check '" + id + "': config section missing, check disabled.");
            return disabled();
        }
        boolean enabled = s.getBoolean("enabled", false);
        double vp = s.getDouble("violation-points", 1.0);
        double threshold = s.getDouble("threshold", 3.0);
        double limit = s.getDouble("buffer-limit", Math.max(threshold, 6.0));
        double decay = s.getDouble("buffer-decay", 0.25);
        long cooldown = s.getLong("cooldown-ms", 1000L);
        int minEv = s.getInt("min-evidence", 3);

        if (vp <= 0) { warnings.add(id + ": violation-points must be > 0, using 1.0"); vp = 1.0; }
        if (threshold <= 0) { warnings.add(id + ": threshold must be > 0, using 1.0"); threshold = 1.0; }
        if (limit < threshold) { warnings.add(id + ": buffer-limit < threshold, raised to threshold"); limit = threshold; }
        if (decay < 0) { warnings.add(id + ": buffer-decay must be >= 0, using 0"); decay = 0; }
        if (cooldown < 0) { warnings.add(id + ": cooldown-ms must be >= 0, using 0"); cooldown = 0; }
        if (minEv < 1) { warnings.add(id + ": min-evidence must be >= 1, using 1"); minEv = 1; }

        return new CheckSettings(enabled, s.getBoolean("debug", false), s.getBoolean("reset-on-clean", true),
                vp, threshold, limit, decay, cooldown, minEv, s);
    }

    public double dbl(String key, double def) { return section == null ? def : section.getDouble(key, def); }

    public int integer(String key, int def) { return section == null ? def : section.getInt(key, def); }

    public long lng(String key, long def) { return section == null ? def : section.getLong(key, def); }

    public boolean bool(String key, boolean def) { return section == null ? def : section.getBoolean(key, def); }

    public String str(String key, String def) { return section == null ? def : section.getString(key, def); }

    public ConfigurationSection raw() { return section; }
}
