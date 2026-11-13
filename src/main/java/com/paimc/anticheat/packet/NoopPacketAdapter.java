package com.paimc.anticheat.packet;

import java.util.List;

public final class NoopPacketAdapter implements PacketAdapter {
    @Override public boolean available() { return false; }

    @Override public String name() { return "none (Bukkit events only)"; }

    @Override
    public List<String> degradedFeatures() {
        return List.of(
                "Sub-tick click timing (swings are timestamped when the server processes them, so intervals are tick-quantised)",
                "Exact crystal/anchor packet ordering (only server-side event order is visible)",
                "Client-claimed on-ground flag and packet-level movement validation",
                "Rotation packets that never produced a PlayerMoveEvent");
    }
}
