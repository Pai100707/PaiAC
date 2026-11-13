package com.paimc.anticheat.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/** Only ever loaded on Folia (never referenced from code paths that run on Spigot/Paper). */
public final class FoliaSchedulerAdapter implements SchedulerAdapter {
    private final Plugin plugin;

    public FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override public boolean isFolia() { return true; }

    @Override
    public Task runGlobalRepeating(Runnable r, long delay, long period) {
        ScheduledTask t = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, st -> r.run(), Math.max(1, delay), Math.max(1, period));
        return t::cancel;
    }

    @Override
    public Task runAsyncRepeating(Runnable r, long delay, long period) {
        ScheduledTask t = Bukkit.getAsyncScheduler()
                .runAtFixedRate(plugin, st -> r.run(), Math.max(1, delay) * 50L, Math.max(1, period) * 50L, TimeUnit.MILLISECONDS);
        return t::cancel;
    }

    @Override
    public void runAsync(Runnable r) {
        Bukkit.getAsyncScheduler().runNow(plugin, st -> r.run());
    }

    @Override
    public void runGlobal(Runnable r) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, r);
    }

    @Override
    public void runForPlayer(Player p, Runnable r) {
        p.getScheduler().run(plugin, st -> r.run(), null);
    }

    @Override
    public void teleport(Player p, Location to) {
        p.teleportAsync(to);
    }

    @Override
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }
}
