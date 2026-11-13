package com.paimc.anticheat.player;

import com.paimc.anticheat.util.DoubleRing;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Everything PaiAC remembers about one online player. */
public final class PlayerState {
    public final UUID uuid;
    public volatile String name;
    public final long joinMs = System.currentTimeMillis();

    private volatile Player player;
    private final ConcurrentHashMap<String, CheckData> checkData = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> vl = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cancelUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> attachments = new ConcurrentHashMap<>();
    public final Set<String> firedPunishments = ConcurrentHashMap.newKeySet();
    public final ArrayDeque<String> recentEvidence = new ArrayDeque<>();

    public volatile long exemptUntilMs;
    public volatile boolean alertsEnabled;
    public volatile boolean bypassAll;
    public volatile long permCheckMs;
    public volatile long lastPunishMs;
    public volatile long verboseUntilMs;

    // ---- movement tracking (written by the player's own thread)
    public boolean hasLast;
    public double lastDh, lastDy;
    public long lastMoveNs;
    public volatile Location safeLocation;

    // ---- timestamps (ms) of events that legitimately change movement
    public volatile long lastTeleportMs, lastVelocityMs, lastDamageMs, lastExplosionMs, lastGamemodeMs;
    public volatile long lastVehicleMs, lastGlideMs, lastRiptideMs, lastBounceMs, lastFlightToggleMs;
    public volatile long lastIceMs, lastSoulMs, lastWorldChangeMs;
    public volatile long lastBlockDamageMs, lastAttackMs;
    public volatile boolean digging;

    // ---- diagnostics (/paiac inspect)
    public volatile String lastExempt = "no move seen yet";
    public volatile long lastMoveSeenMs;
    public volatile long moveEvents;

    // ---- rotation history (newest at index rotIdx)
    private final float[] yawHist = new float[4];
    private final float[] pitchHist = new float[4];
    private final long[] rotNs = new long[4];
    private int rotIdx = -1;
    private int rotCount;

    private final DoubleRing pings = new DoubleRing(10);
    private long lastPingSampleMs;

    public PlayerState(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public Player player() { return player; }

    public void setPlayer(Player p) { this.player = p; }

    // ---------------- violations
    public double vl(String checkId) { return vl.getOrDefault(checkId, 0.0); }

    public double addVl(String checkId, double delta, double max) {
        return vl.merge(checkId, Math.min(max, delta), (a, b) -> Math.min(max, a + b));
    }

    public void setVl(String checkId, double value) {
        if (value <= 0) vl.remove(checkId);
        else vl.put(checkId, value);
    }

    public double totalVl() {
        double t = 0;
        for (double v : vl.values()) t += v;
        return t;
    }

    public Map<String, Double> vlSnapshot() { return new HashMap<>(vl); }

    public void clearVl() { vl.clear(); firedPunishments.clear(); }

    public void clearVl(String checkId) {
        vl.remove(checkId);
        firedPunishments.removeIf(k -> k.startsWith(checkId + "#"));
    }

    /** Merge VLs loaded from storage: keep the larger value so live flags are never lost. */
    public void restoreVl(Map<String, Double> loaded) {
        loaded.forEach((k, v) -> vl.merge(k, v, Math::max));
    }

    // ---------------- per-check data
    public CheckData data(String checkId) { return checkData.computeIfAbsent(checkId, k -> new CheckData()); }

    public CheckData peek(String checkId) { return checkData.get(checkId); }

    @SuppressWarnings("unchecked")
    public <T> T attachment(String key, Supplier<T> factory) {
        return (T) attachments.computeIfAbsent(key, k -> factory.get());
    }

    public void pruneIdle(long nowMs, long retentionMs) {
        checkData.entrySet().removeIf(e -> nowMs - e.getValue().lastTouchMs > retentionMs);
    }

    // ---------------- action cancelling
    public void cancelActions(String checkId, long untilMs) { cancelUntil.put(checkId, untilMs); }

    public boolean shouldCancel(String checkId) {
        Long u = cancelUntil.get(checkId);
        if (u == null) return false;
        if (System.currentTimeMillis() > u) {
            cancelUntil.remove(checkId);
            return false;
        }
        return true;
    }

    public boolean isExempt(long nowMs) { return bypassAll || nowMs < exemptUntilMs; }

    // ---------------- rotation history
    public void pushRotation(float yaw, float pitch, long ns) {
        rotIdx = (rotIdx + 1) % yawHist.length;
        yawHist[rotIdx] = yaw;
        pitchHist[rotIdx] = pitch;
        rotNs[rotIdx] = ns;
        if (rotCount < yawHist.length) rotCount++;
    }

    /**
     * Copies recent rotation samples (newest first) not older than windowNs into the output arrays.
     * @return number of samples copied
     */
    public int rotationSamples(long nowNs, long windowNs, float[] yawOut, float[] pitchOut) {
        int n = 0;
        for (int i = 0; i < rotCount && n < yawOut.length; i++) {
            int idx = ((rotIdx - i) % yawHist.length + yawHist.length) % yawHist.length;
            if (nowNs - rotNs[idx] > windowNs) break;
            yawOut[n] = yawHist[idx];
            pitchOut[n] = pitchHist[idx];
            n++;
        }
        return n;
    }

    // ---------------- ping
    public void samplePing(int ping, long nowMs) {
        if (nowMs - lastPingSampleMs < 1000) return;
        lastPingSampleMs = nowMs;
        pings.add(ping);
    }

    /** Max minus min of the recent ping samples (ms). */
    public double pingJitter() {
        return pings.size() < 3 ? 0 : pings.max() - pings.min();
    }
}
