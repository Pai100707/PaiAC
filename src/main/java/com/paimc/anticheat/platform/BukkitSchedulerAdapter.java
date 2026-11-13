package com.paimc.anticheat.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class BukkitSchedulerAdapter implements SchedulerAdapter {
    private final Plugin plugin;

    public BukkitSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override public boolean isFolia() { return false; }

    @Override
    public Task runGlobalRepeating(Runnable r, long delay, long period) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, r, Math.max(1, delay), Math.max(1, period));
        return t::cancel;
    }

    @Override
    public Task runAsyncRepeating(Runnable r, long delay, long period) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, r, Math.max(1, delay), Math.max(1, period));
        return t::cancel;
    }

    @Override
    public void runAsync(Runnable r) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    @Override
    public void runGlobal(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    @Override
    public void runForPlayer(Player p, Runnable r) {
        runGlobal(r);
    }

    @Override
    public void teleport(Player p, Location to) {
        runForPlayer(p, () -> p.teleport(to));
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
