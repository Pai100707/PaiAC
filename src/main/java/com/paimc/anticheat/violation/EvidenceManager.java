package com.paimc.anticheat.violation;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.storage.Evidence;
import com.paimc.anticheat.util.Colors;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class EvidenceManager {
    private static final int KEEP_IN_MEMORY = 15;
    private final PaiAC plugin;

    public EvidenceManager(PaiAC plugin) {
        this.plugin = plugin;
    }

    public void record(PlayerState s, Player p, String checkId, double observed, double expected, int ping, double tps,
                       double vlBefore, double vlAfter, String details) {
        long now = System.currentTimeMillis();
        String full = details;
        if (now < s.verboseUntilMs && p != null) {
            Location l = p.getLocation();
            full += String.format(" | ctx world=%s xyz=%.2f,%.2f,%.2f yawpitch=%.1f,%.1f gm=%s sprint=%s sneak=%s",
                    l.getWorld() == null ? "?" : l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(),
                    p.getGameMode(), p.isSprinting(), p.isSneaking());
        }
        Evidence ev = new Evidence(now, s.uuid, s.name, checkId, observed, expected, ping, tps,
                plugin.platform().softwareLabel(), plugin.platform().minecraftVersion, vlBefore, vlAfter, full);
        plugin.storage().insertEvidence(ev);
        synchronized (s.recentEvidence) {
            s.recentEvidence.addFirst(checkId + " vl=" + Colors.fmt(vlAfter) + " obs=" + Colors.fmt(observed)
                    + " exp=" + Colors.fmt(expected) + " " + details);
            while (s.recentEvidence.size() > KEEP_IN_MEMORY) s.recentEvidence.removeLast();
        }
    }
}
