package com.paimc.anticheat.player;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.util.CompatUtil;
import com.paimc.anticheat.util.MaterialSets;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;

/** Builds {@link MoveData} (environment + exemptions) once per move so every movement check shares it. */
public final class MovementTracker {
    private final PaiAC plugin;

    public MovementTracker(PaiAC plugin) {
        this.plugin = plugin;
    }

    public MoveData process(Player p, PlayerState s, PlayerMoveEvent e) {
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to == null || to.getWorld() == null) return null;
        long nowNs = System.nanoTime();
        long nowMs = System.currentTimeMillis();

        MoveData m = new MoveData();
        m.world = to.getWorld();
        m.fromX = from.getX(); m.fromY = from.getY(); m.fromZ = from.getZ();
        m.toX = to.getX(); m.toY = to.getY(); m.toZ = to.getZ();
        m.dx = m.toX - m.fromX; m.dy = m.toY - m.fromY; m.dz = m.toZ - m.fromZ;
        m.dh = Math.hypot(m.dx, m.dz);
        m.yaw = to.getYaw();
        m.pitch = to.getPitch();
        m.positionless = m.dx == 0 && m.dy == 0 && m.dz == 0;

        s.pushRotation(m.yaw, m.pitch, nowNs);
        s.samplePing(CompatUtil.ping(p), nowMs);

        if (m.positionless) return m;

        m.ticks = s.lastMoveNs == 0 ? 1 : (int) Math.max(1, Math.min(20, Math.round((nowNs - s.lastMoveNs) / 50_000_000.0)));
        if (!s.hasLast) m.exempt = "first-move";

        scanEnvironment(m, s, nowMs);
        if (m.exempt == null) m.exempt = exemptionReason(p, s, nowMs);
        return m;
    }

    /** Updates the "previous move" fields. Call after all checks ran. */
    public void finish(PlayerState s, MoveData m) {
        if (m == null || m.positionless) return;
        s.hasLast = true;
        s.lastMoveNs = System.nanoTime();
        s.lastDh = m.dh;
        s.lastDy = m.dy;
        if (m.nearGround && m.exempt == null) {
            s.safeLocation = new Location(m.world, m.toX, m.toY, m.toZ, m.yaw, m.pitch);
        }
    }

    private void scanEnvironment(MoveData m, PlayerState s, long nowMs) {
        World w = m.world;
        int bx = floor(m.toX), by = floor(m.toY), bz = floor(m.toZ);
        double[] cxs = {m.toX - 0.31, m.toX + 0.31};
        double[] czs = {m.toZ - 0.31, m.toZ + 0.31};

        for (double cx : cxs) {
            for (double cz : czs) {
                if (!w.isChunkLoaded(floor(cx) >> 4, floor(cz) >> 4)) {
                    m.exempt = "unloaded-chunk";
                    return;
                }
            }
        }

        Material feet = type(w, bx, by, bz);
        Material mid = type(w, bx, floor(m.toY + 0.9), bz);
        m.inLiquid = MaterialSets.LIQUIDS.contains(feet) || MaterialSets.LIQUIDS.contains(mid) || waterlogged(w, bx, by, bz);
        m.climbing = MaterialSets.CLIMBABLE.contains(feet) || MaterialSets.CLIMBABLE.contains(mid);
        m.slowing = MaterialSets.SLOWING.contains(feet) || MaterialSets.SLOWING.contains(mid);

        double frac = m.toY - Math.floor(m.toY);
        for (double cx : cxs) {
            for (double cz : czs) {
                int gx = floor(cx), gz = floor(cz);
                Block under = w.getBlockAt(gx, floor(m.toY - 0.2), gz);
                Material um = under.getType();
                if (!under.isPassable()) m.nearGround = true;
                if (MaterialSets.BOUNCY.contains(um)) m.bouncy = true;
                if (MaterialSets.ICE.contains(um)) m.onIce = true;
                if (MaterialSets.SOUL.contains(um)) m.onSoul = true;
                if (!m.nearGround) {
                    Material below2 = type(w, gx, floor(m.toY - 1.2), gz);
                    if (MaterialSets.isTallCollision(below2) && frac > 0.3 && frac < 0.7) m.nearGround = true;
                }
            }
        }
        if (!m.nearGround) {
            Block feetBlock = w.getBlockAt(bx, by, bz);
            // e.g. lily pad / carpet / snow layer: collidable block that the feet are inside of
            if (!feetBlock.isPassable() && frac < 0.3) m.nearGround = true;
        }
        if (m.bouncy) s.lastBounceMs = nowMs;
        if (m.onIce) s.lastIceMs = nowMs;
        if (m.onSoul) s.lastSoulMs = nowMs;
    }

    private String exemptionReason(Player p, PlayerState s, long now) {
        var cfg = plugin.config().checkFile("movement");
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return "gamemode";
        if (p.getAllowFlight() || p.isFlying()) return "flight";
        if (p.isGliding()) return "elytra";
        if (p.isRiptiding()) return "riptide";
        if (p.isInsideVehicle()) return "vehicle";
        if (p.isDead()) return "dead";
        if (p.hasPotionEffect(PotionEffectType.LEVITATION)) return "levitation";
        if (p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return "slow-falling";

        if (within(now, s.lastTeleportMs, cfg.getInt("exemptions.after-teleport-ticks", 20))) return "teleport";
        if (within(now, s.lastVelocityMs, cfg.getInt("exemptions.after-velocity-ticks", 30))) return "velocity";
        if (within(now, s.lastExplosionMs, cfg.getInt("exemptions.after-explosion-ticks", 40))) return "explosion";
        if (within(now, s.lastDamageMs, cfg.getInt("exemptions.after-damage-ticks", 15))) return "damage";
        if (within(now, s.lastGamemodeMs, cfg.getInt("exemptions.after-gamemode-change-ticks", 40))) return "gamemode-change";
        if (within(now, s.lastVehicleMs, cfg.getInt("exemptions.after-vehicle-ticks", 20))) return "vehicle-exit";
        if (within(now, s.lastGlideMs, cfg.getInt("exemptions.after-glide-ticks", 40))) return "glide-end";
        if (within(now, s.lastRiptideMs, cfg.getInt("exemptions.after-riptide-ticks", 40))) return "riptide-end";
        if (within(now, s.lastBounceMs, cfg.getInt("exemptions.after-bounce-ticks", 40))) return "bounce";
        if (within(now, s.lastFlightToggleMs, cfg.getInt("exemptions.after-flight-toggle-ticks", 60))) return "flight-toggle";
        if (within(now, s.lastWorldChangeMs, cfg.getInt("exemptions.after-world-change-ticks", 40))) return "world-change";
        if (now - s.joinMs < cfg.getInt("exemptions.after-join-ticks", 60) * 50L) return "join";
        return null;
    }

    private static boolean within(long now, long ts, int ticks) {
        return ts > 0 && now - ts <= ticks * 50L;
    }

    private static int floor(double v) { return (int) Math.floor(v); }

    private static Material type(World w, int x, int y, int z) {
        if (y < w.getMinHeight() || y >= w.getMaxHeight()) return Material.AIR;
        return w.getBlockAt(x, y, z).getType();
    }

    private static boolean waterlogged(World w, int x, int y, int z) {
        if (y < w.getMinHeight() || y >= w.getMaxHeight()) return false;
        return w.getBlockAt(x, y, z).getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged();
    }
}
