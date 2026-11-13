package com.paimc.anticheat.checks.anchor;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.player.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

/** Whole place -> charge -> explode sequence completed faster than a human can. */
public final class AnchorSequenceCheck extends Check {
    public AnchorSequenceCheck(PaiAC plugin) {
        super(plugin, "anchor-sequence", "anchor", "anchor", "anchor.sequence",
                "Anchor charge->explode or place->explode sequence completed implausibly fast");
    }

    @Override
    public void onInteract(PlayerState s, Player p, PlayerInteractEvent e) {
        if (AnchorData.classify(p, e) != AnchorAction.EXPLODE) return;
        AnchorData d = AnchorData.of(s);
        if (d.chargeNs == 0) return;
        long now = System.nanoTime();
        double c2e = (now - d.chargeNs) / 1.0E6;
        double total = d.placeNs == 0 ? Double.MAX_VALUE : (now - d.placeNs) / 1.0E6;
        double minC2E = settings().dbl("min-charge-to-explode-ms", 40);
        double minTotal = settings().dbl("min-total-sequence-ms", 150);
        if (c2e < minC2E || total < minTotal) {
            fail(s, Math.min(c2e, total), c2e < minC2E ? minC2E : minTotal,
                    "charge->explode " + String.format("%.0f", c2e) + "ms total " + (total == Double.MAX_VALUE ? "n/a" : String.format("%.0f", total) + "ms"));
        } else {
            pass(s);
        }
    }
}
