package com.paimc.anticheat.checks.combat;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.config.CheckSettings;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.DoubleRing;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;

/**
 * Swing-rate analysis. Swings are timestamped when the server processes them (tick-quantised), so the hard
 * CPS cap is the strong signal and low-variance cadence only a supporting one. Block mining swings are skipped.
 */
public final class AutoClickerCheck extends Check {
    private static final class Data {
        final ArrayDeque<Long> times = new ArrayDeque<>();
        final DoubleRing intervals = new DoubleRing(64);
        long last;
    }

    public AutoClickerCheck(PaiAC plugin) {
        super(plugin, "auto-clicker", "combat", "combat", "combat.auto-clicker",
                "Click rate above the human limit, or inhumanly regular cadence");
    }

    @Override
    public String limitations() {
        return "Tick-quantised timestamps: cannot see sub-tick jitter; mining detection uses a target-block ray, so swings against a nearby wall while fighting are ignored only while 'digging' is set.";
    }

    @Override
    public void onSwing(PlayerState s, Player p) {
        if (s.digging) {
            Block b = p.getTargetBlockExact(6);
            if (b != null && !b.getType().isAir()) return; // mining: not a combat click
            s.digging = false;
        }
        CheckSettings cs = settings();
        Data d = s.attachment("auto-clicker", Data::new);
        long now = System.currentTimeMillis();

        d.times.addLast(now);
        while (!d.times.isEmpty() && d.times.peekFirst() < now - 1000) d.times.pollFirst();
        if (d.last != 0) {
            long iv = now - d.last;
            if (iv >= 2000) d.intervals.clear();
            else d.intervals.add(iv);
        }
        d.last = now;

        boolean jitter = s.pingJitter() > cs.dbl("max-ping-jitter-ms", 80);
        int cps = d.times.size();
        double maxCps = cs.dbl("max-cps", 22);
        int score = 0;
        StringBuilder why = new StringBuilder();

        if (cps > maxCps && !jitter) {
            score += 2;
            why.append("cps=").append(cps);
        }
        if (!jitter && d.intervals.size() >= cs.integer("min-samples", 30)) {
            double mean = d.intervals.mean();
            double std = d.intervals.stdDev();
            if (mean < cs.dbl("variance-mean-limit-ms", 120) && std < cs.dbl("min-stddev-ms", 14)) {
                score += 1;
                if (why.length() > 0) why.append(' ');
                why.append("stddev=").append(String.format("%.1f", std)).append("ms mean=").append(String.format("%.0f", mean)).append("ms");
            }
        }
        if (score >= 2) fail(s, cps, maxCps, why.toString());
        else if (score == 0) pass(s);
    }
}
