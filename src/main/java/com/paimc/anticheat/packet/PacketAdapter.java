package com.paimc.anticheat.packet;

import java.util.List;

/**
 * Optional packet-level data source. PaiAC ships WITHOUT a packet library, so the only implementation
 * is {@link NoopPacketAdapter}. A future PacketEvents/ProtocolLib bridge would implement this interface.
 */
public interface PacketAdapter {
    boolean available();

    String name();

    /** Detection features that need packets and are therefore reduced or disabled right now. */
    List<String> degradedFeatures();
}
