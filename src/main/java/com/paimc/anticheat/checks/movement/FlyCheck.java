package com.paimc.anticheat.checks.movement;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.config.CheckSettings;
import com.paimc.anticheat.player.MoveData;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.Colors;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Predicts vertical velocity with vanilla gravity ((vy - 0.08) * 0.98) while the player is in the air and flags
 * upward deviation (hover, climbing air, glide-up). One-sided: falling faster than predicted is never flagged.
 */
public final class FlyCheck extends Check {
    private static final class Data {
        int airTicks;
        double prevDy;
    }

    public FlyCheck(PaiAC plugin) {
        super(plugin, "fly", "movement", "movement", "movement.fly",
                "Vertical movement that does not follow gravity (hover / rising without a jump)");
    }

    @Override
    public String limitations() {
        return "Uses server-side ground detection (client on-ground flag is not trusted). Custom gravity attributes are not modelled.";
    }

    @Override
    public void onMove(PlayerState s, MoveData m) {
        if (m.positionless) return;
        Data d = s.attachment("fly", Data::new);
        if (m.exempt != null || m.inLiquid || m.climbing || m.slowing || m.bouncy) {
            d.airTicks = 0;
            d.prevDy = m.dy;
            pass(s);
            return;
        }
        if (m.ticks > 1) { // skipped ticks: the prediction chain is broken
            d.airTicks = 0;
            d.prevDy = m.dy;
            return;
        }
        if (m.nearGround) {
            d.airTicks = 0;
            d.prevDy = m.dy;
            pass(s);
            return;
        }
        Player p = s.player();
        if (p == null) return;
        CheckSettings cs = settings();
        double tol = cs.dbl("tolerance", 0.05);
        d.airTicks++;

        if (d.airTicks == 1) {
            PotionEffect jb = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
            int amp = jb == null ? 0 : jb.getAmplifier() + 1;
            double maxUp = 0.42 + 0.1 * amp + tol;
            d.prevDy = m.dy;
            if (m.dy > maxUp && !entitySupport(p)) fail(s, m.dy, maxUp, "leave-ground dy=" + Colors.fmt(m.dy));
            else pass(s);
            return;
        }

        double expected = (d.prevDy - 0.08) * 0.98;
        double diff = m.dy - expected;
        d.prevDy = m.dy;
        if (diff > tol) {
            if (entitySupport(p)) {
                d.airTicks = 0;
                return;
            }
            fail(s, m.dy, expected, "airTicks=" + d.airTicks + " diff=" + Colors.fmt(diff));
        } else {
            pass(s);
        }
    }

    private static boolean entitySupport(Player p) {
        for (Entity e : p.getNearbyEntities(0.9, 1.8, 0.9)) {
            String n = e.getType().name();
            if (n.contains("BOAT") || n.contains("RAFT") || n.contains("MINECART") || n.contains("SHULKER")) return true;
        }
        return false;
    }
}
