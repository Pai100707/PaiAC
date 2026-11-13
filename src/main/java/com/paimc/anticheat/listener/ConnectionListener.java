package com.paimc.anticheat.listener;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.storage.BanEntry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ConnectionListener implements Listener {
    private final PaiAC plugin;

    public ConnectionListener(PaiAC plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        BanEntry b = plugin.bans().active(e.getUniqueId());
        if (b != null) e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.bans().screen(b));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerState s = plugin.states().getOrCreate(p);
        s.alertsEnabled = plugin.settings().alertsDefaultOn && p.hasPermission("paiac.alerts");
        s.lastTeleportMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        PlayerState s = plugin.states().get(e.getPlayer().getUniqueId());
        if (s != null) plugin.alerts().forget(s);
        plugin.states().remove(e.getPlayer().getUniqueId());
    }
}
