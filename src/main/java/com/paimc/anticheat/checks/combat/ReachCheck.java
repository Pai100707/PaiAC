package com.paimc.anticheat.checks.combat;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.config.CheckSettings;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.CompatUtil;
import com.paimc.anticheat.util.Geometry;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;

/** Eye-to-hitbox distance of melee hits, with ping and target-speed compensation. */
public final class ReachCheck extends Check {
    public ReachCheck(PaiAC plugin) {
        super(plugin, "reach", "combat", "combat", "combat.reach",
                "Melee hit from farther than the allowed distance to the target hitbox");
    }

    @Override
    public String limitations() {
        return "Uses the server-side position of both players (no position history): compensation is estimated from ping and target speed.";
    }

    @Override
    public void onAttack(PlayerState s, Player p, EntityDamageByEntityEvent e) {
        if (s.shouldCancel(id())) e.setCancelled(true);
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            pass(s);
            return;
        }
        Entity t = e.getEntity();
        if (!t.getWorld().equals(p.getWorld())) return;

        CheckSettings cs = settings();
        double base = cs.dbl("max-reach", 3.0);
        double attr = CompatUtil.attributeValue(p, 0, "player.entity_interaction_range", "entity_interaction_range");
        if (attr > base) base = attr;

        BoundingBox box = t.getBoundingBox();
        Location eye = p.getEyeLocation();
        double dist = Geometry.distanceToBox(eye.getX(), eye.getY(), eye.getZ(), box);

        int ping = CompatUtil.ping(p);
        double speed = targetSpeed(t);
        double comp = Math.min(ping, cs.dbl("ping-cap-ms", 300)) / 50.0 * speed * cs.dbl("ping-compensation-factor", 1.0);
        comp = Math.min(comp, cs.dbl("max-compensation", 1.0));
        double allowed = base + cs.dbl("base-tolerance", 0.4) + comp;

        double hard = cs.dbl("hard-cancel-distance", 0.0);
        if (hard > 0 && dist > hard) e.setCancelled(true);

        if (dist > allowed) {
            fail(s, dist, allowed, "target=" + t.getType() + " ping=" + ping + " comp=" + String.format("%.2f", comp));
        } else {
            pass(s);
        }
    }

    private double targetSpeed(Entity t) {
        if (t instanceof Player tp) {
            PlayerState ts = plugin.states().get(tp.getUniqueId());
            return ts == null ? 0 : ts.lastDh;
        }
        return t.getVelocity().length();
    }
}
