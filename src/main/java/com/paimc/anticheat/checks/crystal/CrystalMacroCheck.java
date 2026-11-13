package com.paimc.anticheat.checks.crystal;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.config.CheckSettings;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.CompatUtil;
import com.paimc.anticheat.util.DoubleRing;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Measures place -> hit cycles of end crystals (obsidian/bedrock base). Signals: mean cycle too short,
 * cycles too regular, bursts of "instant" cycles, sustained rate above the profile limit.
 * Thresholds come from a selectable profile and MUST be calibrated per server (see baseline-collection).
 */
public final class CrystalMacroCheck extends Check {
    private static final int SLOTS = 6;
    private static final int WINDOW = 32;

    private static final class Data {
        final long[] key = new long[SLOTS];
        final long[] placeNs = new long[SLOTS];
        int idx;
        final DoubleRing cycleMs = new DoubleRing(WINDOW);
        final DoubleRing hitMs = new DoubleRing(WINDOW);
        long lastHitMs;
    }

    public CrystalMacroCheck(PaiAC plugin) {
        super(plugin, "crystal-macro", "crystal", "crystal", "crystal.macro",
                "Crystal place->hit cycles faster / more regular than a human can sustain");
    }

    @Override
    public String limitations() {
        return "Server-side event timing only (tick-quantised, no packet order). Default profiles are UNCALIBRATED starting points: "
                + "run in baseline-collection mode on your own players first.";
    }

    private double prof(String key, double def) {
        YamlConfiguration c = plugin.config().checkFile("crystal");
        String name = c.getString("crystal.profile", "conservative");
        return c.getDouble("crystal.profiles." + name + "." + key, def);
    }

    public String profileName() {
        return plugin.config().checkFile("crystal").getString("crystal.profile", "conservative");
    }

    private static long pack(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }

    @Override
    public void onInteract(PlayerState s, Player p, PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        Block b = e.getClickedBlock();
        if (it == null || b == null || it.getType() != Material.END_CRYSTAL) return;
        if (b.getType() != Material.OBSIDIAN && b.getType() != Material.BEDROCK) return;
        Data d = s.attachment("crystal-macro", Data::new);
        d.key[d.idx] = pack(b.getX(), b.getY(), b.getZ());
        d.placeNs[d.idx] = System.nanoTime();
        d.idx = (d.idx + 1) % SLOTS;
    }

    @Override
    public void onAttack(PlayerState s, Player p, EntityDamageByEntityEvent e) {
        Entity ent = e.getEntity();
        if (!CompatUtil.isCrystal(ent)) return;
        if (s.shouldCancel(id())) e.setCancelled(true);

        Data d = s.attachment("crystal-macro", Data::new);
        Location l = ent.getLocation();
        long k = pack(l.getBlockX(), l.getBlockY() - 1, l.getBlockZ());
        int found = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (d.placeNs[i] != 0 && d.key[i] == k) {
                found = i;
                break;
            }
        }
        if (found < 0) return; // not a crystal this player just placed

        long nowNs = System.nanoTime();
        long nowMs = System.currentTimeMillis();
        double cycle = (nowNs - d.placeNs[found]) / 1.0E6;
        d.placeNs[found] = 0;
        d.key[found] = 0;

        CheckSettings cs = settings();
        if (d.lastHitMs != 0 && nowMs - d.lastHitMs > cs.lng("window-reset-ms", 2000)) {
            d.cycleMs.clear();
            d.hitMs.clear();
        }
        d.lastHitMs = nowMs;
        d.cycleMs.add(cycle);
        d.hitMs.add(nowMs);

        int n = Math.min(cs.integer("sample-size", 10), WINDOW);
        if (d.cycleMs.size() < n) return;

        double sum = 0;
        int instant = 0;
        int from = d.cycleMs.size() - n;
        double instantMs = prof("instant-cycle-ms", 10);
        for (int i = from; i < d.cycleMs.size(); i++) {
            double c = d.cycleMs.get(i);
            sum += c;
            if (c <= instantMs) instant++;
        }
        double mean = sum / n;
        double var = 0;
        for (int i = from; i < d.cycleMs.size(); i++) {
            double c = d.cycleMs.get(i) - mean;
            var += c * c;
        }
        double std = Math.sqrt(var / n);
        double windowMs = d.hitMs.get(d.hitMs.size() - 1) - d.hitMs.get(from);
        double cps = windowMs <= 0 ? 999 : (n - 1) / (windowMs / 1000.0);
        int ping = CompatUtil.ping(p);

        if (cs.bool("baseline-collection", false)) {
            plugin.storage().insertBaseline(s.uuid, id(), profileName(), mean, std, cps, ping);
            d.cycleMs.clear();
            d.hitMs.clear();
            return; // calibration mode never flags
        }
        boolean dbg = cs.debug || plugin.debugEnabled();
        if (s.pingJitter() > cs.dbl("max-ping-jitter-ms", 80) || plugin.tps().tps() < 18.0) { // unreliable timing
            if (dbg) plugin.getLogger().info("[debug] crystal-macro " + s.name + " skipped: ping jitter " + (int) s.pingJitter() + "ms / tps " + String.format("%.1f", plugin.tps().tps()));
            return;
        }

        int score = 0;
        StringBuilder why = new StringBuilder();
        if (mean < prof("min-mean-cycle-ms", 60)) { score++; why.append("fast-mean "); }
        if (std < prof("min-stddev-ms", 3) && mean < prof("consistency-mean-limit-ms", 200)) { score++; why.append("too-regular "); }
        if (instant >= prof("max-instant-cycles", 4)) { score++; why.append("instant-burst "); }
        if (cps > prof("max-cps", 14)) { score += 2; why.append("rate "); }

        String details = "profile=" + profileName() + " mean=" + String.format("%.1f", mean) + "ms std=" + String.format("%.1f", std)
                + "ms cps=" + String.format("%.1f", cps) + " instant=" + instant + " [" + why.toString().trim() + "]";
        if (dbg) plugin.getLogger().info("[debug] crystal-macro " + s.name + " score=" + score + "/" + Math.max(1, cs.integer("required-signals", 2)) + " " + details);
        if (score >= Math.max(1, cs.integer("required-signals", 2))) fail(s, cps, prof("max-cps", 14), details);
        else pass(s);
    }
}
