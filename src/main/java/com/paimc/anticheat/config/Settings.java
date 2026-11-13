package com.paimc.anticheat.config;

import org.bukkit.configuration.file.YamlConfiguration;

/** Immutable snapshot of config.yml, rebuilt on every reload. */
public final class Settings {
    public final boolean debug;
    public final boolean alertsDefaultOn;
    public final long alertCooldownMs;
    public final int stateRetentionSeconds;
    public final boolean reportUnsupported;
    public final boolean foliaEnabled;
    public final double lagSkipTps;
    public final int lagSkipPingMs;
    public final double maxVl;
    public final double decayPerMinute;
    public final boolean asyncStorage;
    public final int maxPendingEvidence;
    public final String storageFile;
    public final int evidenceRetentionDays;
    public final int autosaveSeconds;

    private Settings(YamlConfiguration c) {
        debug = c.getBoolean("general.debug", false);
        alertsDefaultOn = c.getBoolean("general.alerts-default-on", true);
        alertCooldownMs = Math.max(0, c.getLong("general.alert-cooldown-ms", 1000));
        stateRetentionSeconds = Math.max(10, c.getInt("general.player-state-retention-seconds", 120));
        reportUnsupported = c.getBoolean("compatibility.report-unsupported-features", true);
        foliaEnabled = c.getBoolean("compatibility.folia.enabled", true);
        lagSkipTps = c.getDouble("lag-handling.skip-below-tps", 16.0);
        lagSkipPingMs = Math.max(50, c.getInt("lag-handling.skip-above-ping-ms", 1000));
        maxVl = Math.max(1, c.getDouble("violations.max-vl", 100.0));
        decayPerMinute = Math.max(0, c.getDouble("violations.decay-per-minute", 0.5));
        asyncStorage = c.getBoolean("performance.async-storage", true);
        maxPendingEvidence = Math.max(16, c.getInt("performance.max-pending-evidence", 2000));
        storageFile = c.getString("storage.file", "data/paiac.db");
        evidenceRetentionDays = Math.max(0, c.getInt("storage.evidence-retention-days", 30));
        autosaveSeconds = Math.max(30, c.getInt("storage.autosave-interval-seconds", 300));
    }

    public static Settings from(YamlConfiguration c) {
        return new Settings(c);
    }
}
