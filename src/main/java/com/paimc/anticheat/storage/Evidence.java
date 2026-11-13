package com.paimc.anticheat.storage;

import java.util.UUID;

public record Evidence(long ts, UUID uuid, String name, String checkId, double observed, double expected,
                       int ping, double tps, String platform, String mcVersion,
                       double vlBefore, double vlAfter, String details) {
}
