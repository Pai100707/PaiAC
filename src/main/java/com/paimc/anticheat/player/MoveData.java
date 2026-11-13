package com.paimc.anticheat.player;

import org.bukkit.World;

/** One processed movement event with the environment facts shared by all movement checks. */
public final class MoveData {
    public World world;
    public double fromX, fromY, fromZ, toX, toY, toZ;
    public double dx, dy, dz, dh;
    public float yaw, pitch;
    /** Server ticks since the previous position update (1..20). */
    public int ticks = 1;
    /** Rotation-only event (no position change). */
    public boolean positionless;
    public boolean nearGround;
    public boolean inLiquid;
    public boolean climbing;
    public boolean slowing;
    public boolean bouncy;
    public boolean onIce;
    public boolean onSoul;
    /** Non-null when movement checks must not judge this move; value is the reason. */
    public String exempt;
}
