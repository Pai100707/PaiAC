package com.paimc.anticheat.checks;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.config.CheckSettings;
import com.paimc.anticheat.player.CheckData;
import com.paimc.anticheat.player.MoveData;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.Colors;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

/**
 * Base class of every detection. A check only receives events through the hooks below (dispatched by
 * {@link CheckManager} with timing + exception isolation) and reports through {@link #fail}/{@link #pass}.
 * Buffer, evidence counting, cooldown and VL are handled here so individual checks stay small.
 */
public abstract class Check {
    protected final PaiAC plugin;
    private final String id;
    private final String category;
    private final String file;
    private final String path;
    private final String description;
    private volatile CheckSettings settings = CheckSettings.disabled();
    private volatile boolean faulted;
    private final CheckStats stats = new CheckStats();

    protected Check(PaiAC plugin, String id, String category, String file, String path, String description) {
        this.plugin = plugin;
        this.id = id;
        this.category = category;
        this.file = file;
        this.path = path;
        this.description = description;
    }

    public final String id() { return id; }
    public final String category() { return category; }
    public final String file() { return file; }
    public final String path() { return path; }
    public final String description() { return description; }
    public final CheckSettings settings() { return settings; }
    public final CheckStats stats() { return stats; }
    public final boolean faulted() { return faulted; }
    public final void fault() { faulted = true; }

    public final void applySettings(CheckSettings s) {
        this.settings = s;
        this.faulted = false;
        onReload();
    }

    public final boolean enabled() {
        return settings.enabled && !faulted;
    }

    protected void onReload() {}

    /** Honest statement of what this check cannot see without packets. Shown in /paiac checks. */
    public String limitations() { return ""; }

    // ------------------------------------------------------------ event hooks (all optional)
    public void onMove(PlayerState s, MoveData m) {}
    public void onVehicleMove(PlayerState s, VehicleMoveEvent e, Player driver) {}
    public void onAttack(PlayerState s, Player p, EntityDamageByEntityEvent e) {}
    public void onSwing(PlayerState s, Player p) {}
    public void onInteract(PlayerState s, Player p, PlayerInteractEvent e) {}
    public void onBlockPlace(PlayerState s, Player p, BlockPlaceEvent e) {}
    public void onHeldChange(PlayerState s, Player p, PlayerItemHeldEvent e) {}
    public void onSwapHands(PlayerState s, Player p, PlayerSwapHandItemsEvent e) {}
    public void onInventoryClick(PlayerState s, Player p, InventoryClickEvent e) {}
    public void onTotemPop(PlayerState s, Player p, EntityResurrectEvent e) {}
    public void onProjectileLaunch(PlayerState s, Player p, ProjectileLaunchEvent e) {}

    // ------------------------------------------------------------ flag helpers
    protected final void fail(PlayerState s, double observed, double expected, String details) {
        failWeighted(s, 1.0, observed, expected, details);
    }

    protected final void failWeighted(PlayerState s, double weight, double observed, double expected, String details) {
        CheckSettings cs = settings;
        if (!cs.enabled) return;
        CheckData d = s.data(id);
        long now = System.currentTimeMillis();
        d.lastTouchMs = now;
        d.buffer = Math.min(cs.bufferLimit, d.buffer + weight);
        d.evidence++;
        if (cs.debug || plugin.debugEnabled()) {
            plugin.getLogger().info("[debug] " + id + " " + s.name + " buffer=" + Colors.fmt(d.buffer)
                    + " obs=" + Colors.fmt(observed) + " exp=" + Colors.fmt(expected) + " " + details);
        }
        if (d.buffer >= cs.threshold && d.evidence >= cs.minEvidence && now - d.lastFlagMs >= cs.cooldownMs) {
            d.lastFlagMs = now;
            plugin.violations().flag(this, s, observed, expected, details);
        }
    }

    protected final void pass(PlayerState s) {
        CheckData d = s.peek(id);
        if (d == null || d.buffer <= 0) return;
        d.buffer = Math.max(0, d.buffer - settings.bufferDecay);
        if (d.buffer <= 0) {
            d.buffer = 0;
            if (settings.resetOnClean) d.evidence = 0;
        }
    }

    protected final void resetBuffer(PlayerState s) {
        CheckData d = s.peek(id);
        if (d != null) {
            d.buffer = 0;
            d.evidence = 0;
        }
    }
}
