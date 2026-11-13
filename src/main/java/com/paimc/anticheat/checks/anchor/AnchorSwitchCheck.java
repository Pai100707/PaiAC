package com.paimc.anticheat.checks.anchor;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.player.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;

/** Hotbar switching right after charging an anchor: too soon, or in an inhumanly rapid rhythm. */
public final class AnchorSwitchCheck extends Check {
    public AnchorSwitchCheck(PaiAC plugin) {
        super(plugin, "anchor-switch", "anchor", "anchor", "anchor.switch",
                "Hotbar switch speed during an anchor sequence");
    }

    @Override
    public void onHeldChange(PlayerState s, Player p, PlayerItemHeldEvent e) {
        AnchorData d = AnchorData.of(s);
        if (d.chargeNs == 0) return;
        long now = System.nanoTime();
        double sinceCharge = (now - d.chargeNs) / 1.0E6;
        if (sinceCharge > settings().dbl("context-ms", 2000)) return; // not part of a sequence

        double minSwitch = settings().dbl("min-charge-to-switch-ms", 40);
        boolean tooSoon = sinceCharge < minSwitch;
        boolean rapid = false;
        double minAvg = settings().dbl("min-avg-switch-interval-ms", 35);
        if (d.slotIntervalsMs.size() >= settings().integer("min-switch-samples", 4)) {
            rapid = d.slotIntervalsMs.mean() < minAvg;
        }
        if (tooSoon || rapid) {
            fail(s, tooSoon ? sinceCharge : d.slotIntervalsMs.mean(), tooSoon ? minSwitch : minAvg,
                    "charge->switch " + String.format("%.0f", sinceCharge) + "ms avgSwitch " + String.format("%.0f", d.slotIntervalsMs.mean()) + "ms");
        } else {
            pass(s);
        }
    }
}
