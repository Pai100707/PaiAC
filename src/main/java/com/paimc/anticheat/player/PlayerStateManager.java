package com.paimc.anticheat.player;

import com.paimc.anticheat.PaiAC;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStateManager {
    private final PaiAC plugin;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    public PlayerStateManager(PaiAC plugin) {
        this.plugin = plugin;
    }

    public PlayerState get(UUID id) { return states.get(id); }

    public PlayerState getOrCreate(Player p) {
        PlayerState s = states.get(p.getUniqueId());
        if (s != null) {
            s.setPlayer(p);
            return s;
        }
        return states.computeIfAbsent(p.getUniqueId(), id -> {
            PlayerState ns = new PlayerState(id, p.getName());
            ns.setPlayer(p);
            ns.alertsEnabled = plugin.settings().alertsDefaultOn;
            plugin.storage().loadViolations(id).thenAccept(map -> {
                ns.restoreVl(map);
                plugin.punishments().seed(ns);
            });
            return ns;
        });
    }

    public void remove(UUID id) {
        PlayerState s = states.remove(id);
        if (s != null) {
            plugin.storage().saveViolations(id, s.vlSnapshot());
            s.setPlayer(null);
        }
    }

    public Collection<PlayerState> all() { return states.values(); }

    public void saveAll() {
        for (PlayerState s : states.values()) plugin.storage().saveViolations(s.uuid, s.vlSnapshot());
    }

    public void clear() { states.clear(); }
}
