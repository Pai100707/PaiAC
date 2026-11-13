package com.paimc.anticheat.violation;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.config.Settings;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.CompatUtil;
import org.bukkit.entity.Player;

import java.util.Map;

/** Turns a check failure into VL, evidence, an alert and a punishment evaluation. */
public final class ViolationManager {
    private final PaiAC plugin;

    public ViolationManager(PaiAC plugin) {
        this.plugin = plugin;
    }

    public void flag(Check check, PlayerState s, double observed, double expected, String details) {
        Player p = s.player();
        if (p == null) return;
        long now = System.currentTimeMillis();
        if (s.isExempt(now) || p.hasPermission("paiac.bypass." + check.id())) return;

        Settings g = plugin.settings();
        double tps = plugin.tps().tps();
        int ping = CompatUtil.ping(p);
        if (tps < g.lagSkipTps || ping > g.lagSkipPingMs) {
            check.stats().lagSkips.increment();
            return;
        }

        double before = s.vl(check.id());
        double after = s.addVl(check.id(), check.settings().violationPoints, g.maxVl);
        check.stats().flags.increment();

        plugin.evidence().record(s, p, check.id(), observed, expected, ping, tps, before, after, details);
        plugin.alerts().alert(s, check.id(), observed, expected, after, ping, tps, details);
        plugin.punishments().evaluate(s, check.id(), after);
    }

    /** Runs asynchronously once per minute; touches only PlayerState maps (no Bukkit state). */
    public void decayTick() {
        double dec = plugin.settings().decayPerMinute;
        long retention = plugin.settings().stateRetentionSeconds * 1000L;
        long now = System.currentTimeMillis();
        for (PlayerState s : plugin.states().all()) {
            if (dec > 0) {
                for (Map.Entry<String, Double> e : s.vlSnapshot().entrySet()) {
                    double nv = Math.max(0, e.getValue() - dec);
                    s.setVl(e.getKey(), nv);
                    plugin.punishments().onDecay(s, e.getKey(), nv);
                }
            }
            s.pruneIdle(now, retention);
        }
    }
}
