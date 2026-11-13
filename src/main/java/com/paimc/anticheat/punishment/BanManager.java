package com.paimc.anticheat.punishment;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.storage.BanEntry;
import com.paimc.anticheat.util.Colors;
import com.paimc.anticheat.util.Durations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** PaiAC's own ban list (independent of vanilla/other plugins), cached in memory and persisted by StorageService. */
public final class BanManager {
    private final PaiAC plugin;
    private final Map<UUID, BanEntry> bans = new ConcurrentHashMap<>();

    public BanManager(PaiAC plugin) {
        this.plugin = plugin;
    }

    public void load() {
        bans.clear();
        for (BanEntry b : plugin.storage().loadActiveBansBlocking()) {
            if (!b.isExpired(System.currentTimeMillis())) bans.put(b.uuid(), b);
        }
    }

    /** @return the active ban or null; expired bans are cleaned up lazily. */
    public BanEntry active(UUID id) {
        BanEntry b = bans.get(id);
        if (b == null) return null;
        if (b.isExpired(System.currentTimeMillis())) {
            bans.remove(id);
            plugin.storage().deactivateBan(id);
            return null;
        }
        return b;
    }

    public void ban(BanEntry b) {
        bans.put(b.uuid(), b);
        plugin.storage().upsertBan(b);
    }

    public boolean unban(UUID id) {
        BanEntry removed = bans.remove(id);
        if (removed != null) plugin.storage().deactivateBan(id);
        return removed != null;
    }

    public UUID findByName(String name) {
        for (BanEntry b : bans.values()) if (b.name() != null && b.name().equalsIgnoreCase(name)) return b.uuid();
        return null;
    }

    public Collection<BanEntry> list() {
        List<BanEntry> out = new ArrayList<>();
        for (UUID id : new ArrayList<>(bans.keySet())) {
            BanEntry b = active(id);
            if (b != null) out.add(b);
        }
        return out;
    }

    public String screen(BanEntry b) {
        String expires = b.expires() == -1 ? "never" : Durations.format(Math.max(0, b.expires() - System.currentTimeMillis())) + " left";
        String t = plugin.config().msg("ban-screen", "&cYou are banned by PaiAC.\n&7Reason: &f{reason}\n&7Expires: &f{expires}");
        return Colors.color(t.replace("{reason}", String.valueOf(b.reason())).replace("{expires}", expires));
    }
}
