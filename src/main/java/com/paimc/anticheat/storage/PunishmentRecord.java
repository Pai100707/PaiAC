package com.paimc.anticheat.storage;

import java.util.UUID;

public record PunishmentRecord(long ts, UUID uuid, String name, String checkId, String action, String reason,
                               double vl, long expires, boolean dryRun, String executor) {
}
