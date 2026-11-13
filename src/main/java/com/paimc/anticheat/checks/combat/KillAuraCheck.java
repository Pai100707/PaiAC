package com.paimc.anticheat.checks.combat;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.config.CheckSettings;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.Geometry;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Combines several weak signals; a single signal (e.g. a fast rotation) never flags on its own.
 * Signals: look-ray misses the hitbox, impossible target switch, aim-snap onto the hit, attacking in an
 * invalid state, hitting without line of sight.
 */
public final class KillAuraCheck extends Check {
    private static final class Data {
        UUID lastTarget;
        long lastAttackMs;
        Vector lastDir;
    }

    public KillAuraCheck(PaiAC plugin) {
        super(plugin, "kill-aura", "combat", "combat", "combat.kill-aura",
                "Several independent aim/target anomalies on the same hit");
    }

    @Override
    public String limitations() {
        return "Rotation is only sampled from move events (no raw rotation packets); requires multiple signals per hit.";
    }

    @Override
    public void onAttack(PlayerState s, Player p, EntityDamageByEntityEvent e) {
        if (e.getCause() != DamageCause.ENTITY_ATTACK || !(e.getEntity() instanceof LivingEntity target)) return;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            pass(s);
            return;
        }
        CheckSettings cs = settings();
        Data d = s.attachment("kill-aura", Data::new);
        long now = System.currentTimeMillis();
        long nowNs = System.nanoTime();

        Location eyeLoc = p.getEyeLocation();
        Vector eye = eyeLoc.toVector();
        BoundingBox tb = target.getBoundingBox();
        Vector center = new Vector(tb.getCenterX(), tb.getCenterY(), tb.getCenterZ());
        Vector toCenter = center.clone().subtract(eye);

        int score = 0;
        List<String> sig = new ArrayList<>(4);

        // 1) look ray vs hitbox over the most recent rotation samples
        float[] yaws = new float[4];
        float[] pitches = new float[4];
        int n = s.rotationSamples(nowNs, cs.lng("ray-sample-window-ms", 200) * 1_000_000L, yaws, pitches);
        if (n > 0) {
            double reach = cs.dbl("ray-max-distance", 6.0);
            BoundingBox expanded = tb.clone().expand(cs.dbl("ray-tolerance", 0.25));
            boolean anyHit = false;
            for (int i = 0; i < n && !anyHit; i++) {
                if (expanded.rayTrace(eye, Geometry.direction(yaws[i], pitches[i]), reach) != null) anyHit = true;
            }
            if (!anyHit) {
                score++;
                sig.add("ray-miss");
            }
        }

        // 2) switching between widely separated targets faster than a human can aim
        Vector dir = toCenter.clone().normalize();
        UUID tid = target.getUniqueId();
        if (d.lastTarget != null && !d.lastTarget.equals(tid) && d.lastDir != null
                && now - d.lastAttackMs <= cs.lng("switch-window-ms", 120)) {
            double ang = Geometry.angleDeg(d.lastDir, dir);
            if (ang >= cs.dbl("switch-angle-degrees", 70)) {
                score++;
                sig.add("target-switch " + (int) ang + "deg");
            }
        }
        d.lastTarget = tid;
        d.lastAttackMs = now;
        d.lastDir = dir;

        // 3) large rotation snap followed by an on-target hit
        if (n >= 2) {
            double maxDelta = 0;
            for (int i = 0; i + 1 < n; i++) {
                maxDelta = Math.max(maxDelta, Math.abs(Geometry.wrapDegrees(yaws[i] - yaws[i + 1])));
            }
            double aimErr = Geometry.angleDeg(Geometry.direction(yaws[0], pitches[0]), toCenter);
            if (maxDelta >= cs.dbl("snap-degrees", 100) && aimErr <= cs.dbl("snap-hit-angle-degrees", 10)) {
                score++;
                sig.add("snap " + (int) maxDelta + "deg");
            }
        }

        // 4) impossible state (counts double)
        InventoryType open = p.getOpenInventory().getType();
        if (p.isSleeping() || p.isDead() || (open != InventoryType.CRAFTING && open != InventoryType.CREATIVE)) {
            score += 2;
            sig.add("invalid-state " + open);
        }

        // 5) no line of sight (optional, can be noisy near thin geometry, hence just one signal)
        if (cs.bool("check-line-of-sight", true) && !p.hasLineOfSight(target)) {
            score++;
            sig.add("no-line-of-sight");
        }

        int required = Math.max(1, cs.integer("required-signals", 2));
        if (score >= required) fail(s, score, required, String.join(",", sig));
        else pass(s);
    }
}
