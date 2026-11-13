package com.paimc.anticheat.platform;

/** Estimates TPS from the spacing of a 20-tick global task; works on Spigot, Paper and Folia alike. */
public final class TpsMonitor {
    private volatile double tps = 20.0;
    private long lastNs;

    public void start(SchedulerAdapter scheduler) {
        scheduler.runGlobalRepeating(this::sample, 20, 20);
    }

    private void sample() {
        long now = System.nanoTime();
        if (lastNs != 0) {
            double ms = (now - lastNs) / 1.0E6;
            double v = Math.min(20.0, 20.0 * 1000.0 / Math.max(ms, 1.0));
            tps = tps * 0.5 + v * 0.5;
        }
        lastNs = now;
    }

    public double tps() {
        return Math.min(20.0, tps);
    }
}
