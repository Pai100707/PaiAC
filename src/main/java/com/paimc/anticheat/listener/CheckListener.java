package com.paimc.anticheat.listener;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.anchor.AnchorAction;
import com.paimc.anticheat.checks.anchor.AnchorData;
import com.paimc.anticheat.player.MoveData;
import com.paimc.anticheat.player.PlayerState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

/** Feeds Bukkit events to the check pipeline and records the events that legitimately change movement. */
public final class CheckListener implements Listener {
    private final PaiAC plugin;

    public CheckListener(PaiAC plugin) {
        this.plugin = plugin;
    }

    private PlayerState state(Player p) {
        PlayerState s = plugin.states().getOrCreate(p);
        long now = System.currentTimeMillis();
        if (now - s.permCheckMs > 10_000) {
            s.permCheckMs = now;
            s.bypassAll = p.hasPermission("paiac.bypass");
        }
        return s;
    }

    // ------------------------------------------------------------------ detection feeds
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        PlayerState s = state(p);
        long nowMs = System.currentTimeMillis();
        s.lastMoveSeenMs = nowMs;
        s.moveEvents++;
        if (s.isExempt(nowMs)) {
            s.lastExempt = s.bypassAll ? "BYPASS permission (paiac.bypass)" : "temporary /paiac exempt";
            return;
        }
        MoveData m = plugin.movement().process(p, s, e);
        if (m == null) return;
        s.lastExempt = m.positionless ? "rotation-only event" : (m.exempt == null ? "none (move is judged)" : m.exempt);
        plugin.checks().dispatch(s, c -> c.onMove(s, m));
        plugin.movement().finish(s, m);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent e) {
        var passengers = e.getVehicle().getPassengers();
        if (passengers.isEmpty() || !(passengers.get(0) instanceof Player driver)) return;
        PlayerState s = state(driver);
        plugin.checks().dispatch(s, c -> c.onVehicleMove(s, e, driver));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        PlayerState s = state(p);
        s.lastAttackMs = System.currentTimeMillis();
        s.digging = false;
        plugin.checks().dispatch(s, c -> c.onAttack(s, p, e));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        PlayerState s = state(p);
        plugin.checks().dispatch(s, c -> c.onSwing(s, p));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        PlayerState s = state(p);
        plugin.checks().dispatch(s, c -> c.onInteract(s, p, e));
        AnchorAction a = AnchorData.classify(p, e);
        if (a != AnchorAction.NONE) AnchorData.of(s).afterInteract(a);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent e) {
        PlayerState s = state(e.getPlayer());
        s.digging = true;
        s.lastBlockDamageMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        state(e.getPlayer()).digging = false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        PlayerState s = state(p);
        plugin.checks().dispatch(s, c -> c.onBlockPlace(s, p, e));
        if (e.getBlockPlaced().getType() == Material.RESPAWN_ANCHOR) AnchorData.of(s).afterPlace();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        PlayerState s = state(p);
        plugin.checks().dispatch(s, c -> c.onHeldChange(s, p, e));
        AnchorData.of(s).afterHeld();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        PlayerState s = state(p);
        plugin.checks().dispatch(s, c -> c.onSwapHands(s, p, e));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        PlayerState s = state(p);
        plugin.checks().dispatch(s, c -> c.onInventoryClick(s, p, e));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PlayerState s = state(p);
        plugin.checks().dispatch(s, c -> c.onTotemPop(s, p, e));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        PlayerState s = state(p);
        plugin.checks().dispatch(s, c -> c.onProjectileLaunch(s, p, e));
    }

    // ------------------------------------------------------------------ legitimate movement sources
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        state(e.getPlayer()).lastTeleportMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        state(e.getPlayer()).lastTeleportMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        PlayerState s = state(e.getPlayer());
        long now = System.currentTimeMillis();
        s.lastWorldChangeMs = now;
        s.lastTeleportMs = now;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        state(e.getPlayer()).lastVelocityMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PlayerState s = state(p);
        long now = System.currentTimeMillis();
        s.lastDamageMs = now;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || c == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            s.lastExplosionMs = now;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        markNearby(e.getLocation(), 12);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        markNearby(e.getBlock().getLocation(), 12);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity().getType().name().contains("WIND_CHARGE")) markNearby(e.getEntity().getLocation(), 8);
    }

    private void markNearby(Location loc, double radius) {
        if (loc.getWorld() == null) return;
        long now = System.currentTimeMillis();
        for (Entity en : loc.getWorld().getNearbyEntities(loc, radius, radius, radius, x -> x instanceof Player)) {
            PlayerState s = plugin.states().get(en.getUniqueId());
            if (s != null) s.lastExplosionMs = now;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p) state(p).lastGlideMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent e) {
        state(e.getPlayer()).lastRiptideMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlightToggle(PlayerToggleFlightEvent e) {
        state(e.getPlayer()).lastFlightToggleMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        state(e.getPlayer()).lastGamemodeMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player p) state(p).lastVehicleMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent e) {
        if (e.getExited() instanceof Player p) state(p).lastVehicleMs = System.currentTimeMillis();
    }
}
