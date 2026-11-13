package com.paimc.anticheat.player;

/** Per-player, per-check buffer. Touched only from the player's own thread (plus idle pruning). */
public final class CheckData {
    public double buffer;
    public int evidence;
    public long lastFlagMs;
    public volatile long lastTouchMs = System.currentTimeMillis();
}
