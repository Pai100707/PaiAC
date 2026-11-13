package com.paimc.anticheat.checks.movement;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.config.CheckSettings;
import com.paimc.anticheat.player.MoveData;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.Colors;
import com.paimc.anticheat.util.CompatUtil;
import org.bukkit.entity.Player;

/**
 * Horizontal speed cap derived from the player's movement-speed attribute (which already includes sprint,
 * Speed/Slowness and soul-speed modifiers). 0.1 attribute == 0.2158 blocks/tick walking.
 */
public final class SpeedCheck extends Check {
    private static final double ATTR_TO_BLOCKS_PER_TICK = 2.1585;

    public SpeedCheck(PaiAC plugin) {
        super(plugin, "speed", "movement", "movement", "movement.speed",
                "Horizontal speed above what the movement-speed attribute allows");
    }

    @Override
    public String limitations() {
        return "Cap is attribute-based with generous air/ice multipliers; strafe/air-control exploits that stay under the cap are not detected.";
    }

    @Override
    public void onMove(PlayerState s, MoveData m) {
        if (m.positionless) return;
        CheckSettings cs = settings();
        if (m.exempt != null || m.ticks > cs.integer("max-ticks", 4)) {
            pass(s);
            return;
        }
        Player p = s.player();
        if (p == null) return;
        long now = System.currentTimeMillis();

        double attr = CompatUtil.attributeValue(p, 0.1, "generic.movement_speed", "movement_speed");
        double walkRatio = p.getWalkSpeed() / 0.2;
        attr = Math.max(attr, 0.1 * walkRatio);
        double cap = attr * ATTR_TO_BLOCKS_PER_TICK;

        if (m.inLiquid) {
            cap = Math.max(cap, cs.dbl("liquid-cap-per-tick", 0.45));
        } else {
            if (!m.nearGround) cap *= cs.dbl("air-multiplier", 2.2);
            if (m.onIce || now - s.lastIceMs < 500) cap *= cs.dbl("ice-multiplier", 2.5);
            if (m.onSoul || now - s.lastSoulMs < 500) cap *= cs.dbl("soul-speed-multiplier", 2.0);
        }

        double tps = plugin.tps().tps();
        double tol = cs.dbl("tolerance", 1.10) * (1.0 + Math.max(0, 20.0 - tps) * 0.03);
        double allowed = cap * tol * m.ticks;
        if (m.dh > allowed) {
            fail(s, m.dh / m.ticks, cap * tol,
                    "ticks=" + m.ticks + " air=" + !m.nearGround + " liquid=" + m.inLiquid + " dh=" + Colors.fmt(m.dh));
        } else {
            pass(s);
        }
    }
}
