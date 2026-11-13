package com.paimc.anticheat.storage;

import java.util.UUID;

public record BanEntry(UUID uuid, String name, String reason, long created, long expires, String by) {
    public boolean isExpired(long now) {
        return expires != -1 && now >= expires;
    }
}
