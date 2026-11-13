package com.paimc.anticheat.checks.anchor;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.player.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

/** Charging an anchor with glowstone implausibly soon after placing it. */
public final class AnchorChargeCheck extends Check {
    public AnchorChargeCheck(PaiAC plugin) {
        super(plugin, "anchor-charge", "anchor", "anchor", "anchor.charge",
                "Glowstone charge right after anchor placement (item swap + click faster than humanly possible)");
    }

    @Override
    public void onInteract(PlayerState s, Player p, PlayerInteractEvent e) {
        if (AnchorData.classify(p, e) != AnchorAction.CHARGE) return;
        AnchorData d = AnchorData.of(s);
        if (d.placeNs == 0) return;
        double ms = (System.nanoTime() - d.placeNs) / 1.0E6;
        double min = settings().dbl("min-place-to-charge-ms", 50);
        if (ms < min) fail(s, ms, min, "place->charge " + String.format("%.0f", ms) + "ms");
        else pass(s);
    }
}
