package com.paimc.anticheat.checks.movement;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.config.CheckSettings;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.Colors;
import com.paimc.anticheat.util.MaterialSets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.vehicle.VehicleMoveEvent;

/** Boats that hover/rise away from any support, or move faster than their surface allows. */
public final class BoatFlyCheck extends Check {
    private static final class Data {
        int airTicks;
        long lastNs;
    }

    public BoatFlyCheck(PaiAC plugin) {
        super(plugin, "boat-fly", "movement", "movement", "movement.boat-fly",
                "Boat staying in the air or exceeding surface speed limits");
    }

    @Override
    public void onVehicleMove(PlayerState s, VehicleMoveEvent e, Player driver) {
        if (!(e.getVehicle() instanceof Boat)) return;
        long now = System.currentTimeMillis();
        Data d = s.attachment("boat-fly", Data::new);
        long nowNs = System.nanoTime();
        int ticks = d.lastNs == 0 ? 1 : (int) Math.max(1, Math.min(10, Math.round((nowNs - d.lastNs) / 50_000_000.0)));
        d.lastNs = nowNs;

        if (now - s.lastTeleportMs < 2000 || now - s.lastVelocityMs < 1500 || now - s.lastExplosionMs < 2000
                || now - s.lastVehicleMs < 500) {
            d.airTicks = 0;
            return;
        }
        Location from = e.getFrom();
        Location to = e.getTo();
        World w = to.getWorld();
        if (w == null || !w.isChunkLoaded(to.getBlockX() >> 4, to.getBlockZ() >> 4)) return;

        CheckSettings cs = settings();
        double dy = to.getY() - from.getY();
        double dh = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());

        int bx = to.getBlockX(), bz = to.getBlockZ();
        Material feet = w.getBlockAt(bx, (int) Math.floor(to.getY()), bz).getType();
        Block belowB = w.getBlockAt(bx, (int) Math.floor(to.getY() - 0.1), bz);
        Material below = belowB.getType();
        boolean supported = MaterialSets.LIQUIDS.contains(feet) || MaterialSets.LIQUIDS.contains(below) || !belowB.isPassable();

        if (!supported) {
            d.airTicks++;
            int minAir = cs.integer("min-air-ticks", 6);
            double hover = cs.dbl("max-hover-dy", -0.02);
            if (d.airTicks >= minAir && dy > hover) {
                fail(s, dy, hover, "airTicks=" + d.airTicks + " dy=" + Colors.fmt(dy));
            } else {
                pass(s);
            }
            return;
        }
        d.airTicks = 0;

        double cap;
        if (below == Material.BLUE_ICE) cap = cs.dbl("blue-ice-cap-per-tick", 4.2);
        else if (MaterialSets.ICE.contains(below)) cap = cs.dbl("ice-cap-per-tick", 2.5);
        else cap = cs.dbl("default-cap-per-tick", 0.7);
        double tol = cs.dbl("tolerance", 1.15);
        double perTick = dh / ticks;
        if (perTick > cap * tol) fail(s, perTick, cap * tol, "surface=" + below + " ticks=" + ticks);
        else pass(s);
    }
}
