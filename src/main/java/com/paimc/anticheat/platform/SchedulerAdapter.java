package com.paimc.anticheat.platform;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Hides the Bukkit vs Folia scheduler difference. */
public interface SchedulerAdapter {
    interface Task {
        void cancel();
    }

    boolean isFolia();

    /** Repeating task on the main/global thread. */
    Task runGlobalRepeating(Runnable r, long delayTicks, long periodTicks);

    /** Repeating task off the server thread. Must not touch Bukkit world/entity state. */
    Task runAsyncRepeating(Runnable r, long delayTicks, long periodTicks);

    void runAsync(Runnable r);

    void runGlobal(Runnable r);

    /** Runs on the thread that owns this player (region thread on Folia). */
    void runForPlayer(Player p, Runnable r);

    void teleport(Player p, Location to);

    void cancelAll();
}
